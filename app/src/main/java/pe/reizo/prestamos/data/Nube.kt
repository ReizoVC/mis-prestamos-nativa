package pe.reizo.prestamos.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pe.reizo.prestamos.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class EstadoNube { SOLO_LOCAL, OK, SIN_INTERNET, ERROR }

private class SesionVencida : Exception()
private class Respuesta(val codigo: Int, val cuerpo: String)

/**
 * Copia en la nube (Supabase). La app siempre lee y escribe en la base del celular;
 * esto sube los cambios pendientes y baja los de la nube en segundo plano.
 * Usa las mismas tablas que la versión web (supabase/schema.sql).
 */
class Nube(ctx: Context, private val db: BaseDatos, private val ambito: CoroutineScope) {
    private val url = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val llave = BuildConfig.SUPABASE_ANON_KEY.trim()
    val configurada = !BuildConfig.MODO_DESARROLLO && url.isNotEmpty() && llave.isNotEmpty()

    private val prefs = ctx.getSharedPreferences("nube", Context.MODE_PRIVATE)
    private val t = db.tablas()
    private val candado = Mutex()
    private var espera: Job? = null
    lateinit var repo: Repo

    val conSesion = MutableStateFlow(prefs.getString("refresh", null) != null)
    val estado = MutableStateFlow(if (configurada) EstadoNube.OK else EstadoNube.SOLO_LOCAL)
    val ultima = MutableStateFlow(prefs.getLong("ultima", 0L))

