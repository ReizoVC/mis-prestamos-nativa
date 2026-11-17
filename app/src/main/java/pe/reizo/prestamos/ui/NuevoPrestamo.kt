@file:OptIn(ExperimentalMaterial3Api::class)

package pe.reizo.prestamos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pe.reizo.prestamos.core.Libro
import pe.reizo.prestamos.core.errorAPagar
import pe.reizo.prestamos.core.errorCelular
import pe.reizo.prestamos.core.errorDni
import pe.reizo.prestamos.core.errorDniRepetido
import pe.reizo.prestamos.core.errorNombre
import pe.reizo.prestamos.core.errorPrestado
import pe.reizo.prestamos.core.parseMonto
import pe.reizo.prestamos.core.soles
import kotlin.math.roundToInt

@Composable
fun NuevoPrestamo(vm: AppVM, libro: Libro, clienteInicial: String?, avisos: SnackbarHostState) {
    val inicial = remember { clienteInicial?.let(libro::cliente) }
    var elegidoId by rememberSaveable { mutableStateOf(inicial?.id) }
    var nombre by rememberSaveable { mutableStateOf(inicial?.nombre ?: "") }
    var celular by rememberSaveable { mutableStateOf(inicial?.celular ?: "") }
    var dni by rememberSaveable { mutableStateOf(inicial?.dni ?: "") }
    var prestado by rememberSaveable { mutableStateOf("") }
    var aPagar by rememberSaveable { mutableStateOf("") }
    var nota by rememberSaveable { mutableStateOf("") }
    var errores by remember { mutableStateOf(List<String?>(5) { null }) }
    var guardando by remember { mutableStateOf(false) }
    val focos = remember { List(5) { FocusRequester() } }
    val ambito = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme

    val elegido = elegidoId?.let(libro::cliente)
    val sugerencias = if (elegido == null) libro.sugerencias(nombre) else emptyList()
    fun sinError(i: Int) { if (errores[i] != null) errores = errores.toMutableList().also { it[i] = null } }

    fun guardar() {
        val mp = parseMonto(prestado)
        val ma = parseMonto(aPagar)
        val nuevos = listOf(
            errorNombre(nombre),
            errorCelular(celular),
            errorDni(dni) ?: errorDniRepetido(dni, elegido?.id, libro.clientes),
            errorPrestado(mp),
            errorAPagar(ma, mp),
        )
        errores = nuevos
        val primero = nuevos.indexOfFirst { it != null }
        if (primero >= 0) {
            runCatching { focos[primero].requestFocus() }
            return
        }
        guardando = true
        ambito.launch {
            val id = vm.repo.crearPrestamo(elegido?.id, nombre, celular, dni, mp!!, ma!!, nota)
            vm.avisar("Préstamo de ${soles(mp)} guardado")
            vm.irAFicha(id)
        }
    }

    Scaffold(
        topBar = { BarraInterna("Nuevo préstamo", vm::volver) },
        snackbarHost = { SnackbarHost(avisos) },
    ) { margen ->
        Box(Modifier.padding(margen).fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it.take(60); sinError(0) },
                    label = { Text("Nombre") },
                    placeholder = { Text("Escriba el nombre") },
                    readOnly = elegido != null,
                    singleLine = true,
                    isError = errores[0] != null,
                    supportingText = errores[0]?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().focusRequester(focos[0]),
                )

                // Clientes que ya existen y coinciden con lo escrito
                if (sugerencias.isNotEmpty()) {
                    Tarjeta(color = c.surfaceContainerHigh) {
                        sugerencias.forEach { s ->
                            val deuda = libro.deuda(s.id)
                            ListItem(
                                headlineContent = { Text(s.nombre, style = MaterialTheme.typography.titleSmall) },
                                supportingContent = {
                                    Text("DNI ${s.dni} · " + if (deuda > 0) "debe ${soles(deuda)}" else "ya pagó todo", style = MaterialTheme.typography.bodyMedium)
                                },
                                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                                modifier = Modifier.clickable {
                                    elegidoId = s.id; nombre = s.nombre; celular = s.celular; dni = s.dni
                                    errores = List(5) { null }
                                },
                            )
                        }
                    }
                }

                if (elegido != null) {
                    Surface(color = c.primaryContainer, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Cliente que ya existe", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            TextButton(
                                onClick = { elegidoId = null; nombre = ""; celular = ""; dni = ""; errores = List(5) { null } },
                                modifier = Modifier.heightIn(min = ALTO_BOTON),
                            ) { Text("Cambiar") }
                        }
                    }
                    val deuda = libro.deuda(elegido.id)
                    if (deuda > 0) {
                        Surface(color = c.errorContainer, shape = MaterialTheme.shapes.medium) {
                            Text(
                                "Todavía le debe ${soles(deuda)}. El nuevo préstamo se lleva aparte.",
                                Modifier.fillMaxWidth().padding(16.dp),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                    val anteriores = libro.prestamosDe(elegido.id)
                    if (anteriores.isNotEmpty()) {
                        var abierto by rememberSaveable { mutableStateOf(false) }
                        Tarjeta(color = c.surfaceContainerLowest) {
                            Row(
                                Modifier.fillMaxWidth().clickable { abierto = !abierto }.heightIn(min = 60.dp).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Préstamos anteriores (${anteriores.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Icon(if (abierto) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (abierto) "Cerrar" else "Abrir")
                            }
                            AnimatedVisibility(abierto) {
                                Column {
                                    HorizontalDivider(color = c.surfaceContainerHigh)
                                    ListaPrestamos(libro, anteriores, vm.ahora)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = celular,
                    onValueChange = { celular = it.filter(Char::isDigit).take(9); sinError(1) },
                    label = { Text("Celular") },
                    placeholder = { Text("9 dígitos") },
                    singleLine = true,
                    isError = errores[1] != null,
                    supportingText = errores[1]?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().focusRequester(focos[1]),
                )
                OutlinedTextField(
                    value = dni,
                    onValueChange = { dni = it.filter(Char::isDigit).take(8); sinError(2) },
                    label = { Text("DNI") },
                    placeholder = { Text("8 dígitos") },
                    singleLine = true,
                    isError = errores[2] != null,
                    supportingText = errores[2]?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().focusRequester(focos[2]),
                )
                CampoMonto(prestado, { prestado = it; sinError(3) }, "¿Cuánto le prestó?", Modifier.focusRequester(focos[3]), error = errores[3])
                CampoMonto(aPagar, { aPagar = it; sinError(4) }, "¿Cuánto le va a pagar?", Modifier.focusRequester(focos[4]), error = errores[4])
                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it.take(120) },
                    label = { Text("Nota (si quiere)") },
                    placeholder = { Text("Ej.: paga los sábados") },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )

                val mp = parseMonto(prestado)
                val ma = parseMonto(aPagar)
                Surface(color = c.primaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text(
                        if (mp != null && ma != null && mp > 0 && ma >= mp) "Gana ${soles(ma - mp)} (${((ma - mp) * 100.0 / mp).roundToInt()}%)"
                        else "Escriba los montos para ver cuánto gana",
                        Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Button(onClick = ::guardar, enabled = !guardando, modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp)) {
                    Text("Guardar préstamo", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
