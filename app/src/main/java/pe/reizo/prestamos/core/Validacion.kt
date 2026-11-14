package pe.reizo.prestamos.core

// Cada función devuelve el mensaje de error, o null si el dato está bien.

fun errorNombre(nombre: String): String? =
    if (nombre.isBlank() || nombre.trim().length > 60) "Escriba el nombre." else null

fun errorCelular(celular: String): String? =
    if (Regex("9\\d{8}").matches(celular)) null else "El celular tiene 9 dígitos y empieza con 9."

fun errorDni(dni: String): String? =
    if (Regex("\\d{8}").matches(dni)) null else "El DNI tiene 8 dígitos."

fun errorDniRepetido(dni: String, clienteId: String?, clientes: List<Cliente>): String? =
    clientes.firstOrNull { !it.borrado && it.dni == dni && it.id != clienteId }
        ?.let { "Ese DNI ya es de ${it.nombre}. Búsquelo por su nombre." }

fun errorPrestado(monto: Long?): String? =
    if (monto == null || monto <= 0) "Escriba cuánto le prestó." else null

fun errorAPagar(aPagar: Long?, prestado: Long?): String? =
    if (aPagar == null || aPagar <= 0 || (prestado != null && aPagar < prestado)) "Debe ser igual o mayor a lo prestado." else null

fun errorPago(monto: Long?, falta: Long): String? = when {
    monto == null || monto <= 0 -> "Escriba cuánto le pagó."
    monto > falta -> "Es más de lo que debe. Le falta ${soles(falta)}."
    else -> null
}
