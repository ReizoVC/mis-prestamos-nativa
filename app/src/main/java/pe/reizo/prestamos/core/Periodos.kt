package pe.reizo.prestamos.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class Periodo(val etiqueta: String) {
    HOY("Hoy"), SEMANA("Semana"), MES("Mes"), ANIO("Año"), FECHAS("Elegir fechas")
}

data class Rango(val desde: Long, val hasta: Long, val texto: String)

/** Rango de fechas del resumen, en hora de Lima. La semana empieza el lunes. */
fun rangoDe(periodo: Periodo, ahora: Long, desde: LocalDate? = null, hasta: LocalDate? = null): Rango {
    val hoy = dia(ahora)
    return when (periodo) {
        Periodo.HOY -> Rango(inicioDia(hoy), finDia(hoy), "Hoy, ${fechaLarga(hoy)}")
        Periodo.SEMANA -> {
            val lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Rango(inicioDia(lunes), finDia(hoy), "Del ${diaMes(lunes)} al ${diaMes(hoy)}")
        }
        Periodo.MES -> Rango(inicioDia(hoy.withDayOfMonth(1)), finDia(hoy), mesLargo(hoy))
        Periodo.ANIO -> Rango(inicioDia(hoy.withDayOfYear(1)), finDia(hoy), "Año ${hoy.year}")
        Periodo.FECHAS -> {
            val a = desde ?: hoy.withDayOfMonth(1)
            val b = hasta ?: hoy
            val (ini, fin) = if (a.isAfter(b)) b to a else a to b
            Rango(inicioDia(ini), finDia(fin), "Del ${diaMes(ini)} al ${diaMes(fin)}")
        }
    }
}
