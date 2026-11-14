package pe.reizo.prestamos.core

import java.math.BigDecimal
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

val LIMA: ZoneId = ZoneId.of("America/Lima")

private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "set", "oct", "nov", "dic")
private val MESES_LARGOS = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "setiembre", "octubre", "noviembre", "diciembre",
)

fun dia(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(LIMA).toLocalDate()
fun inicioDia(d: LocalDate): Long = d.atStartOfDay(LIMA).toInstant().toEpochMilli()
fun finDia(d: LocalDate): Long = inicioDia(d.plusDays(1)) - 1

/** Días calendario en Lima entre dos momentos. */
fun diasEntre(desde: Long, hasta: Long): Int = ChronoUnit.DAYS.between(dia(desde), dia(hasta)).toInt()

/** "S/ 1,250" o "S/ 1,250.50" si tiene céntimos. */
fun soles(centimos: Long): String {
    val a = abs(centimos)
    val entero = String.format(Locale.US, "%,d", a / 100)
    val dec = if (a % 100 != 0L) String.format(Locale.US, ".%02d", a % 100) else ""
    return (if (centimos < 0) "- " else "") + "S/ " + entero + dec
}

/** Lo que se pone en un campo de monto: "420" o "420.50". */
fun montoParaCampo(centimos: Long): String =
    if (centimos % 100 == 0L) (centimos / 100).toString()
    else String.format(Locale.US, "%d.%02d", centimos / 100, centimos % 100)

/** Acepta coma o punto como decimal. Devuelve céntimos, o null si no es un monto. */
fun parseMonto(texto: String): Long? {
    val s = texto.trim().replace(',', '.')
    if (!Regex("""\d{1,9}(\.\d{0,2})?""").matches(s)) return null
    return BigDecimal(s.trimEnd('.')).movePointRight(2).toLong()
}

/** Deja solo dígitos y un separador decimal con dos decimales como máximo. */
fun limpiarMonto(texto: String): String {
    val sb = StringBuilder()
    var decimales = -1
    for (ch in texto) {
        when {
            ch.isDigit() && decimales < 0 -> if (sb.length < 9) sb.append(ch)
            ch.isDigit() && decimales < 2 -> { sb.append(ch); decimales++ }
            (ch == '.' || ch == ',') && decimales < 0 && sb.isNotEmpty() -> { sb.append('.'); decimales = 0 }
        }
    }
    return sb.toString()
}

/** "4 oct", y con año si no es el año en curso: "4 oct 2025". */
fun fechaCorta(ms: Long, ahora: Long = System.currentTimeMillis()): String {
    val d = dia(ms)
    val base = "${d.dayOfMonth} ${MESES[d.monthValue - 1]}"
    return if (d.year == dia(ahora).year) base else "$base ${d.year}"
}

fun fechaLarga(d: LocalDate): String = "${d.dayOfMonth} de ${MESES_LARGOS[d.monthValue - 1]}"
fun mesLargo(d: LocalDate): String = MESES_LARGOS[d.monthValue - 1].replaceFirstChar { it.uppercase() } + " ${d.year}"
fun diaMes(d: LocalDate): String = "${d.dayOfMonth} ${MESES[d.monthValue - 1]}"

fun haceDias(dias: Int): String = when {
    dias <= 0 -> "hoy"
    dias == 1 -> "ayer"
    else -> "hace $dias días"
}

fun celularBonito(c: String): String = if (c.length == 9) "${c.substring(0, 3)} ${c.substring(3, 6)} ${c.substring(6)}" else c

fun primerNombre(nombre: String): String = nombre.trim().substringBefore(' ')

/** Sin tildes y en minúsculas, para buscar. */
fun normalizar(s: String): String =
    Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
