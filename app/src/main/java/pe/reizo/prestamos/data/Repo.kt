package pe.reizo.prestamos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import pe.reizo.prestamos.core.Cliente
import pe.reizo.prestamos.core.Pago
import pe.reizo.prestamos.core.Prestamo
import pe.reizo.prestamos.core.datosEjemplo
import pe.reizo.prestamos.core.pagadoEnSegun
import java.time.Instant
import java.util.UUID

/** Todo cambio de datos pasa por aquí. Cada cambio queda pendiente de subir y avisa a la nube. */
class Repo(private val db: BaseDatos, private val alCambiar: () -> Unit) {
    private val t = db.tablas()
    private fun nuevoId() = UUID.randomUUID().toString()

    /** Crea el préstamo (y el cliente si es nuevo). Devuelve el id del cliente. */
    suspend fun crearPrestamo(
        clienteId: String?, nombre: String, celular: String, dni: String, prestado: Long, aPagar: Long,
    ): String {
        val ahora = System.currentTimeMillis()
        val id = db.withTransaction {
            val existente = clienteId?.let { t.cliente(it) }
            val cid = existente?.id ?: nuevoId()
            if (existente == null) {
                t.guardar(Cliente(cid, nombre.trim(), celular, dni, ahora, ahora))
            } else if (existente.celular != celular || existente.dni != dni) {
                t.guardar(existente.copy(celular = celular, dni = dni, actualizadoEn = ahora, pendiente = true))
            }
            t.guardar(Prestamo(nuevoId(), cid, prestado, aPagar, ahora, null, ahora))
            cid
        }
        alCambiar()
        return id
    }

    /** Devuelve true si con este pago terminó de pagar el préstamo. */
    suspend fun registrarPago(prestamoId: String, monto: Long): Boolean {
        val ahora = System.currentTimeMillis()
        val termino = db.withTransaction {
            t.guardar(Pago(nuevoId(), prestamoId, monto, ahora, ahora))
            recalcular(prestamoId, ahora)
        }
        alCambiar()
        return termino
    }

    suspend fun corregirPago(pagoId: String, monto: Long) = cambiarPago(pagoId) { it.copy(monto = monto) }
    suspend fun borrarPago(pagoId: String) = cambiarPago(pagoId) { it.copy(borrado = true) }
    suspend fun restaurarPago(pagoId: String) = cambiarPago(pagoId) { it.copy(borrado = false) }

    /** Lo que se marcó como borrado al eliminar un cliente, para poder deshacerlo. */
    class Eliminado(val clienteId: String, val prestamos: List<String>, val pagos: List<String>)

    /** Elimina al cliente con sus préstamos y pagos. Nada se borra de verdad: quedan marcados como borrados. */
    suspend fun eliminarCliente(clienteId: String): Eliminado = marcarCliente(clienteId, null)

    suspend fun restaurarCliente(e: Eliminado) { marcarCliente(e.clienteId, e) }

    private suspend fun marcarCliente(clienteId: String, deshacer: Eliminado?): Eliminado {
        val ahora = System.currentTimeMillis()
        val borrar = deshacer == null
        val hecho = db.withTransaction {
            val prestamos = deshacer?.prestamos?.mapNotNull { t.prestamo(it) } ?: t.prestamosVigentes(clienteId)
            val pagos = deshacer?.pagos?.mapNotNull { t.pago(it) } ?: prestamos.flatMap { t.pagosVigentes(it.id) }
            t.cliente(clienteId)?.let { t.guardar(it.copy(borrado = borrar, actualizadoEn = ahora, pendiente = true)) }
            prestamos.forEach { t.guardar(it.copy(borrado = borrar, actualizadoEn = ahora, pendiente = true)) }
            pagos.forEach { t.guardar(it.copy(borrado = borrar, actualizadoEn = ahora, pendiente = true)) }
            Eliminado(clienteId, prestamos.map { it.id }, pagos.map { it.id })
        }
        alCambiar()
        return hecho
    }

