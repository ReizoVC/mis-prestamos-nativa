package pe.reizo.prestamos.ui

import android.app.Application
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.reizo.prestamos.BuildConfig
import pe.reizo.prestamos.core.Cliente
import pe.reizo.prestamos.core.DIAS_SIN_PAGAR
import pe.reizo.prestamos.core.Filtro
import pe.reizo.prestamos.core.Libro
import pe.reizo.prestamos.core.Periodo
import pe.reizo.prestamos.core.dia
import pe.reizo.prestamos.core.rangoDe
import pe.reizo.prestamos.data.Actualizaciones
import pe.reizo.prestamos.data.BaseDatos
import pe.reizo.prestamos.data.Novedad
import pe.reizo.prestamos.data.Nube
import pe.reizo.prestamos.data.Repo
import java.time.LocalDate

/** Pantallas que se abren encima de las tres pestañas. */
sealed interface Pantalla {
    data class Ficha(val clienteId: String) : Pantalla
    data class Nuevo(val clienteId: String?) : Pantalla
    data class Desglose(val tipo: TipoDesglose) : Pantalla
    data class Cobranza(val prestamoId: String) : Pantalla
    data object Ajustes : Pantalla
    data object Papelera : Pantalla
}

/** Aviso corto abajo. Puede traer un botón (por ejemplo "Deshacer"). */
class Aviso(val texto: String, val accion: String?, val alAccion: (suspend () -> Unit)?)

enum class Pestana { CLIENTES, ANTIGUOS, RESUMEN }

enum class TemaApp(val etiqueta: String) { CLARO("Claro"), OSCURO("Oscuro"), SISTEMA("Como el celular") }

/** Tamaño de la letra dentro de la app, encima del que ya tenga el celular. */
enum class Letra(val etiqueta: String, val escala: Float) { NORMAL("Normal", 1f), GRANDE("Grande", 1.15f), MUY_GRANDE("Muy grande", 1.3f) }

class AppVM(app: Application) : AndroidViewModel(app) {
    private val db = BaseDatos.abrir(app, enMemoria = BuildConfig.MODO_DESARROLLO)
    val nube = Nube(app, db, viewModelScope)
    val repo = Repo(db) { nube.pedir() }.also { nube.repo = it }
    private val prefs = app.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    // ---- preferencias de Ajustes. La app arranca en modo claro.
    var tema by mutableStateOf(enumDe(prefs.getString("tema", null), TemaApp.CLARO))
        private set
    var letra by mutableStateOf(enumDe(prefs.getString("letra", null), Letra.NORMAL))
        private set
    private val diasSinPagar = MutableStateFlow(prefs.getInt("dias_sin_pagar", DIAS_SIN_PAGAR))
    val diasSinPagarElegidos: StateFlow<Int> get() = diasSinPagar

    fun cambiarTema(t: TemaApp) { prefs.edit().putString("tema", t.name).apply(); tema = t }
    fun cambiarLetra(l: Letra) { prefs.edit().putString("letra", l.name).apply(); letra = l }
    fun cambiarDiasSinPagar(d: Int) { prefs.edit().putInt("dias_sin_pagar", d).apply(); diasSinPagar.value = d }

