package pe.reizo.prestamos.core

import java.time.LocalDate
import java.time.YearMonth

enum class EstadoDia { FUERA, PRESTO, PAGO, SIN_PAGO, HOY, FUTURO }

data class DiaTarjeta(val fecha: LocalDate, val estado: EstadoDia, val monto: Long)

/** Último día que cuenta en la tarjeta: el que terminó de pagar o, si todavía debe, hoy. */
private fun finTarjeta(p: Prestamo, hoy: LocalDate): LocalDate = p.pagadoEn?.let(::dia) ?: hoy

/** Meses que abarca el préstamo, del primero al último. */
fun mesesTarjeta(p: Prestamo, hoy: LocalDate): List<YearMonth> {
    val fin = YearMonth.from(finTarjeta(p, hoy))
    return generateSequence(YearMonth.from(dia(p.creadoEn))) { it.plusMonths(1) }.takeWhile { it <= fin }.toList()
}

/**
 * Cada día del mes con lo que pasó: pagó (y cuánto), no pagó, el día que prestó, hoy (todavía puede pagar)
 * o fuera del préstamo. Varios pagos el mismo día se suman.
 */
fun diasTarjeta(p: Prestamo, pagos: List<Pago>, mes: YearMonth, hoy: LocalDate): List<DiaTarjeta> {
    val inicio = dia(p.creadoEn)
    val fin = finTarjeta(p, hoy)
    val porDia = pagos.filter { !it.borrado }.groupBy { dia(it.fecha) }.mapValues { (_, l) -> l.sumOf { it.monto } }
    return (1..mes.lengthOfMonth()).map { n ->
        val d = mes.atDay(n)
        val monto = porDia[d] ?: 0
        val estado = when {
            monto > 0 -> EstadoDia.PAGO
            d < inicio -> EstadoDia.FUERA
            d > fin -> if (d > hoy) EstadoDia.FUTURO else EstadoDia.FUERA
            d == inicio -> EstadoDia.PRESTO
            d == hoy -> EstadoDia.HOY
            else -> EstadoDia.SIN_PAGO
        }
        DiaTarjeta(d, estado, monto)
    }
}
