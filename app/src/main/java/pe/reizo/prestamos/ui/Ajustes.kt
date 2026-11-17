@file:OptIn(ExperimentalMaterial3Api::class)

package pe.reizo.prestamos.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.reizo.prestamos.BuildConfig
import pe.reizo.prestamos.core.Libro
import pe.reizo.prestamos.core.OPCIONES_DIAS_SIN_PAGAR
import pe.reizo.prestamos.core.dia
import pe.reizo.prestamos.core.fechaCorta
import pe.reizo.prestamos.data.EstadoNube

private fun haceRato(desde: Long, ahora: Long): String {
    val min = ((ahora - desde) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "hace un momento"
        min == 1L -> "hace 1 minuto"
        min < 60 -> "hace $min minutos"
        min < 120 -> "hace 1 hora"
        min < 1440 -> "hace ${min / 60} horas"
        else -> "hace ${min / 1440} días"
    }
}

@Composable
fun Ajustes(vm: AppVM, libro: Libro, avisos: SnackbarHostState) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    val estado by vm.nube.estado.collectAsStateWithLifecycle()
    val ultima by vm.nube.ultima.collectAsStateWithLifecycle()
    val pendientes by vm.pendientes.collectAsStateWithLifecycle()
    var recuperando by remember { mutableStateOf<String?>(null) }
    var cerrando by remember { mutableStateOf(false) }
    var sincronizando by remember { mutableStateOf(false) }

    val guardarArchivo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { destino ->
        if (destino != null) ambito.launch {
            val bien = runCatching {
                val texto = vm.repo.respaldo(BuildConfig.VERSION_NAME)
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(destino)!!.use { it.write(texto.toByteArray()) } }
            }.isSuccess
            vm.avisar(if (bien) "Respaldo guardado" else "No se pudo guardar el respaldo")
        }
    }
    val abrirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { origen ->
        if (origen != null) ambito.launch {
            recuperando = runCatching {
                withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(origen)!!.use { it.readBytes().decodeToString() } }
            }.getOrNull()
            if (recuperando == null) vm.avisar("No se pudo leer ese archivo")
        }
    }

    // Qué opción se está eligiendo en la hoja de abajo
    var eligiendo by remember { mutableStateOf<Eleccion?>(null) }
    val dias by vm.diasSinPagarElegidos.collectAsStateWithLifecycle()
    val c = MaterialTheme.colorScheme

    Scaffold(
        topBar = { BarraInterna("Ajustes", vm::volver) },
        snackbarHost = { SnackbarHost(avisos) },
    ) { margen ->
        Box(Modifier.padding(margen).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (BuildConfig.MODO_DESARROLLO) {
                    Tarjeta(color = c.tertiaryContainer) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Modo de prueba", style = MaterialTheme.typography.titleMedium)
                            Text("Nada de lo que haga aquí se guarda. Al cerrar la app vuelven los clientes de ejemplo.")
                            FilledTonalButton(onClick = vm::reiniciarEjemplo, modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_BOTON)) {
                                Text("Volver a los datos de ejemplo")
                            }
                        }
                    }
                }

                // Cada grupo es un bloque redondeado; cada fila dice su valor actual y al tocarla se cambia
                Grupo("Apariencia", listOf(
                    {
                        FilaAjuste(Icons.Default.Palette, c.tertiaryContainer, c.onTertiaryContainer, "Tema", vm.tema.etiqueta) {
                            eligiendo = Eleccion.TEMA
                        }
                    },
                    {
                        FilaAjuste(Icons.Default.FormatSize, c.tertiaryContainer, c.onTertiaryContainer, "Tamaño de letra", vm.letra.etiqueta) {
                            eligiendo = Eleccion.LETRA
                        }
                    },
                ))

                Grupo("Cobranza", listOf {
                    FilaAjuste(
                        Icons.Default.Schedule, c.errorContainer, c.onErrorContainer,
                        "Días para \"Sin pagar\"", "$dias días",
                    ) { eligiendo = Eleccion.DIAS }
                })

                Grupo("Seguridad", listOf {
                    FilaAjuste(
                        Icons.Default.Fingerprint, c.primaryContainer, c.onPrimaryContainer,
                        "Pedir huella o clave al abrir",
                        if (vm.puedeBloquear) "Usa el mismo bloqueo de este celular."
                        else "Este celular no tiene bloqueo de pantalla. Actívelo en los ajustes del celular para proteger la app.",
                        final = { Switch(checked = vm.bloqueoActivo, onCheckedChange = null, enabled = vm.puedeBloquear) },
                        alTocar = if (vm.puedeBloquear) ({ vm.cambiarBloqueo(!vm.bloqueoPedido) }) else null,
                    )
                })

                val n = libro.eliminados.size
                Grupo("Sus datos", buildList {
                    if (!BuildConfig.MODO_DESARROLLO) add {
                        val cambios = if (pendientes == 1) "Hay 1 cambio esperando" else "Hay $pendientes cambios esperando"
                        val texto = when {
                            estado == EstadoNube.SOLO_LOCAL -> "Modo solo en este celular (nube no configurada)"
                            sincronizando -> "Sincronizando…"
                            estado == EstadoNube.SIN_INTERNET && pendientes > 0 -> "Sin internet. $cambios para subirse."
                            estado == EstadoNube.SIN_INTERNET -> "Sin internet. No hay cambios esperando para subirse."
                            estado == EstadoNube.ERROR -> "No se pudo guardar en la nube. Se intentará de nuevo."
                            pendientes > 0 -> "$cambios para subirse."
                            ultima > 0 -> "Todo guardado · ${haceRato(ultima, vm.ahora)}"
                            else -> "Todo guardado"
                        }
                        val bien = estado == EstadoNube.OK && pendientes == 0
                        FilaAjuste(
                            when {
                                estado == EstadoNube.SOLO_LOCAL -> Icons.Default.PhoneAndroid
                                bien -> Icons.Default.CloudDone
                                else -> Icons.Default.CloudOff
                            },
                            c.secondaryContainer, c.onSecondaryContainer,
                            "Nube", texto,
                            alTocar = if (vm.nube.configurada && !sincronizando) ({
                                sincronizando = true
                                ambito.launch { vm.nube.sincronizar(); sincronizando = false }
                            }) else null,
                        )
                    }
                    add {
                        FilaAjuste(Icons.Default.Save, c.secondaryContainer, c.onSecondaryContainer, "Guardar respaldo",
                            "Crea un archivo con todos los datos. Puede guardarlo en Drive.") {
                            guardarArchivo.launch("respaldo-prestamos-${dia(System.currentTimeMillis())}.json")
                        }
                    }
                    add {
                        FilaAjuste(Icons.Default.Restore, c.secondaryContainer, c.onSecondaryContainer, "Recuperar un respaldo",
                            "Trae los datos de un archivo de respaldo. No borra lo que ya hay.") {
                            abrirArchivo.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                        }
                    }
                    add {
                        FilaAjuste(
                            Icons.Default.DeleteOutline, c.secondaryContainer, c.onSecondaryContainer, "Clientes eliminados",
                            when (n) {
                                0 -> "No hay ninguno."
                                1 -> "Hay 1 para recuperar."
                                else -> "Hay $n para recuperar."
                            },
                            alTocar = if (n > 0) ({ vm.abrir(Pantalla.Papelera) }) else null,
                        )
                    }
                })

                Grupo("Aplicación", buildList {
                    add {
                        FilaAjuste(
                            Icons.Default.SystemUpdate, c.surfaceContainerHighest, c.onSurface, "Versión ${BuildConfig.VERSION_NAME}",
                            if (vm.novedad != null) "Hay una versión nueva: ${vm.novedad?.version}. Vuelva a Clientes para actualizar."
                            else "Toque para buscar una versión nueva.",
                        ) { vm.buscarActualizacion(aMano = true) }
                    }
                    if (vm.nube.configurada) add {
                        FilaAjuste(Icons.AutoMirrored.Filled.Logout, c.surfaceContainerHighest, c.onSurface, "Cerrar sesión", null) {
                            if (pendientes > 0) vm.avisar("Primero conéctese a internet para subir los cambios.") else cerrando = true
                        }
                    }
                })
            }
        }
    }

    when (eligiendo) {
        Eleccion.TEMA -> HojaEleccion(
            "Tema", TemaApp.entries.map { it to it.etiqueta }, vm.tema,
            alElegir = { vm.cambiarTema(it); eligiendo = null }, alCerrar = { eligiendo = null },
        )
        Eleccion.LETRA -> HojaEleccion(
            "Tamaño de letra", Letra.entries.map { it to it.etiqueta }, vm.letra,
            alElegir = { vm.cambiarLetra(it); eligiendo = null }, alCerrar = { eligiendo = null },
        )
        Eleccion.DIAS -> HojaDias(dias, alElegir = { vm.cambiarDiasSinPagar(it); eligiendo = null }, alCerrar = { eligiendo = null })
        null -> {}
    }

    recuperando?.let { texto ->
        AlertDialog(
            onDismissRequest = { recuperando = null },
            title = { Text("¿Recuperar este respaldo?") },
            text = { Text("Se agregan los datos del archivo a los de este celular. No se borra nada.") },
            confirmButton = {
                TextButton(onClick = {
                    recuperando = null
                    ambito.launch {
                        val n = runCatching { vm.repo.recuperar(texto) }.getOrNull()
                        vm.avisar(if (n != null) "Respaldo recuperado: $n clientes" else "Ese archivo no es un respaldo de Mis Préstamos")
                    }
                }) { Text("Sí, recuperar") }
            },
            dismissButton = { TextButton(onClick = { recuperando = null }) { Text("No") } },
        )
    }

    if (cerrando) {
        AlertDialog(
            onDismissRequest = { cerrando = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Se cerrará la sesión en este celular. Sus datos siguen guardados en la nube.") },
            confirmButton = { TextButton(onClick = { cerrando = false; vm.nube.cerrarSesion() }) { Text("Sí, cerrar") } },
            dismissButton = { TextButton(onClick = { cerrando = false }) { Text("No") } },
        )
    }
}

