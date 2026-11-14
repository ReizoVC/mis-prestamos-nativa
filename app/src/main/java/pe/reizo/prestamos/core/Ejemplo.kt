package pe.reizo.prestamos.core

import java.util.UUID

class DatosEjemplo(val clientes: List<Cliente>, val prestamos: List<Prestamo>, val pagos: List<Pago>)

private const val DIA = 86_400_000L

/**
 * Doce clientes de prueba con fechas relativas a hoy.
 * Los ids salen del DNI, así cargar dos veces no duplica nada.
 */
fun datosEjemplo(ahora: Long): DatosEjemplo {
    val clientes = mutableListOf<Cliente>()
    val prestamos = mutableListOf<Prestamo>()
    val pagos = mutableListOf<Pago>()
    fun id(clave: String) = UUID.nameUUIDFromBytes("ejemplo-$clave".toByteArray()).toString()

    // prestamo = (prestó, paga, hace días) + pagos (monto, hace días)
    class P(val presto: Int, val paga: Int, val hace: Int, vararg val pagos: Pair<Int, Int>)

    fun cliente(nombre: String, celular: String, dni: String, vararg ps: P) {
        val cid = id(dni)
        clientes += Cliente(cid, nombre, celular, dni, ahora - ps.maxOf { it.hace } * DIA, ahora)
        ps.forEachIndexed { i, p ->
            val pid = id("$dni-$i")
            val suyos = p.pagos.mapIndexed { j, (monto, hace) ->
                Pago(id("$dni-$i-$j"), pid, monto * 100L, ahora - hace * DIA, ahora)
            }
            pagos += suyos
            val base = Prestamo(pid, cid, p.presto * 100L, p.paga * 100L, ahora - p.hace * DIA, null, ahora)
            prestamos += base.copy(pagadoEn = pagadoEnSegun(base, suyos))
        }
    }

    cliente("Rosa Huamán", "987112233", "45127893", P(500, 600, 40, 100 to 30, 80 to 22))
    cliente("Julio Quispe", "956443210", "40981254", P(1000, 1200, 43, 200 to 35, 300 to 26, 200 to 8))
    cliente("Pedro Mamani", "912876540", "47823019", P(300, 360, 25))
    cliente("Carmen Flores", "998223401", "42650987", P(200, 240, 12, 50 to 5))
    cliente("Lucía Rojas", "934556712", "46209833", P(150, 180, 9, 30 to 2))
    cliente("Teodoro Ccahuana", "965012347", "41378206", P(800, 960, 60, 160 to 50, 160 to 38))
    cliente("Gladys Paredes", "987650091", "43890125", P(400, 480, 3))
    cliente("Wilmer Torres", "941238870", "48012376", P(250, 300, 18, 100 to 10, 60 to 1))
    cliente("Ana Sulca", "923871104", "44721350", P(300, 360, 120, 120 to 100, 120 to 80, 120 to 62))
    cliente(
        "Mario Inga", "978120035", "40216987",
        P(600, 720, 200, 360 to 170, 360 to 140), P(200, 240, 90, 240 to 60),
    )
    cliente("Martha Chávez", "917402263", "45589102", P(100, 120, 70, 120 to 45))
    cliente("Félix Ramos", "952339871", "47130654", P(500, 600, 30, 300 to 20, 300 to 6))

    return DatosEjemplo(clientes, prestamos, pagos)
}
