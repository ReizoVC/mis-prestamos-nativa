@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package pe.reizo.prestamos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.reizo.prestamos.core.Filtro
import pe.reizo.prestamos.core.Libro
import pe.reizo.prestamos.core.fechaCorta
import pe.reizo.prestamos.core.soles

// Espacio al final de las listas para que el botón "Nuevo préstamo" no tape la última fila
private val HUECO_BOTON = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp)

// Formas de ordenar a los que están al día. Los atrasados siempre van arriba, del más atrasado al menos.
private val ORDENES = listOf(
    Filtro.TODOS to "Por nombre",
    Filtro.DEBEN_MAS to "Los que deben más",
    Filtro.NUEVOS to "Los más nuevos",
)

@Composable
fun ListaClientes(vm: AppVM, libro: Libro) {
    val pendientes by vm.pendientes.collectAsStateWithLifecycle()
    val orden = if (ORDENES.any { it.first == vm.filtro }) vm.filtro else Filtro.TODOS
    val todos = libro.clientesActivos(vm.busqueda, orden, vm.ahora)
    val atrasados = todos.filter { libro.estaSinPagar(it.id, vm.ahora) }.sortedByDescending { libro.diasSinPagar(it.id, vm.ahora) }
    val alDia = todos.filter { !libro.estaSinPagar(it.id, vm.ahora) }
    val c = MaterialTheme.colorScheme

    Column {
        CampoBusqueda(vm.busqueda, { vm.busqueda = it }, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp))
        if (vm.nube.configurada && pendientes > 20) {
            Surface(
                color = c.tertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, null)
                    Text("Hay cambios sin guardar en la nube. Conéctese a internet.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        when {
            todos.isNotEmpty() -> LazyColumn(contentPadding = HUECO_BOTON, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Primero los que hay que cobrar: un grupo aparte, con su título y cuántos son
                if (atrasados.isNotEmpty()) {
                    item(key = "titulo-atrasados") {
                        TituloGrupo("Sin pagar hace ${libro.diasSinPagar} días o más (${atrasados.size})", alerta = true)
                    }
                    itemsIndexed(atrasados, key = { _, cl -> cl.id }) { i, cl ->
                        FilaCliente(
                            nombre = cl.nombre,
                            detalle = "Hace ${libro.diasSinPagar(cl.id, vm.ahora)} días",
                            derecha = soles(libro.deuda(cl.id)),
                            alerta = true,
                            posicion = i, total = atrasados.size,
                            alTocar = { vm.abrir(Pantalla.Ficha(cl.id)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (alDia.isNotEmpty()) {
                    item(key = "titulo-al-dia") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TituloGrupo("Al día (${alDia.size})", alerta = false, modifier = Modifier.weight(1f))
                            Box {
                                var abierto by remember { mutableStateOf(false) }
                                TextButton(onClick = { abierto = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text("Ordenar")
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                                    ORDENES.forEach { (f, texto) ->
                                        DropdownMenuItem(
                                            text = { Text(texto, style = MaterialTheme.typography.bodyLarge) },
                                            onClick = { vm.filtro = f; abierto = false },
                                            leadingIcon = {
                                                if (orden == f) Icon(Icons.Default.Check, contentDescription = "Elegido") else Spacer(Modifier.size(24.dp))
                                            },
                                            modifier = Modifier.heightIn(min = 56.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(alDia, key = { _, cl -> cl.id }) { i, cl ->
                        FilaCliente(
                            nombre = cl.nombre,
                            detalle = libro.estado(cl.id, vm.ahora),
                            derecha = soles(libro.deuda(cl.id)),
                            alerta = false,
                            posicion = i, total = alDia.size,
                            alTocar = { vm.abrir(Pantalla.Ficha(cl.id)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            !libro.hayActivos() -> Vacio("Todavía no hay préstamos. Toque Nuevo préstamo para registrar el primero.")
            else -> Vacio("No hay clientes con ese nombre.")
        }
    }
}

@Composable
private fun TituloGrupo(texto: String, alerta: Boolean, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier = modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = if (alerta) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ListaAntiguos(vm: AppVM, libro: Libro) {
    val clientes = libro.clientesAntiguos(vm.busquedaAntiguos)
    Column {
        CampoBusqueda(vm.busquedaAntiguos, { vm.busquedaAntiguos = it }, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        when {
            clientes.isNotEmpty() -> LazyColumn(contentPadding = HUECO_BOTON, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(clientes, key = { _, c -> c.id }) { i, c ->
                    val n = libro.prestamosDe(c.id).size
                    val termino = libro.terminoEn(c.id)?.let { fechaCorta(it, vm.ahora) } ?: ""
                    // Aquí todos ya pagaron: no hace falta repetir "Pagado" en cada fila
                    FilaCliente(
                        nombre = c.nombre,
                        detalle = "$n ${if (n == 1) "préstamo" else "préstamos"} · terminó el $termino",
                        derecha = null,
                        alerta = false,
                        posicion = i, total = clientes.size,
                        alTocar = { vm.abrir(Pantalla.Ficha(c.id)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            vm.busquedaAntiguos.isNotBlank() -> Vacio("No hay clientes con ese nombre.")
            else -> Vacio("Aquí aparecen los clientes que ya terminaron de pagar.")
        }
    }
}

/** Fila de una lista agrupada: las filas forman un solo bloque, no una tarjeta cada una. */
@Composable
private fun FilaCliente(
    nombre: String,
    detalle: String,
    derecha: String?,
    alerta: Boolean,
    posicion: Int,
    total: Int,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val forma = formaEnGrupo(posicion, total)
    Surface(
        color = if (alerta) c.errorContainer else c.surfaceContainerLowest,
        contentColor = if (alerta) c.onErrorContainer else c.onSurface,
        shape = forma,
        modifier = modifier.fillMaxWidth().clip(forma).clickable(onClick = alTocar),
    ) {
        Row(
            Modifier.heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    detalle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (alerta) FontWeight.Bold else FontWeight.Normal,
                    color = if (alerta) c.onErrorContainer else c.onSurfaceVariant,
                )
            }
            if (derecha != null) Text(derecha, style = MaterialTheme.typography.titleLarge)
        }
    }
}