    private suspend fun cambiarPago(pagoId: String, cambio: (Pago) -> Pago) {
        val ahora = System.currentTimeMillis()
        db.withTransaction {
            val pago = t.pago(pagoId) ?: return@withTransaction
            t.guardar(cambio(pago).copy(actualizadoEn = ahora, pendiente = true))
            recalcular(pago.prestamoId, ahora)
        }
        alCambiar()
    }

    /** Cierra el préstamo si ya está completo, o lo reabre si volvió a deber. */
    private suspend fun recalcular(prestamoId: String, ahora: Long): Boolean {
        val p = t.prestamo(prestamoId) ?: return false
        val pagadoEn = pagadoEnSegun(p, t.pagosVigentes(prestamoId))
        if (pagadoEn != p.pagadoEn) t.guardar(p.copy(pagadoEn = pagadoEn, actualizadoEn = ahora, pendiente = true))
        return pagadoEn != null
    }

    // ---- mezcla de filas que llegan de la nube o de un respaldo
    // De la nube: reemplaza si la fila local no tiene cambios sin subir, o si la remota es más nueva.
    // De un respaldo: entra solo si no existe o es más nueva, y queda pendiente de subir.

    suspend fun mezclar(r: Cliente, deNube: Boolean) {
        val l = t.cliente(r.id)
        if (l == null || (deNube && !l.pendiente) || r.actualizadoEn > l.actualizadoEn) t.guardar(r.copy(pendiente = !deNube))
    }

    suspend fun mezclar(r: Prestamo, deNube: Boolean) {
        val l = t.prestamo(r.id)
        if (l == null || (deNube && !l.pendiente) || r.actualizadoEn > l.actualizadoEn) t.guardar(r.copy(pendiente = !deNube))
    }

    suspend fun mezclar(r: Pago, deNube: Boolean) {
        val l = t.pago(r.id)
        if (l == null || (deNube && !l.pendiente) || r.actualizadoEn > l.actualizadoEn) t.guardar(r.copy(pendiente = !deNube))
    }

    // ---- respaldo (mismo formato que la versión web, así sirve para pasar datos de una a otra)

    suspend fun respaldo(version: String): String {
        fun lista(filas: List<JSONObject>) = JSONArray().apply { filas.forEach { put(it) } }
        return JSONObject()
            .put("app", "mis-prestamos")
            .put("version", version)
            .put("creado_en", Instant.now().toString())
            .put("clientes", lista(t.clientes().map { it.aJson(false) }))
            .put("prestamos", lista(t.prestamos().map { it.aJson(false) }))
            .put("pagos", lista(t.pagos().map { it.aJson(false) }))
            .toString(2)
    }

    /** Mezcla un respaldo con lo que ya hay. Devuelve cuántos clientes traía. Lanza excepción si el archivo no sirve. */
    suspend fun recuperar(texto: String): Int {
        val o = JSONObject(texto)
        require(o.optString("app") == "mis-prestamos") { "No es un respaldo de Mis Préstamos" }
        fun <T> filas(k: String, f: (JSONObject) -> T): List<T> {
            val a = o.getJSONArray(k)
            return List(a.length()) { f(a.getJSONObject(it)) }
        }
        val clientes = filas("clientes", ::clienteDe)
        val prestamos = filas("prestamos", ::prestamoDe)
        val pagos = filas("pagos", ::pagoDe)
        db.withTransaction {
            clientes.forEach { mezclar(it, deNube = false) }
            prestamos.forEach { mezclar(it, deNube = false) }
            pagos.forEach { mezclar(it, deNube = false) }
        }
        alCambiar()
        return clientes.count { !it.borrado }
    }

    /** Deja solo los clientes de ejemplo (borra lo demás). Solo se usa en modo desarrollo. */
    suspend fun cargarEjemplo() {
        val e = datosEjemplo(System.currentTimeMillis())
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
        db.withTransaction {
            e.clientes.forEach { t.guardar(it) }
            e.prestamos.forEach { t.guardar(it) }
            e.pagos.forEach { t.guardar(it) }
        }
        alCambiar()
    }
}