private enum class Eleccion { TEMA, LETRA, DIAS }

/** Título del grupo y sus filas pegadas en un bloque redondeado, como los Ajustes de Android. */
@Composable
private fun Grupo(titulo: String, filas: List<@Composable () -> Unit>) {
    Text(
        titulo,
        Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        filas.forEachIndexed { i, fila ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = formaEnGrupo(i, filas.size)) { fila() }
        }
    }
}

/** Una fila de ajuste: ícono en un círculo de color, título, valor actual y, si se puede tocar, una flecha. */
@Composable
private fun FilaAjuste(
    icono: ImageVector,
    fondoIcono: Color,
    colorIcono: Color,
    titulo: String,
    detalle: String?,
    final: (@Composable () -> Unit)? = null,
    // al final para que la llave {} de quien llama sea la acción al tocar, no contenido de la fila
    alTocar: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (alTocar != null) Modifier.clickable(onClick = alTocar) else Modifier)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(fondoIcono), contentAlignment = Alignment.Center) {
            Icon(icono, null, Modifier.size(24.dp), tint = colorIcono)
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            if (detalle != null) Text(detalle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            final != null -> final()
            alTocar != null -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Hoja desde abajo con opciones grandes; al tocar una se aplica al instante. */
@Composable
private fun <T> HojaEleccion(titulo: String, opciones: List<Pair<T, String>>, elegido: T, alElegir: (T) -> Unit, alCerrar: () -> Unit) {
    ModalBottomSheet(onDismissRequest = alCerrar) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(titulo, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.headlineSmall)
            opciones.forEach { (valor, texto) -> FilaRadio(texto, valor == elegido) { alElegir(valor) } }
        }
    }
}

