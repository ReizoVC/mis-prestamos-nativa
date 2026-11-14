@file:OptIn(ExperimentalMaterial3Api::class)

package pe.reizo.prestamos.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = { BarraInterna("Ajustes", vm::volver) },
        snackbarHost = { SnackbarHost(avisos) },
    ) { margen ->
        Box(Modifier.padding(margen).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (BuildConfig.MODO_DESARROLLO) {
                    Tarjeta(color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Fila(
                            Icons.Default.Science, "Modo de prueba",
                            "Nada de lo que haga aquí se guarda. Al cerrar la app vuelven los clientes de ejemplo.",
                        )
                        FilledTonalButton(
                            onClick = vm::reiniciarEjemplo,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).height(ALTO_BOTON),
                        ) { Text("Volver a los datos de ejemplo") }
                    }
                }

                // ---- nube
                if (!BuildConfig.MODO_DESARROLLO) Tarjeta {
                    val cambios = if (pendientes == 1) "Hay 1 cambio esperando" else "Hay $pendientes cambios esperando"
                    val texto = when {
                        estado == EstadoNube.SOLO_LOCAL -> "Modo solo en este celular (nube no configurada)"
                        estado == EstadoNube.SIN_INTERNET && pendientes > 0 -> "Sin internet. $cambios para subirse."
                        estado == EstadoNube.SIN_INTERNET -> "Sin internet. No hay cambios esperando para subirse."
                        estado == EstadoNube.ERROR -> "No se pudo guardar en la nube. Se intentará de nuevo."
                        pendientes > 0 -> "$cambios para subirse."
                        ultima > 0 -> "Todo guardado en la nube · ${haceRato(ultima, vm.ahora)}"
                        else -> "Todo guardado en la nube"
                    }
                    val bien = estado == EstadoNube.OK && pendientes == 0
                    Fila(
                        icono = when {
                            estado == EstadoNube.SOLO_LOCAL -> Icons.Default.PhoneAndroid
                            bien -> Icons.Default.CloudDone
                            else -> Icons.Default.CloudOff
                        },
                        titulo = texto,
                    )
                    if (vm.nube.configurada) {
                        FilledTonalButton(
                            onClick = {
                                sincronizando = true
                                ambito.launch { vm.nube.sincronizar(); sincronizando = false }
                            },
                            enabled = !sincronizando,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).height(ALTO_BOTON),
                        ) { Text(if (sincronizando) "Sincronizando…" else "Sincronizar ahora") }
                    }
                }

                // ---- bloqueo
                Tarjeta {
                    Fila(
                        icono = Icons.Default.Fingerprint,
                        titulo = "Pedir huella o clave al abrir",
                        detalle = if (vm.puedeBloquear) "Usa el mismo bloqueo de este celular."
                        else "Este celular no tiene bloqueo de pantalla. Actívelo en los ajustes del celular para proteger la app.",
                        alTocar = if (vm.puedeBloquear) ({ vm.cambiarBloqueo(!vm.bloqueoPedido) }) else null,
                    ) {
                        Switch(checked = vm.bloqueoActivo, onCheckedChange = null, enabled = vm.puedeBloquear)
                    }
                }

                // ---- respaldo
                Tarjeta {
                    Fila(
                        icono = Icons.Default.Save,
                        titulo = "Guardar respaldo",
                        detalle = "Crea un archivo con todos los datos. Puede guardarlo en Drive.",
                        alTocar = { guardarArchivo.launch("respaldo-prestamos-${dia(System.currentTimeMillis())}.json") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    Fila(
                        icono = Icons.Default.Restore,
                        titulo = "Recuperar un respaldo",
                        detalle = "Trae los datos de un archivo de respaldo. No borra lo que ya hay.",
                        alTocar = { abrirArchivo.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                    )
                }

                Tarjeta {
                    val n = libro.eliminados.size
                    Fila(
                        Icons.Default.DeleteOutline, "Clientes eliminados",
                        when (n) {
                            0 -> "No hay ninguno."
                            1 -> "Hay 1. Toque para recuperarlo."
                            else -> "Hay $n. Toque para recuperar alguno."
                        },
                        alTocar = if (n > 0) ({ vm.abrir(Pantalla.Papelera) }) else null,
                    )
                }

                if (vm.nube.configurada) {
                    Tarjeta {
                        Fila(Icons.AutoMirrored.Filled.Logout, "Cerrar sesión", alTocar = {
                            if (pendientes > 0) vm.avisar("Primero conéctese a internet para subir los cambios.") else cerrando = true
                        })
                    }
                }

                Tarjeta {
                    Fila(
                        Icons.Default.SystemUpdate, "Versión ${BuildConfig.VERSION_NAME}",
                        if (vm.novedad != null) "Hay una versión nueva: ${vm.novedad?.version}. Vuelva a Clientes para actualizar."
                        else "Toque para buscar una versión nueva.",
                        alTocar = { vm.buscarActualizacion(aMano = true) },
                    )
                }
            }
        }
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

@Composable
private fun Fila(
    icono: ImageVector,
    titulo: String,
    detalle: String? = null,
    alTocar: (() -> Unit)? = null,
    final: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(titulo, style = MaterialTheme.typography.titleSmall) },
        supportingContent = detalle?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        leadingContent = { Icon(icono, null, Modifier.size(28.dp)) },
        trailingContent = final,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .then(if (alTocar != null) Modifier.clickable(onClick = alTocar) else Modifier)
            .heightIn(min = 64.dp)
            .padding(vertical = 4.dp),
    )
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
                            FilledTonalButton(onClick = { vm.recuperarCliente(c) }, modifier = Modifier.height(ALTO_BOTON)) { Text("Recuperar") }
                        }
                    }
                }
            }
        }
    }
}
