package pe.reizo.prestamos.core

/** Compara versiones tipo "1.2.10". True si `remota` es mayor que `local`. */
fun esVersionMasNueva(remota: String, local: String): Boolean {
    fun partes(v: String) = v.trim().trimStart('v', 'V').split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = partes(remota)
    val b = partes(local)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
