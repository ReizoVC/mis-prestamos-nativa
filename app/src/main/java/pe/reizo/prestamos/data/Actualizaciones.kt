package pe.reizo.prestamos.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pe.reizo.prestamos.BuildConfig
import pe.reizo.prestamos.core.esVersionMasNueva
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class Novedad(val version: String, val url: String)

/**
 * Actualizaciones fuera de Play Store: la app mira la última versión publicada en GitHub Releases,
 * descarga el APK y se lo entrega al instalador de Android. Android solo acepta instalarlo encima
 * si está firmado con la misma llave, así que un APK ajeno no puede reemplazar la app.
 */
object Actualizaciones {
    val activas = BuildConfig.ACTUALIZACIONES_URL.isNotEmpty()

    private fun abrir(direccion: String): HttpURLConnection =
        (URL(direccion).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "app-android")
            setRequestProperty("Accept", "application/vnd.github+json")
        }

    /** Devuelve la versión publicada si es más nueva que la instalada. Sin internet o sin versiones: null. */
    suspend fun buscar(): Novedad? = withContext(Dispatchers.IO) {
        if (!activas) return@withContext null
        try {
            val c = abrir(BuildConfig.ACTUALIZACIONES_URL)
            try {
                if (c.responseCode != 200) return@withContext null
                val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val version = o.getString("tag_name").trimStart('v', 'V')
                if (!esVersionMasNueva(version, BuildConfig.VERSION_NAME)) return@withContext null
                val archivos = o.getJSONArray("assets")
                for (i in 0 until archivos.length()) {
                    val a = archivos.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) return@withContext Novedad(version, a.getString("browser_download_url"))
                }
                null
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Descarga el APK a la carpeta temporal de la app. `avance` va de 0 a 1. */
    suspend fun descargar(ctx: Context, novedad: Novedad, avance: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val carpeta = File(ctx.cacheDir, "actualizacion").apply { mkdirs() }
        val destino = File(carpeta, "mis-prestamos.apk")
        val c = abrir(novedad.url)
        try {
            if (c.responseCode != 200) throw IOException("Descarga: ${c.responseCode}")
            val total = c.contentLengthLong
            var leido = 0L
            c.inputStream.use { entrada ->
                destino.outputStream().use { salida ->
                    val bloque = ByteArray(64 * 1024)
                    while (true) {
                        val n = entrada.read(bloque)
                        if (n < 0) break
                        salida.write(bloque, 0, n)
                        leido += n
                        if (total > 0) avance(leido.toFloat() / total)
                    }
                }
            }
            destino
        } finally {
            c.disconnect()
        }
    }

    /** Abre el instalador de Android con el APK descargado. Android pide confirmar. */
    fun instalar(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.archivos", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
