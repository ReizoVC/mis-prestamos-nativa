@file:OptIn(ExperimentalMaterial3Api::class)

package pe.reizo.prestamos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.reizo.prestamos.core.Libro
import pe.reizo.prestamos.core.Prestamo
import pe.reizo.prestamos.core.fechaCorta
import pe.reizo.prestamos.core.limpiarMonto
import pe.reizo.prestamos.core.soles

val ALTO_BOTON = 56.dp

/**
 * Forma de una fila dentro de un grupo (como las listas de Ajustes de Android):
 * esquinas grandes en los extremos del grupo y pequeñas entre filas vecinas.
 */
fun formaEnGrupo(posicion: Int, total: Int): RoundedCornerShape {
    val arriba = if (posicion == 0) 20.dp else 4.dp
    val abajo = if (posicion == total - 1) 20.dp else 4.dp
    return RoundedCornerShape(topStart = arriba, topEnd = arriba, bottomStart = abajo, bottomEnd = abajo)
}

@Composable
fun BarraInterna(titulo: String, volver: () -> Unit, acciones: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(titulo, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = volver, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
        },
        actions = acciones,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun CampoBusqueda(valor: String, alCambiar: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = valor,
        onValueChange = alCambiar,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Buscar por nombre o DNI") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (valor.isNotEmpty()) IconButton(onClick = { alCambiar("") }) {
                Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    )
}

/** Campo de monto con "S/" delante. Acepta coma o punto. */
@Composable
fun CampoMonto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    ultimo: Boolean = false,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { alCambiar(limpiarMonto(it)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(etiqueta) },
        prefix = { Text("S/ ", style = MaterialTheme.typography.headlineSmall) },
        textStyle = MaterialTheme.typography.headlineSmall,
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = if (ultimo) ImeAction.Done else ImeAction.Next,
        ),
    )
}

/** Píldora de estado: siempre con texto, el color nunca va solo. */
@Composable
fun Etiqueta(texto: String, bien: Boolean) {
    val c = MaterialTheme.colorScheme
    Surface(
        color = if (bien) c.primaryContainer else c.errorContainer,
        contentColor = if (bien) c.onPrimaryContainer else c.onErrorContainer,
        shape = CircleShape,
    ) {
        Text(texto, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Vacio(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color, contentColor = contentColorFor(color)),
        content = contenido,
    )
}

@Composable
fun FilaDato(izquierda: String, derecha: String, modifier: Modifier = Modifier, colorDerecha: Color = Color.Unspecified, negrita: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(izquierda, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            derecha,
            style = MaterialTheme.typography.titleSmall,
            color = colorDerecha,
            fontWeight = if (negrita) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

/** Varios préstamos plegables en un solo bloque, separados por líneas. */
@Composable
fun ListaPrestamos(libro: Libro, prestamos: List<Prestamo>, ahora: Long) {
    Column {
        prestamos.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            PrestamoPlegable(libro, p, ahora)
        }
    }
}

/** Un préstamo plegado ("S/ 500 el 4 oct · Pagado") que al tocarlo muestra su historia y sus pagos. */
@Composable
fun PrestamoPlegable(libro: Libro, p: Prestamo, ahora: Long) {
    var abierto by rememberSaveable(p.id) { mutableStateOf(false) }
    val falta = libro.falta(p)
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { abierto = !abierto }.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${soles(p.montoPrestado)} el ${fechaCorta(p.creadoEn, ahora)}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (p.pagadoEn != null) Etiqueta("Pagado", true) else Etiqueta("Debe ${soles(falta)}", false)
            Icon(
                if (abierto) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (abierto) "Cerrar" else "Abrir",
            )
        }
        AnimatedVisibility(abierto) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                val historia = "Prestó ${soles(p.montoPrestado)}, " +
                    (p.pagadoEn?.let { "pagaba ${soles(p.montoAPagar)}. Terminó el ${fechaCorta(it, ahora)}." }
                        ?: "paga ${soles(p.montoAPagar)}.")
                Text(historia, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.nota.isNotBlank()) Text("Nota: ${p.nota}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                val pagos = libro.pagosDe(p.id)
                if (pagos.isEmpty()) Text("Todavía no hizo pagos.", style = MaterialTheme.typography.bodyMedium)
                pagos.forEach { FilaDato(fechaCorta(it.fecha, ahora), soles(it.monto)) }
            }
        }
    }
}
