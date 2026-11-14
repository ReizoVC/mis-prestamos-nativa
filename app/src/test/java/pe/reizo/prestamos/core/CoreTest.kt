package pe.reizo.prestamos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class CoreTest {
    private fun lima(a: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        ZonedDateTime.of(a, m, d, h, min, 0, 0, LIMA).toInstant().toEpochMilli()

    private val ahora = lima(2026, 11, 13)
    private val ejemplo = datosEjemplo(ahora)
    private val libro = Libro(ejemplo.clientes, ejemplo.prestamos, ejemplo.pagos)

    private fun id(nombre: String) = libro.clientes.first { it.nombre.startsWith(nombre) }.id
    private fun nombres(l: List<Cliente>) = l.map { primerNombre(it.nombre) }

    private val c = Cliente("c1", "Rosa", "987112233", "45127893", 0, 0)
    private val p = Prestamo("p1", "c1", 50_000, 60_000, 0, null, 0)
    private fun pago(id: String, monto: Long, fecha: Long = 1000, borrado: Boolean = false) =
        Pago(id, "p1", monto, fecha, 0, borrado)

    // ---- cálculos

    @Test fun `pagado, falta y deuda ignoran pagos borrados`() {
        val l = Libro(listOf(c), listOf(p), listOf(pago("a", 10_000), pago("b", 8_000), pago("x", 5_000, borrado = true)))
        assertEquals(18_000, l.pagado(p))
        assertEquals(42_000, l.falta(p))
        assertEquals(42_000, l.deuda("c1"))
    }

    @Test fun `sin pagos falta todo`() {
        val l = Libro(listOf(c), listOf(p), emptyList())
        assertEquals(0, l.pagado(p))
        assertEquals(60_000, l.falta(p))
        assertEquals(10_000, ganancia(p))
    }

    @Test fun `falta nunca es negativo`() {
        val l = Libro(listOf(c), listOf(p), listOf(pago("a", 70_000)))
        assertEquals(0, l.falta(p))
    }

    @Test fun `los decimales se suman exactos`() {
        assertEquals(10L, parseMonto("0.1"))
        assertEquals(20L, parseMonto("0,2"))
        val l = Libro(listOf(c), listOf(p), listOf(pago("a", parseMonto("0.1")!!), pago("b", parseMonto("0.2")!!)))
        assertEquals(30, l.pagado(p))
        assertEquals("S/ 0.30", soles(l.pagado(p)))
    }

    // ---- cierre y reapertura

    @Test fun `el pago que completa cierra el prestamo y borrarlo lo reabre`() {
        val pagos = listOf(pago("a", 20_000, 1000), pago("b", 40_000, 5000))
        assertEquals(5000L, pagadoEnSegun(p, pagos))
        assertNull(pagadoEnSegun(p.copy(pagadoEn = 5000), listOf(pagos[0], pagos[1].copy(borrado = true))))
        assertNull(pagadoEnSegun(p, listOf(pagos[0])))
    }

    @Test fun `cliente sin prestamos activos es antiguo`() {
        val l = Libro(listOf(c), listOf(p.copy(pagadoEn = 5000)), listOf(pago("a", 60_000, 5000)))
        assertTrue(l.esAntiguo("c1"))
        assertFalse(l.esActivo("c1"))
        assertEquals(0, l.deuda("c1"))
    }

    // ---- sin pagar

    @Test fun `dias sin pagar cruza la medianoche de Lima`() {
        val prestamo = p.copy(creadoEn = lima(2026, 11, 12, 23, 30))
        val l = Libro(listOf(c), listOf(prestamo), emptyList())
        assertEquals(0, l.diasSinPagar("c1", lima(2026, 11, 12, 23, 59)))
        assertEquals(1, l.diasSinPagar("c1", lima(2026, 11, 13, 0, 10)))
    }

    @Test fun `sin pagar empieza a los 15 dias`() {
        val l = Libro(listOf(c), listOf(p.copy(creadoEn = lima(2026, 10, 30))), emptyList())
        assertFalse(l.estaSinPagar("c1", lima(2026, 11, 13)))
        assertTrue(l.estaSinPagar("c1", lima(2026, 11, 14)))
    }

    @Test fun `texto de estado`() {
        assertEquals("Sin pagos hace 22 días", libro.estado(id("Rosa"), ahora))
        assertEquals("Sin pagos desde que prestó (25 días)", libro.estado(id("Pedro"), ahora))
        assertEquals("Pagó S/ 50 hace 5 días", libro.estado(id("Carmen"), ahora))
        assertEquals("Pagó S/ 60 ayer", libro.estado(id("Wilmer"), ahora))
        assertEquals("Sin pagos aún · prestó hace 3 días", libro.estado(id("Gladys"), ahora))
    }

    // ---- filtros con los datos de ejemplo

    @Test fun `filtro Todos ordena por nombre`() {
        assertEquals(
            listOf("Carmen", "Gladys", "Julio", "Lucía", "Pedro", "Rosa", "Teodoro", "Wilmer"),
            nombres(libro.clientesActivos("", Filtro.TODOS, ahora)),
        )
    }

    @Test fun `filtro Deben mas ordena por deuda`() {
        assertEquals(
            listOf("Teodoro", "Julio", "Gladys", "Rosa", "Pedro", "Carmen", "Lucía", "Wilmer"),
            nombres(libro.clientesActivos("", Filtro.DEBEN_MAS, ahora)),
        )
        assertEquals(64_000, libro.deuda(id("Teodoro")))
    }

    @Test fun `filtro Sin pagar muestra solo atrasados`() {
        assertEquals(listOf("Teodoro", "Pedro", "Rosa"), nombres(libro.clientesActivos("", Filtro.SIN_PAGAR, ahora)))
    }

    @Test fun `filtro Nuevos ordena por prestamo mas reciente`() {
        assertEquals(
            listOf("Gladys", "Lucía", "Carmen", "Wilmer", "Pedro", "Rosa", "Julio", "Teodoro"),
            nombres(libro.clientesActivos("", Filtro.NUEVOS, ahora)),
        )
    }

    @Test fun `el buscador ignora tildes y mayusculas y busca por DNI`() {
        assertEquals(listOf("Teodoro"), nombres(libro.clientesActivos("ccahuana", Filtro.TODOS, ahora)))
        assertEquals(listOf("Martha"), nombres(libro.clientesAntiguos("chavez")))
        assertEquals(listOf("Rosa"), nombres(libro.clientesActivos("45127893", Filtro.TODOS, ahora)))
        assertEquals(listOf("Lucía"), nombres(libro.sugerencias("LUCIA")))
    }

    @Test fun `antiguos por termino mas reciente`() {
        assertEquals(listOf("Félix", "Martha", "Mario", "Ana"), nombres(libro.clientesAntiguos("")))
    }

    // ---- periodos y resumen

    @Test fun `la semana empieza el lunes`() {
        val r = rangoDe(Periodo.SEMANA, lima(2026, 11, 13)) // viernes
        assertEquals(LocalDate.of(2026, 11, 9), dia(r.desde))
        assertEquals("Del 9 nov al 13 nov", r.texto)
        assertEquals(LocalDate.of(2026, 11, 13), dia(r.hasta))
    }

    @Test fun `textos de los periodos`() {
        assertEquals("Hoy, 13 de noviembre", rangoDe(Periodo.HOY, ahora).texto)
        assertEquals("Noviembre 2026", rangoDe(Periodo.MES, ahora).texto)
        assertEquals("Año 2026", rangoDe(Periodo.ANIO, ahora).texto)
    }

    @Test fun `fechas invertidas se intercambian`() {
        val r = rangoDe(Periodo.FECHAS, ahora, LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 1))
        assertEquals("Del 1 nov al 13 nov", r.texto)
        assertTrue(r.desde < r.hasta)
    }

    @Test fun `la ganancia cuenta solo prestamos terminados dentro del rango`() {
        val mes = libro.resumen(rangoDe(Periodo.MES, ahora))
        // En noviembre solo terminó Félix (hace 6 días): 600 - 500
        assertEquals(10_000, mes.gano)
        assertEquals(1, mes.terminados.size)
        val anio = libro.resumen(rangoDe(Periodo.ANIO, ahora))
        // Félix 100 + Martha 20 + Ana 60 + Mario 120 + 40
        assertEquals(34_000, anio.gano)
        assertEquals(0, libro.resumen(rangoDe(Periodo.HOY, ahora)).gano)
    }

    @Test fun `presto y cobro del mes`() {
        val mes = libro.resumen(rangoDe(Periodo.MES, ahora))
        // Préstamos de noviembre: Carmen 200 (12), Lucía 150 (9), Gladys 400 (3)
        assertEquals(75_000, mes.presto)
        // Pagos de noviembre: Julio 200 (8), Carmen 50 (5), Lucía 30 (2), Wilmer 100 (10) y 60 (1), Félix 300 (6)
        assertEquals(74_000, mes.cobro)
        assertEquals(9, mes.movimientos.size)
        assertTrue(mes.movimientos.zipWithNext().all { (a, b) -> a.fecha >= b.fecha })
    }

    @Test fun `le deben hoy y ganara`() {
        assertEquals(288_000, libro.leDebenHoy)
        assertEquals(72_000, libro.ganara)
    }

    // ---- validaciones y formato

    @Test fun validaciones() {
        assertNotNull(errorNombre("  "))
        assertNull(errorNombre("Rosa"))
        assertNotNull(errorCelular("887112233"))
        assertNotNull(errorCelular("98711223"))
        assertNull(errorCelular("987112233"))
        assertNotNull(errorDni("4512789"))
        assertNull(errorDni("45127893"))
        assertNotNull(errorPrestado(0))
        assertNotNull(errorPrestado(null))
        assertNotNull(errorAPagar(40_000, 50_000))
        assertNull(errorAPagar(50_000, 50_000))
        assertEquals("Escriba cuánto le pagó.", errorPago(null, 42_000))
        assertEquals("Es más de lo que debe. Le falta S/ 420.", errorPago(50_000, 42_000))
        assertNull(errorPago(42_000, 42_000))
    }

    @Test fun `DNI repetido`() {
        assertEquals(
            "Ese DNI ya es de Rosa Huamán. Búsquelo por su nombre.",
            errorDniRepetido("45127893", null, libro.clientes),
        )
        assertNull(errorDniRepetido("45127893", id("Rosa"), libro.clientes))
        assertNull(errorDniRepetido("11111111", null, libro.clientes))
    }

    @Test fun `comparacion de versiones`() {
        assertTrue(esVersionMasNueva("1.0.1", "1.0.0"))
        assertTrue(esVersionMasNueva("v1.10.0", "1.9.9"))
        assertTrue(esVersionMasNueva("2.0", "1.9.9"))
        assertFalse(esVersionMasNueva("1.0.0", "1.0.0"))
        assertFalse(esVersionMasNueva("1.0.0", "1.0.1"))
        assertFalse(esVersionMasNueva("basura", "1.0.0"))
    }

    @Test fun `los eliminados van a la papelera y no cuentan`() {
        val borrado = c.copy(id = "c2", nombre = "Ana", borrado = true, actualizadoEn = 9)
        val l = Libro(listOf(c, borrado), listOf(p, p.copy(id = "p2", clienteId = "c2", borrado = true)), emptyList())
        assertEquals(listOf("Rosa"), l.clientes.map { it.nombre })
        assertEquals(listOf("Ana"), l.eliminados.map { it.nombre })
        assertEquals(60_000, l.leDebenHoy)
    }

    @Test fun `mensaje de WhatsApp`() {
        assertEquals("Hola Rosa, le escribo por el préstamo. A la fecha le falta pagar S/ 420.", mensajeWhatsapp("Rosa Huamán", 42_000))
        assertEquals("Hola Rosa", mensajeWhatsapp("Rosa Huamán", 0))
    }

    @Test fun formato() {
        assertEquals("S/ 1,250", soles(125_000))
        assertEquals("S/ 1,250.50", soles(125_050))
        assertEquals("S/ 0", soles(0))
        assertEquals(125_050L, parseMonto("1250,5"))
        assertNull(parseMonto("1.250"))
        assertNull(parseMonto("abc"))
        assertEquals("12.5", limpiarMonto("12.5.x"))
        assertEquals("12.55", limpiarMonto("12,559"))
        assertEquals("4 oct", fechaCorta(lima(2026, 10, 4), ahora))
        assertEquals("12 set 2025", fechaCorta(lima(2025, 9, 12), ahora))
        assertEquals("987 112 233", celularBonito("987112233"))
        assertEquals("420.50", montoParaCampo(42_050))
    }
}