    init {
        if (configurada) {
            // Cuando vuelve el internet, se sube lo pendiente
            ctx.getSystemService(ConnectivityManager::class.java)
                ?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = pedir()
                })
        }
    }

    /** Pide una sincronización. Espera un momento para juntar varios cambios seguidos. */
    fun pedir(demora: Long = 2000) {
        if (!configurada || !conSesion.value) return
        espera?.cancel()
        espera = ambito.launch { delay(demora); sincronizar() }
    }

    /** Devuelve null si entró, o el mensaje de error. */
    suspend fun iniciarSesion(correo: String, clave: String): String? = withContext(Dispatchers.IO) {
        try {
            val cuerpo = JSONObject().put("email", correo.trim()).put("password", clave).toString()
            val r = http("POST", "/auth/v1/token?grant_type=password", cuerpo, null)
            if (r.codigo == 200) {
                guardarSesion(JSONObject(r.cuerpo))
                conSesion.value = true
                pedir(0)
                null
            } else "Correo o contraseña incorrectos."
        } catch (e: IOException) {
            "Necesita internet para iniciar sesión."
        }
    }

    /** Cierra la sesión en este celular. Los datos locales no se tocan. */
    fun cerrarSesion() {
        prefs.edit().clear().apply()
        ultima.value = 0
        conSesion.value = false
    }

    suspend fun sincronizar() {
        if (!configurada || !conSesion.value) return
        candado.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val token = tokenVigente()
                    subir(token)
                    bajar(token)
                    val ahora = System.currentTimeMillis()
                    prefs.edit().putLong("ultima", ahora).apply()
                    ultima.value = ahora
                    estado.value = EstadoNube.OK
                } catch (e: SesionVencida) {
                    // Hay que iniciar sesión otra vez; los datos locales siguen ahí
                    prefs.edit().remove("refresh").remove("access").apply()
                    conSesion.value = false
                } catch (e: IOException) {
                    estado.value = EstadoNube.SIN_INTERNET
                } catch (e: Exception) {
                    estado.value = EstadoNube.ERROR
                }
            }
        }
    }

    // ---- sesión

    private fun guardarSesion(o: JSONObject) {
        prefs.edit()
            .putString("access", o.getString("access_token"))
            .putString("refresh", o.getString("refresh_token"))
            .putLong("expira", System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000)
            .apply()
    }

    private fun tokenVigente(): String {
        val access = prefs.getString("access", null)
        if (access != null && System.currentTimeMillis() < prefs.getLong("expira", 0) - 60_000) return access
        val refresh = prefs.getString("refresh", null) ?: throw SesionVencida()
        val r = http("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh).toString(), null)
        return when (r.codigo) {
            200 -> JSONObject(r.cuerpo).also { guardarSesion(it) }.getString("access_token")
            400, 401, 403 -> throw SesionVencida()
            else -> throw IOException("Sesión: ${r.codigo}")
        }
    }

    // ---- subir: clientes, préstamos y pagos, en ese orden por las referencias

    private suspend fun subir(token: String) {
        t.clientesPendientes().chunked(200).forEach { lote ->
            enviar("clientes", lote.map { it.aJson(true) }, token)
            lote.forEach { t.clienteSubido(it.id, it.actualizadoEn) }
        }
        t.prestamosPendientes().chunked(200).forEach { lote ->
            enviar("prestamos", lote.map { it.aJson(true) }, token)
            lote.forEach { t.prestamoSubido(it.id, it.actualizadoEn) }
        }
        t.pagosPendientes().chunked(200).forEach { lote ->
            enviar("pagos", lote.map { it.aJson(true) }, token)
            lote.forEach { t.pagoSubido(it.id, it.actualizadoEn) }
        }
    }

    private fun enviar(tabla: String, filas: List<JSONObject>, token: String) {
        val cuerpo = JSONArray().apply { filas.forEach { put(it) } }.toString()
        val r = http("POST", "/rest/v1/$tabla?on_conflict=id", cuerpo, token, "resolution=merge-duplicates,return=minimal")
        comprobar(r)
    }

    // ---- bajar: lo que cambió en el servidor desde la última vez (un cursor por tabla)

    private suspend fun bajar(token: String) {
        bajarTabla("clientes", token) { repo.mezclar(clienteDe(it), deNube = true) }
        bajarTabla("prestamos", token) { repo.mezclar(prestamoDe(it), deNube = true) }
        bajarTabla("pagos", token) { repo.mezclar(pagoDe(it), deNube = true) }
    }

    private suspend fun bajarTabla(tabla: String, token: String, guardar: suspend (JSONObject) -> Unit) {
        val cursor = prefs.getString("cursor_$tabla", null)
        val desde = cursor?.let { "&servidor_actualizado_en=gt." + URLEncoder.encode(it, "UTF-8") } ?: ""
        var salto = 0
        var mayor = cursor
        while (true) {
            val r = http(
                "GET",
                "/rest/v1/$tabla?select=*&order=servidor_actualizado_en.asc,id.asc&limit=1000&offset=$salto$desde",
                null, token,
            )
            comprobar(r)
            val filas = JSONArray(r.cuerpo)
            for (i in 0 until filas.length()) {
                val fila = filas.getJSONObject(i)
                guardar(fila)
                mayor = fila.getString("servidor_actualizado_en")
            }
            if (filas.length() < 1000) break
            salto += 1000
        }
        if (mayor != cursor) prefs.edit().putString("cursor_$tabla", mayor).apply()
    }

    // ---- red

    private fun comprobar(r: Respuesta) {
        if (r.codigo == 401) throw SesionVencida()
        if (r.codigo !in 200..299) throw IllegalStateException("Nube ${r.codigo}: ${r.cuerpo}")
    }

    private fun http(metodo: String, ruta: String, cuerpo: String?, token: String?, prefer: String? = null): Respuesta {
        val c = URL(url + ruta).openConnection() as HttpURLConnection
        try {
            c.requestMethod = metodo
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("apikey", llave)
            c.setRequestProperty("Authorization", "Bearer ${token ?: llave}")
            if (prefer != null) c.setRequestProperty("Prefer", prefer)
            if (cuerpo != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(cuerpo.toByteArray()) }
            }
            val codigo = c.responseCode
            val flujo = if (codigo < 400) c.inputStream else c.errorStream
            return Respuesta(codigo, flujo?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            c.disconnect()
        }
    }
}
