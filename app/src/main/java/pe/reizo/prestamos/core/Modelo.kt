package pe.reizo.prestamos.core

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Los montos se guardan en céntimos (Long) para que las sumas sean exactas.
// Las fechas son milisegundos desde 1970. Nada se borra de verdad: borrar es borrado = true.
// `pendiente` es solo local: true si falta subir la fila a la nube.

@Entity(tableName = "clientes")
data class Cliente(
    @PrimaryKey val id: String,
    val nombre: String,
    val celular: String,
    val dni: String,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val borrado: Boolean = false,
    val pendiente: Boolean = true,
)

@Entity(tableName = "prestamos", indices = [Index("clienteId")])
data class Prestamo(
    @PrimaryKey val id: String,
    val clienteId: String,
    val montoPrestado: Long,
    val montoAPagar: Long,
    val creadoEn: Long,
    val pagadoEn: Long?,
    val actualizadoEn: Long,
    val borrado: Boolean = false,
    val pendiente: Boolean = true,
)

@Entity(tableName = "pagos", indices = [Index("prestamoId")])
data class Pago(
    @PrimaryKey val id: String,
    val prestamoId: String,
    val monto: Long,
    val fecha: Long,
    val actualizadoEn: Long,
    val borrado: Boolean = false,
    val pendiente: Boolean = true,
)
