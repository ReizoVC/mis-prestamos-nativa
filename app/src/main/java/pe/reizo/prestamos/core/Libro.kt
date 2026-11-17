package pe.reizo.prestamos.core

import java.text.Collator
import java.util.Locale

/** Valor de fábrica. Ella lo puede cambiar en Ajustes (7, 15 o 30 días). */
const val DIAS_SIN_PAGAR = 15
val OPCIONES_DIAS_SIN_PAGAR = listOf(7, 15, 30)

enum class Filtro(val etiqueta: String) {
    TODOS("Todos"), DEBEN_MAS("Deben más"), SIN_PAGAR("Sin pagar"), NUEVOS("Nuevos")
}

data class Resumen(
    val presto: Long,
    val cobro: Long,
    val gano: Long,
    val prestados: List<Prestamo>,    // forman "Prestó", el más reciente primero
    val cobrados: List<Pago>,         // forman "Cobró", el más reciente primero
    val terminados: List<Prestamo>,   // forman "Ganó", por fecha de término
)

fun ganancia(p: Prestamo): Long = p.montoAPagar - p.montoPrestado

/**
 * Fecha en que el préstamo quedó pagado según sus pagos vigentes, o null si todavía debe.
 * Si ya estaba cerrado y sigue completo, conserva su fecha.
 */
fun pagadoEnSegun(p: Prestamo, pagosVigentes: List<Pago>): Long? {
    val pagos = pagosVigentes.filter { !it.borrado }
    if (pagos.sumOf { it.monto } < p.montoAPagar) return null
    return p.pagadoEn ?: pagos.maxOf { it.fecha }
}

