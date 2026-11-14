package pe.reizo.prestamos.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import pe.reizo.prestamos.core.Cliente
import pe.reizo.prestamos.core.Pago
import pe.reizo.prestamos.core.Prestamo

@Dao
interface Tablas {
    // Lo que miran las pantallas (se actualiza solo con cada cambio)
    @Query("SELECT * FROM clientes") fun verClientes(): Flow<List<Cliente>>
    @Query("SELECT * FROM prestamos") fun verPrestamos(): Flow<List<Prestamo>>
    @Query("SELECT * FROM pagos") fun verPagos(): Flow<List<Pago>>

    @Query(
        "SELECT (SELECT COUNT(*) FROM clientes WHERE pendiente = 1) + " +
            "(SELECT COUNT(*) FROM prestamos WHERE pendiente = 1) + " +
            "(SELECT COUNT(*) FROM pagos WHERE pendiente = 1)",
    )
    fun verPendientes(): Flow<Int>

    @Query("SELECT * FROM clientes") suspend fun clientes(): List<Cliente>
    @Query("SELECT * FROM prestamos") suspend fun prestamos(): List<Prestamo>
    @Query("SELECT * FROM pagos") suspend fun pagos(): List<Pago>

    @Query("SELECT * FROM clientes WHERE id = :id") suspend fun cliente(id: String): Cliente?
    @Query("SELECT * FROM prestamos WHERE id = :id") suspend fun prestamo(id: String): Prestamo?
    @Query("SELECT * FROM pagos WHERE id = :id") suspend fun pago(id: String): Pago?
    @Query("SELECT * FROM pagos WHERE prestamoId = :prestamoId AND borrado = 0")
    suspend fun pagosVigentes(prestamoId: String): List<Pago>

    @Query("SELECT * FROM prestamos WHERE clienteId = :clienteId AND borrado = 0")
    suspend fun prestamosVigentes(clienteId: String): List<Prestamo>

    // Incluyen las filas borradas (para recuperar a un cliente eliminado)
    @Query("SELECT * FROM prestamos WHERE clienteId = :clienteId") suspend fun prestamosDe(clienteId: String): List<Prestamo>
    @Query("SELECT * FROM pagos WHERE prestamoId = :prestamoId") suspend fun pagosDe(prestamoId: String): List<Pago>

    @Upsert suspend fun guardar(c: Cliente)
    @Upsert suspend fun guardar(p: Prestamo)
    @Upsert suspend fun guardar(p: Pago)

    // Sincronización
    @Query("SELECT * FROM clientes WHERE pendiente = 1") suspend fun clientesPendientes(): List<Cliente>
    @Query("SELECT * FROM prestamos WHERE pendiente = 1") suspend fun prestamosPendientes(): List<Prestamo>
    @Query("SELECT * FROM pagos WHERE pendiente = 1") suspend fun pagosPendientes(): List<Pago>

    // Solo se marca como subida si no cambió mientras subía
    @Query("UPDATE clientes SET pendiente = 0 WHERE id = :id AND actualizadoEn = :actualizadoEn")
    suspend fun clienteSubido(id: String, actualizadoEn: Long)
    @Query("UPDATE prestamos SET pendiente = 0 WHERE id = :id AND actualizadoEn = :actualizadoEn")
    suspend fun prestamoSubido(id: String, actualizadoEn: Long)
    @Query("UPDATE pagos SET pendiente = 0 WHERE id = :id AND actualizadoEn = :actualizadoEn")
    suspend fun pagoSubido(id: String, actualizadoEn: Long)
}

// Para cambiar el esquema: subir `version` y agregar una Migration. Nunca borrar la base para migrar.
@Database(entities = [Cliente::class, Prestamo::class, Pago::class], version = 2, exportSchema = false)
abstract class BaseDatos : RoomDatabase() {
    abstract fun tablas(): Tablas

    companion object {
        /** Con `enMemoria` nada se escribe en el celular: la base desaparece al cerrar la app. */
        fun abrir(ctx: Context, enMemoria: Boolean): BaseDatos =
            if (enMemoria) Room.inMemoryDatabaseBuilder(ctx.applicationContext, BaseDatos::class.java).build()
            else Room.databaseBuilder(ctx.applicationContext, BaseDatos::class.java, "prestamos.db")
                .addMigrations(V1_A_V2)
                .build()

        /** Versión 2: nota en cada préstamo. */
        private val V1_A_V2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE prestamos ADD COLUMN nota TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
