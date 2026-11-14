@file:OptIn(ExperimentalMaterial3Api::class)

package pe.reizo.prestamos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.reizo.prestamos.core.Libro

@Composable
fun App(vm: AppVM, pedirDesbloqueo: () -> Unit) {
    val libro by vm.libro.collectAsStateWithLifecycle()
    val conSesion by vm.nube.conSesion.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val datos = libro
        when {
            vm.bloqueado -> Bloqueo(pedirDesbloqueo)
            vm.nube.configurada && !conSesion -> InicioSesion(vm)
            datos != null -> Navegacion(vm, datos)
        }
    }
}

@Composable
private fun Navegacion(vm: AppVM, libro: Libro) {
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.avisos.collect { aviso ->
            avisos.currentSnackbarData?.dismiss()
            launch {
                // Los avisos con "Deshacer" duran más, para dar tiempo a leer y tocar
                val duracion = if (aviso.accion != null) SnackbarDuration.Long else SnackbarDuration.Short
                val r = avisos.showSnackbar(aviso.texto, aviso.accion, duration = duracion)
                if (r == SnackbarResult.ActionPerformed) aviso.alAccion?.invoke()
            }
        }
    }
    LaunchedEffect(Unit) { vm.avisarSiSeActualizo() }
    val guardadas = rememberSaveableStateHolder()
    val actual = vm.pila.lastOrNull()
    BackHandler(enabled = actual != null) { vm.volver() }

    AnimatedContent(
        targetState = actual,
        transitionSpec = {
            if (vm.avanzando) (slideInHorizontally { it / 5 } + fadeIn()) togetherWith fadeOut()
            else fadeIn() togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
        },
        label = "pantallas",
    ) { pantalla ->
        guardadas.SaveableStateProvider(pantalla?.toString() ?: "inicio") {
            when (pantalla) {
                null -> Inicio(vm, libro, avisos)
                is Pantalla.Ficha -> Ficha(vm, libro, pantalla.clienteId, avisos)
                is Pantalla.Nuevo -> NuevoPrestamo(vm, libro, pantalla.clienteId, avisos)
                Pantalla.Ganancia -> Ganancia(vm, libro)
                Pantalla.Ajustes -> Ajustes(vm, libro, avisos)
                Pantalla.Papelera -> Papelera(vm, libro, avisos)
            }
        }
    }
}

@Composable
private fun Inicio(vm: AppVM, libro: Libro, avisos: SnackbarHostState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (vm.pestana) {
                            Pestana.CLIENTES -> "Clientes"
                            Pestana.ANTIGUOS -> "Antiguos"
                            Pestana.RESUMEN -> "Resumen"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                actions = {
                    if (vm.pestana == Pestana.RESUMEN) {
                        IconButton(onClick = { vm.abrir(Pantalla.Ajustes) }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes", modifier = Modifier.size(28.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar {
                @Composable
                fun RowScope.pestana(p: Pestana, texto: String, icono: @Composable () -> Unit) {
                    NavigationBarItem(
                        selected = vm.pestana == p,
                        onClick = { vm.pestana = p },
                        icon = icono,
                        label = { Text(texto) },
                    )
                }
                pestana(Pestana.CLIENTES, "Clientes") { Icon(Icons.Default.People, null) }
                pestana(Pestana.ANTIGUOS, "Antiguos") { Icon(Icons.Default.History, null) }
                pestana(Pestana.RESUMEN, "Resumen") { Icon(Icons.Default.BarChart, null) }
            }
        },
        floatingActionButton = {
            if (vm.pestana != Pestana.RESUMEN) {
                ExtendedFloatingActionButton(
                    onClick = { vm.abrir(Pantalla.Nuevo(null)) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Nuevo préstamo") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.height(64.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { margen ->
        Box(Modifier.padding(margen).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 600.dp)) {
                AvisoActualizacion(vm)
                when (vm.pestana) {
                    Pestana.CLIENTES -> ListaClientes(vm, libro)
                    Pestana.ANTIGUOS -> ListaAntiguos(vm, libro)
                    Pestana.RESUMEN -> ResumenPestana(vm, libro)
                }
            }
        }
    }
}

/** Aparece arriba de las pestañas cuando hay una versión más nueva publicada. */
@Composable
private fun AvisoActualizacion(vm: AppVM) {
    val novedad = vm.novedad ?: return
    val avance = vm.descarga
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Hay una versión nueva", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (avance == null) "Toque para tener las mejoras" else "Descargando la versión ${novedad.version}…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (avance == null) {
                    Button(
                        onClick = vm::actualizar,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.inverseOnSurface,
                            contentColor = MaterialTheme.colorScheme.inverseSurface,
                        ),
                        modifier = Modifier.height(ALTO_BOTON),
                    ) { Text("Actualizar") }
                }
            }
            if (avance != null) {
                LinearProgressIndicator(
                    progress = { avance },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.inversePrimary,
                    trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.25f),
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun Bloqueo(pedir: () -> Unit) {
    LaunchedEffect(Unit) { pedir() }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text("Mis Préstamos", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Use su huella o la clave de este celular para entrar.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = pedir, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Entrar") }
    }
}

/** Solo aparece si la nube está configurada. La cuenta la crea el administrador; aquí no hay registro. */
@Composable
private fun InicioSesion(vm: AppVM) {
    var correo by rememberSaveable { mutableStateOf("") }
    var clave by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var entrando by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Iniciar sesión", style = MaterialTheme.typography.headlineMedium)
        Text("Solo se hace una vez. Así sus datos quedan guardados también en la nube.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = correo, onValueChange = { correo = it; error = null },
            label = { Text("Correo") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = clave, onValueChange = { clave = it; error = null },
            label = { Text("Contraseña") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
        )
        Button(
            onClick = {
                entrando = true
                ambito.launch {
                    error = vm.nube.iniciarSesion(correo, clave)
                    entrando = false
                }
            },
            enabled = !entrando && correo.isNotBlank() && clave.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) { Text(if (entrando) "Entrando…" else "Entrar") }
    }
}