/** Foto de todos los datos. Todas las cuentas de la app salen de aquí. */
class Libro(
    clientes: List<Cliente>,
    prestamos: List<Prestamo>,
    pagos: List<Pago>,
    /** Desde cuántos días sin pagar un cliente pasa a "Sin pagar". */
    val diasSinPagar: Int = DIAS_SIN_PAGAR,
) {
    val clientes: List<Cliente> = clientes.filter { !it.borrado }
    /** Clientes eliminados (papelera), el más reciente primero. */
    val eliminados: List<Cliente> = clientes.filter { it.borrado }.sortedByDescending { it.actualizadoEn }
    val prestamos: List<Prestamo> = prestamos.filter { !it.borrado }
    val pagos: List<Pago> = pagos.filter { !it.borrado }

    private val clientePorId = this.clientes.associateBy { it.id }
    private val prestamoPorId = this.prestamos.associateBy { it.id }
    private val porCliente = this.prestamos.groupBy { it.clienteId }
    private val porPrestamo = this.pagos.groupBy { it.prestamoId }
    private val alfabeto = Collator.getInstance(Locale("es"))

    fun cliente(id: String): Cliente? = clientePorId[id]
    fun prestamo(id: String): Prestamo? = prestamoPorId[id]

    /** Préstamos del cliente, el más reciente primero. */
    fun prestamosDe(clienteId: String): List<Prestamo> = porCliente[clienteId].orEmpty().sortedByDescending { it.creadoEn }

    /** Pagos del préstamo, el más reciente primero. */
    fun pagosDe(prestamoId: String): List<Pago> = porPrestamo[prestamoId].orEmpty().sortedByDescending { it.fecha }

    fun pagado(p: Prestamo): Long = porPrestamo[p.id].orEmpty().sumOf { it.monto }
    fun falta(p: Prestamo): Long = (p.montoAPagar - pagado(p)).coerceAtLeast(0)

    fun activosDe(clienteId: String): List<Prestamo> = prestamosDe(clienteId).filter { it.pagadoEn == null }
    fun pagadosDe(clienteId: String): List<Prestamo> =
        prestamosDe(clienteId).filter { it.pagadoEn != null }.sortedByDescending { it.pagadoEn }

    fun deuda(clienteId: String): Long = activosDe(clienteId).sumOf { falta(it) }
    fun esActivo(clienteId: String): Boolean = porCliente[clienteId].orEmpty().any { it.pagadoEn == null }
    fun esAntiguo(clienteId: String): Boolean = !porCliente[clienteId].isNullOrEmpty() && !esActivo(clienteId)

    fun ultimoPago(clienteId: String): Pago? =
        activosDe(clienteId).flatMap { porPrestamo[it.id].orEmpty() }.maxByOrNull { it.fecha }

    /** Fecha del último pago; si no hay pagos, la del préstamo activo más antiguo. */
    fun ultimoMovimiento(clienteId: String): Long? =
        ultimoPago(clienteId)?.fecha ?: activosDe(clienteId).minOfOrNull { it.creadoEn }

    fun diasSinPagar(clienteId: String, ahora: Long): Int =
        ultimoMovimiento(clienteId)?.let { diasEntre(it, ahora) } ?: 0

    fun estaSinPagar(clienteId: String, ahora: Long): Boolean =
        esActivo(clienteId) && diasSinPagar(clienteId, ahora) >= diasSinPagar

    /** Texto bajo el nombre en la lista de clientes. */
    fun estado(clienteId: String, ahora: Long): String {
        val pago = ultimoPago(clienteId)
        val dias = diasSinPagar(clienteId, ahora)
        return when {
            dias >= diasSinPagar && pago != null -> "Sin pagos hace $dias días"
            dias >= diasSinPagar -> "Sin pagos desde que prestó ($dias días)"
            pago != null -> "Pagó ${soles(pago.monto)} ${haceDias(dias)}"
            else -> "Sin pagos aún · prestó ${haceDias(dias)}"
        }
    }

    private fun coincide(c: Cliente, texto: String): Boolean {
        val t = normalizar(texto)
        return t.isEmpty() || normalizar(c.nombre).contains(t) || c.dni.contains(t)
    }

    fun clientesActivos(texto: String, filtro: Filtro, ahora: Long): List<Cliente> {
        val base = clientes.filter { esActivo(it.id) && coincide(it, texto) }
        return when (filtro) {
            Filtro.TODOS -> base.sortedWith { a, b -> alfabeto.compare(a.nombre, b.nombre) }
            Filtro.DEBEN_MAS -> base.sortedByDescending { deuda(it.id) }
            Filtro.SIN_PAGAR -> base.filter { estaSinPagar(it.id, ahora) }.sortedByDescending { diasSinPagar(it.id, ahora) }
            Filtro.NUEVOS -> base.sortedByDescending { c -> activosDe(c.id).maxOf { it.creadoEn } }
        }
    }

    fun hayActivos(): Boolean = clientes.any { esActivo(it.id) }

    /** Fecha en que el cliente antiguo terminó su último préstamo. */
    fun terminoEn(clienteId: String): Long? = porCliente[clienteId].orEmpty().mapNotNull { it.pagadoEn }.maxOrNull()

    fun clientesAntiguos(texto: String): List<Cliente> =
        clientes.filter { esAntiguo(it.id) && coincide(it, texto) }.sortedByDescending { terminoEn(it.id) }

    /** Hasta 5 clientes (activos y antiguos) para autocompletar. */
    fun sugerencias(texto: String): List<Cliente> {
        if (texto.trim().length < 2) return emptyList()
        return clientes.filter { coincide(it, texto) }
            .sortedWith { a, b -> alfabeto.compare(a.nombre, b.nombre) }
            .take(5)
    }

    val leDebenHoy: Long get() = prestamos.filter { it.pagadoEn == null }.sumOf { falta(it) }
    val ganara: Long get() = prestamos.filter { it.pagadoEn == null }.sumOf { ganancia(it) }

    fun resumen(rango: Rango): Resumen {
        val dentro = rango.desde..rango.hasta
        val prestados = prestamos.filter { it.creadoEn in dentro }.sortedByDescending { it.creadoEn }
        val cobrados = pagos.filter { it.fecha in dentro }.sortedByDescending { it.fecha }
        val terminados = prestamos.filter { it.pagadoEn != null && it.pagadoEn in dentro }.sortedByDescending { it.pagadoEn }
        return Resumen(
            presto = prestados.sumOf { it.montoPrestado },
            cobro = cobrados.sumOf { it.monto },
            gano = terminados.sumOf { ganancia(it) },
            prestados = prestados,
            cobrados = cobrados,
            terminados = terminados,
        )
    }
}