@Composable
private fun FilaRadio(texto: String, elegida: Boolean, alTocar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(elegida, onClick = alTocar, role = Role.RadioButton)
            .heightIn(min = 60.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = elegida, onClick = null)
        Text(texto, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Días para pasar a "Sin pagar": las tres opciones comunes o el número que ella quiera. */
@Composable
private fun HojaDias(actual: Int, alElegir: (Int) -> Unit, alCerrar: () -> Unit) {
    val esOtro = actual !in OPCIONES_DIAS_SIN_PAGAR
    var otro by remember { mutableStateOf(esOtro) }
    var texto by remember { mutableStateOf(if (esOtro) actual.toString() else "") }
    var error by remember { mutableStateOf<String?>(null) }
    val foco = remember { FocusRequester() }

    fun guardarOtro() {
        val n = texto.toIntOrNull()
        if (n == null || n !in 1..365) error = "Escriba un número de días entre 1 y 365." else alElegir(n)
    }

    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 24.dp).imePadding()) {
            Text("Días para \"Sin pagar\"", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.headlineSmall)
            Text(
                "Si un cliente no paga en este tiempo, sube al grupo rojo de Clientes.",
                Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OPCIONES_DIAS_SIN_PAGAR.forEach { d -> FilaRadio("$d días", !otro && d == actual) { alElegir(d) } }
            FilaRadio("Otro número de días", otro) { otro = true }
            if (otro) {
                LaunchedEffect(Unit) { foco.requestFocus() }
                Row(
                    Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it.filter(Char::isDigit).take(3); error = null },
                        label = { Text("Días") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { guardarOtro() }),
                        modifier = Modifier.weight(1f).focusRequester(foco),
                    )
                    Button(onClick = ::guardarOtro, modifier = Modifier.padding(top = 8.dp).heightIn(min = ALTO_BOTON)) { Text("Guardar") }
                }
            }
        }
    }
}

/** Clientes eliminados. Se pueden recuperar con sus préstamos y pagos. */
@Composable
fun Papelera(vm: AppVM, libro: Libro, avisos: SnackbarHostState) {
    val eliminados = libro.eliminados
    Scaffold(
        topBar = { BarraInterna("Clientes eliminados", vm::volver) },
        snackbarHost = { SnackbarHost(avisos) },
    ) { margen ->
        Box(Modifier.padding(margen).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (eliminados.isEmpty()) Vacio("No hay clientes eliminados.")
                eliminados.forEachIndexed { i, c ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = formaEnGrupo(i, eliminados.size)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.nombre, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Se eliminó el ${fechaCorta(c.actualizadoEn, vm.ahora)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalButton(onClick = { vm.recuperarCliente(c) }, modifier = Modifier.heightIn(min = ALTO_BOTON)) { Text("Recuperar") }
                        }
                    }
                }
            }
        }
    }
}
