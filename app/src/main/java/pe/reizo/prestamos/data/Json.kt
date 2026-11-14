package pe.reizo.prestamos.data

import org.json.JSONObject
import pe.reizo.prestamos.core.Cliente
import pe.reizo.prestamos.core.Pago
import pe.reizo.prestamos.core.Prestamo
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.OffsetDateTime

// Un solo formato de filas para dos usos:
//  - respaldo (iso = false): fechas en milisegundos, igual que el respaldo de la versión web
//  - nube (iso = true): fechas ISO, como las espera Supabase
// En ambos los montos van en soles con decimales.

private fun fechaJson(ms: Long, iso: Boolean): Any = if (iso) Instant.ofEpochMilli(ms).toString() else ms
private fun solesJson(centimos: Long): Any = BigDecimal.valueOf(centimos, 2)

private fun JSONObject.fecha(k: String): Long = when (val v = get(k)) {
    is Number -> v.toLong()
    else -> OffsetDateTime.parse(v.toString()).toInstant().toEpochMilli()
}

private fun JSONObject.centimos(k: String): Long =
    BigDecimal(get(k).toString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

private fun JSONObject.borrado(): Boolean = optBoolean("borrado", false)

fun Cliente.aJson(iso: Boolean): JSONObject = JSONObject()
    .put("id", id).put("nombre", nombre).put("celular", celular).put("dni", dni)
    .put("creado_en", fechaJson(creadoEn, iso)).put("actualizado_en", fechaJson(actualizadoEn, iso))
    .put("borrado", borrado)

fun Prestamo.aJson(iso: Boolean): JSONObject = JSONObject()
    .put("id", id).put("cliente_id", clienteId)
    .put("monto_prestado", solesJson(montoPrestado)).put("monto_a_pagar", solesJson(montoAPagar))
    .put("creado_en", fechaJson(creadoEn, iso))
    .put("pagado_en", pagadoEn?.let { fechaJson(it, iso) } ?: JSONObject.NULL)
    .put("actualizado_en", fechaJson(actualizadoEn, iso))
    .put("borrado", borrado)
    .put("nota", nota)

fun Pago.aJson(iso: Boolean): JSONObject = JSONObject()
    .put("id", id).put("prestamo_id", prestamoId)
    .put("monto", solesJson(monto))
    .put("fecha", fechaJson(fecha, iso)).put("actualizado_en", fechaJson(actualizadoEn, iso))
    .put("borrado", borrado)

fun clienteDe(o: JSONObject): Cliente = Cliente(
    id = o.getString("id"), nombre = o.getString("nombre"), celular = o.getString("celular"), dni = o.getString("dni"),
    creadoEn = o.fecha("creado_en"), actualizadoEn = o.fecha("actualizado_en"), borrado = o.borrado(),
)

fun prestamoDe(o: JSONObject): Prestamo = Prestamo(
    id = o.getString("id"), clienteId = o.getString("cliente_id"),
    montoPrestado = o.centimos("monto_prestado"), montoAPagar = o.centimos("monto_a_pagar"),
    creadoEn = o.fecha("creado_en"), pagadoEn = if (o.isNull("pagado_en")) null else o.fecha("pagado_en"),
    actualizadoEn = o.fecha("actualizado_en"), borrado = o.borrado(),
    nota = if (o.isNull("nota")) "" else o.optString("nota"),
)

fun pagoDe(o: JSONObject): Pago = Pago(
    id = o.getString("id"), prestamoId = o.getString("prestamo_id"), monto = o.centimos("monto"),
    fecha = o.fecha("fecha"), actualizadoEn = o.fecha("actualizado_en"), borrado = o.borrado(),
)