    val libro: StateFlow<Libro?> = db.tablas().let { t ->
        combine(t.verClientes(), t.verPrestamos(), t.verPagos(), diasSinPagar) { c, p, g, d -> Libro(c, p, g, d) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val pendientes: StateFlow<Int> = db.tablas().verPendientes().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // ---- navegación
    val pila = mutableStateListOf<Pantalla>()
    var avanzando by mutableStateOf(true)
        private set
    var pestana by mutableStateOf(Pestana.CLIENTES)

    fun abrir(p: Pantalla) { avanzando = true; pila.add(p) }
    fun volver() { avanzando = false; pila.removeLastOrNull() }
    fun irAFicha(clienteId: String) { avanzando = true; pila.clear(); pila.add(Pantalla.Ficha(clienteId)) }

    // ---- estado de las pestañas
    var busqueda by mutableStateOf("")
    var filtro by mutableStateOf(Filtro.TODOS)
    var busquedaAntiguos by mutableStateOf("")
    var periodo by mutableStateOf(Periodo.MES)
    var desde: LocalDate by mutableStateOf(LocalDate.now().withDayOfMonth(1))
    var hasta: LocalDate by mutableStateOf(LocalDate.now())
    var ahora by mutableLongStateOf(System.currentTimeMillis())

    val rango get() = rangoDe(periodo, ahora, desde, hasta)

    // ---- avisos (uno a la vez, abajo)
    val avisos = MutableSharedFlow<Aviso>(extraBufferCapacity = 4)
    fun avisar(texto: String, accion: String? = null, alAccion: (suspend () -> Unit)? = null) {
        avisos.tryEmit(Aviso(texto, accion, alAccion))
    }

    // Estas acciones siguen aunque la pantalla que las pidió ya se haya cerrado
    fun eliminarCliente(c: Cliente) {
        volver()
        viewModelScope.launch {
            repo.eliminarCliente(c.id)
            avisar("Se eliminó a ${c.nombre}", "Deshacer") { repo.restaurarCliente(c.id) }
        }
    }

    fun borrarPago(pagoId: String) {
        viewModelScope.launch {
            repo.borrarPago(pagoId)
            avisar("Pago borrado", "Deshacer") { repo.restaurarPago(pagoId) }
        }
    }

    fun recuperarCliente(c: Cliente) {
        viewModelScope.launch { repo.restaurarCliente(c.id); avisar("Se recuperó a ${c.nombre}") }
    }

    fun reiniciarEjemplo() {
        viewModelScope.launch { repo.cargarEjemplo(); avisar("Volvieron los datos de ejemplo") }
    }

    // ---- actualizaciones
    var novedad by mutableStateOf<Novedad?>(null)
        private set
    /** Avance de la descarga de 0 a 1, o null si no se está descargando. */
    var descarga by mutableStateOf<Float?>(null)
        private set
    private var revisadoEn = 0L

    /** Mira si hay versión nueva. Sola, como mucho cada 30 minutos; `aMano` es el botón de Ajustes. */
    fun buscarActualizacion(aMano: Boolean = false) {
        val ahoraMs = System.currentTimeMillis()
        if (!Actualizaciones.activas || (!aMano && ahoraMs - revisadoEn < 30 * 60_000)) return
        revisadoEn = ahoraMs
        viewModelScope.launch {
            novedad = Actualizaciones.buscar()
            if (aMano && novedad == null) avisar("Ya tiene la última versión")
        }
    }

    fun actualizar() {
        val n = novedad ?: return
        if (descarga != null) return
        descarga = 0f
        viewModelScope.launch {
            try {
                val apk = Actualizaciones.descargar(getApplication(), n) { descarga = it }
                Actualizaciones.instalar(getApplication(), apk)
            } catch (e: Exception) {
                avisar("No se pudo descargar. Revise su internet.")
            }
            descarga = null
        }
    }

    /** Después de instalar una versión nueva, lo dice una vez. */
    fun avisarSiSeActualizo() {
        val anterior = prefs.getString("version", null)
        if (anterior == BuildConfig.VERSION_NAME) return
        prefs.edit().putString("version", BuildConfig.VERSION_NAME).apply()
        if (anterior != null) avisar("Listo, ya tiene la versión ${BuildConfig.VERSION_NAME}")
    }

    // ---- bloqueo con la huella o la clave del celular
    val puedeBloquear: Boolean
        get() = BiometricManager.from(getApplication()).canAuthenticate(FORMAS_DE_DESBLOQUEO) == BiometricManager.BIOMETRIC_SUCCESS
    var bloqueoPedido by mutableStateOf(prefs.getBoolean("bloqueo", true))
        private set
    val bloqueoActivo get() = bloqueoPedido && puedeBloquear
    var bloqueado by mutableStateOf(bloqueoActivo)

    fun cambiarBloqueo(activo: Boolean) {
        prefs.edit().putBoolean("bloqueo", activo).apply()
        bloqueoPedido = activo
    }

    init {
        desde = dia(ahora).withDayOfMonth(1)
        hasta = dia(ahora)
        if (BuildConfig.MODO_DESARROLLO) viewModelScope.launch { repo.cargarEjemplo() }
        viewModelScope.launch {
            // El reloj de la app avanza cada minuto ("hace 5 días" cambia solo) y la nube se revisa cada 2
            var minutos = 0
            while (true) {
                delay(60_000)
                ahora = System.currentTimeMillis()
                if (++minutos % 2 == 0) nube.pedir(0)
            }
        }
    }

    override fun onCleared() { db.close() }

    private inline fun <reified E : Enum<E>> enumDe(nombre: String?, defecto: E): E =
        enumValues<E>().firstOrNull { it.name == nombre } ?: defecto

    companion object {
        const val FORMAS_DE_DESBLOQUEO = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}
