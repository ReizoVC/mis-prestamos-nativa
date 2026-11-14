@file:OptIn(ExperimentalMaterial3Api::class)

package pe.reizo.prestamos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pe.reizo.prestamos.core.Cliente
import pe.reizo.prestamos.core.Libro
import pe.reizo.prestamos.core.Pago
import pe.reizo.prestamos.core.Prestamo
import pe.reizo.prestamos.core.celularBonito
import pe.reizo.prestamos.core.dia
import pe.reizo.prestamos.core.diasEntre
import pe.reizo.prestamos.core.errorCelular
import pe.reizo.prestamos.core.errorDni
import pe.reizo.prestamos.core.errorDniRepetido
import pe.reizo.prestamos.core.errorNombre
import pe.reizo.prestamos.core.errorPago
import pe.reizo.prestamos.core.fechaCorta
import pe.reizo.prestamos.core.fechaLarga
import pe.reizo.prestamos.core.haceDias
import pe.reizo.prestamos.core.inicioDia
import pe.reizo.prestamos.core.mensajeWhatsapp
import pe.reizo.prestamos.core.montoParaCampo
import pe.reizo.prestamos.core.parseMonto
import pe.reizo.prestamos.core.primerNombre
import pe.reizo.prestamos.core.soles
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun abrirEnlace(ctx: Context, accion: String, enlace: String) {
    // Si el celular no tiene con qué abrirlo (por ejemplo, sin WhatsApp), no pasa nada
    runCatching { ctx.startActivity(Intent(accion, Uri.parse(enlace))) }
}

@Composable
fun Ficha(vm: AppVM, libro: Libro, clienteId: String, avisos: SnackbarHostState) {
    val cliente = libro.cliente(clienteId)
    val ctx = LocalContext.current
    var pagoPara by remember { mutableStateOf<Prestamo?>(null) }
    var pagoElegido by remember { mutableStateOf<Pago?>(null) }
    var corrigiendo by remember { mutableStateOf<Pago?>(null) }
    var borrando by remember { mutableStateOf<Pago?>(null) }
    var eliminando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf(false) }
    var notaDe by remember { mutableStateOf<Prestamo?>(null) }
    val ambito = rememberCoroutineScope()

    Scaffold(
        topBar = {
            BarraInterna("Cliente", vm::volver) {
                if (cliente != null) {
                    TextButton(onClick = { editando = true }, modifier = Modifier.heightIn(min = ALTO_BOTON)) {
                        Icon(Icons.Default.Edit, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Editar")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { margen ->
        if (cliente == null) return@Scaffold
        val activos = libro.activosDe(clienteId)
        val pagados = libro.pagadosDe(clienteId)
        Box(Modifier.padding(margen).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column {
                    Text(cliente.nombre, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Cel. ${celularBonito(cliente.celular)} · DNI ${cliente.dni}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = { abrirEnlace(ctx, Intent.ACTION_DIAL, "tel:${cliente.celular}") },
                        modifier = Modifier.weight(1f).height(ALTO_BOTON),
                    ) {
                        Icon(Icons.Default.Call, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Llamar")
                    }
                    Button(
                        onClick = {
                            // Abre el chat con el mensaje ya escrito; ella decide si lo envía o lo cambia
                            val texto = Uri.encode(mensajeWhatsapp(cliente.nombre, libro.deuda(clienteId)))
                            abrirEnlace(ctx, Intent.ACTION_VIEW, "https://wa.me/51${cliente.celular}?text=$texto")
                        },
                        modifier = Modifier.weight(1f).height(ALTO_BOTON),
                        colors = ButtonDefaults.buttonColors(containerColor = VerdeWhatsapp, contentColor = Color.White),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null)
                        Spacer(Modifier.width(8.dp))
                        Text("WhatsApp")
                    }
                }

                activos.forEach { p ->
                    PrestamoActivo(
                        libro, p, vm.ahora,
                        alPagar = { pagoPara = p },
                        alTocarPago = { pagoElegido = it },
                        alTocarNota = { notaDe = p },
                    )
                }

                if (activos.isEmpty()) {
                    Tarjeta(color = MaterialTheme.colorScheme.primaryContainer) {
                        val termino = libro.terminoEn(clienteId)?.let { " Terminó de pagar el ${fechaCorta(it, vm.ahora)}." } ?: ""
                        Text("No debe nada.$termino", Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }

                if (pagados.isNotEmpty()) {
                    Text("Préstamos ya pagados (${pagados.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Tarjeta { ListaPrestamos(libro, pagados, vm.ahora) }
                }

                FilledTonalButton(
                    onClick = { vm.abrir(Pantalla.Nuevo(clienteId)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_BOTON),
                ) { Text("Nuevo préstamo para ${primerNombre(cliente.nombre)}") }

                // Lejos de los botones de uso diario, para no tocarlo sin querer
                TextButton(
                    onClick = { eliminando = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_BOTON).padding(top = 16.dp),
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Eliminar cliente")
                }
            }
        }
    }

    // ---- registrar pago
    pagoPara?.let { p ->
        val nombre = cliente?.nombre ?: ""
        HojaPago(
            titulo = "Registrar pago",
            subtitulo = "$nombre · le falta ${soles(libro.falta(p))}",
            maximo = libro.falta(p),
            inicial = "",
            ahora = vm.ahora,
            fechaMinima = p.creadoEn,
            alCerrar = { pagoPara = null },
            alGuardar = { monto, fecha ->
                pagoPara = null
                ambito.launch {
                    val guardado = vm.repo.registrarPago(p.id, monto, fecha)
                    val quedanOtros = libro.activosDe(clienteId).any { it.id != p.id }
                    val texto = when {
                        guardado.termino && !quedanOtros -> "${primerNombre(nombre)} terminó de pagar. Pasa a Antiguos."
                        guardado.termino -> "${primerNombre(nombre)} terminó de pagar este préstamo."
                        else -> "Pago de ${soles(monto)} guardado"
                    }
                    // Si se equivocó de monto o de cliente, lo quita con un toque
                    vm.avisar(texto, "Deshacer") { vm.repo.borrarPago(guardado.id) }
                }
            },
        )
    }

    // ---- menú de un pago: corregir o borrar
    pagoElegido?.let { pago ->
        ModalBottomSheet(onDismissRequest = { pagoElegido = null }) {
            Text(
                "Pago de ${soles(pago.monto)} del ${fechaCorta(pago.fecha, vm.ahora)}",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            val transparente = ListItemDefaults.colors(containerColor = Color.Transparent)
            ListItem(
                headlineContent = { Text("Corregir monto", style = MaterialTheme.typography.bodyLarge) },
                leadingContent = { Icon(Icons.Default.Edit, null) },
                colors = transparente,
                modifier = Modifier.clickable { corrigiendo = pago; pagoElegido = null }.heightIn(min = 64.dp).padding(horizontal = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Borrar pago", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                colors = transparente,
                modifier = Modifier.clickable { borrando = pago; pagoElegido = null }.heightIn(min = 64.dp).padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    corrigiendo?.let { pago ->
        val p = libro.prestamo(pago.prestamoId)
        if (p != null) {
            // Lo máximo que puede valer este pago: lo que falta más lo que ya cuenta este mismo pago
            val maximo = p.montoAPagar - (libro.pagado(p) - pago.monto)
            HojaPago(
                titulo = "Corregir monto",
                subtitulo = "Pago del ${fechaCorta(pago.fecha, vm.ahora)}. Antes decía ${soles(pago.monto)}.",
                maximo = maximo,
                inicial = montoParaCampo(pago.monto),
                ahora = vm.ahora,
                fechaMinima = null,
                alCerrar = { corrigiendo = null },
                alGuardar = { monto, _ ->
                    corrigiendo = null
                    ambito.launch {
                        vm.repo.corregirPago(pago.id, monto)
                        vm.avisar("Pago corregido a ${soles(monto)}")
                    }
                },
            )
        }
    }

    borrando?.let { pago ->
        AlertDialog(
            onDismissRequest = { borrando = null },
            title = { Text("¿Borrar el pago de ${soles(pago.monto)} del ${fechaCorta(pago.fecha, vm.ahora)}?") },
            confirmButton = {
                TextButton(onClick = {
                    borrando = null
                    vm.borrarPago(pago.id)
                }) { Text("Sí, borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { borrando = null }) { Text("No") } },
        )
    }

    if (eliminando && cliente != null) {
        val deuda = libro.deuda(clienteId)
        AlertDialog(
            onDismissRequest = { eliminando = false },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Eliminar a ${cliente.nombre}?") },
            text = {
                Text(
                    (if (deuda > 0) "Todavía le debe ${soles(deuda)}. " else "") +
                        "Se quitarán sus préstamos y pagos, y dejarán de contarse en el resumen. " +
                        "Podrá recuperarlo desde Ajustes.",
                )
            },
            confirmButton = {
                TextButton(onClick = { eliminando = false; vm.eliminarCliente(cliente) }) {
                    Text("Sí, eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { eliminando = false }) { Text("No") } },
        )
    }

    if (editando && cliente != null) {
        HojaEditarCliente(
            cliente, libro,
            alCerrar = { editando = false },
            alGuardar = { nombre, celular, dni ->
                editando = false
                ambito.launch {
                    vm.repo.editarCliente(cliente.id, nombre, celular, dni)
                    vm.avisar("Datos guardados")
                }
            },
        )
    }

    notaDe?.let { p ->
        var texto by remember(p.id) { mutableStateOf(p.nota) }
        AlertDialog(
            onDismissRequest = { notaDe = null },
            title = { Text("Nota del préstamo") },
            text = {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.take(120) },
                    placeholder = { Text("Ej.: paga los sábados") },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    notaDe = null
                    ambito.launch { vm.repo.cambiarNota(p.id, texto) }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { notaDe = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PrestamoActivo(
    libro: Libro,
    p: Prestamo,
    ahora: Long,
    alPagar: () -> Unit,
    alTocarPago: (Pago) -> Unit,
    alTocarNota: () -> Unit,
) {
    val pagado = libro.pagado(p)
    val falta = libro.falta(p)
    val pagos = libro.pagosDe(p.id)
    Tarjeta {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Préstamo del ${fechaCorta(p.creadoEn, ahora)}", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { if (p.montoAPagar > 0) pagado.toFloat() / p.montoAPagar else 0f },
                modifier = Modifier.fillMaxWidth().height(12.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
            Row {
                Dato("Le prestó", soles(p.montoPrestado), Modifier.weight(1f))
                Dato("Le va a pagar", soles(p.montoAPagar), Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Dato("Ya pagó", soles(pagado), Modifier.weight(1f))
                Column(Modifier.weight(1f)) {
                    Text("Le falta", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(soles(falta), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Button(onClick = alPagar, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Registrar pago") }
        }
        // Nota: se toca para escribirla o cambiarla
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Cambiar nota", onClick = alTocarNota)
                .heightIn(min = ALTO_BOTON).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.StickyNote2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                p.nota.ifBlank { "Agregar una nota" },
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (p.nota.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        Column(Modifier.padding(top = 14.dp, bottom = 8.dp)) {
            Text("Pagos que hizo", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleSmall)
            if (pagos.isEmpty()) {
                Text(
                    "Todavía no hizo pagos.",
                    Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            pagos.forEach { pago ->
                Row(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Corregir o borrar") { alTocarPago(pago) }
                        .heightIn(min = 56.dp).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${fechaCorta(pago.fecha, ahora)} · ${haceDias(diasEntre(pago.fecha, ahora))}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(soles(pago.monto), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Dato(nombre: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(nombre, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Hoja desde abajo para escribir un monto de pago (nuevo o corregido).
 * Con `fechaMinima` deja elegir el día del pago (entre el día del préstamo y hoy).
 */
@Composable
private fun HojaPago(
    titulo: String,
    subtitulo: String,
    maximo: Long,
    inicial: String,
    ahora: Long,
    fechaMinima: Long?,
    alCerrar: () -> Unit,
    alGuardar: (monto: Long, fecha: Long?) -> Unit,
) {
    var texto by remember { mutableStateOf(inicial) }
    var error by remember { mutableStateOf<String?>(null) }
    var otroDia by remember { mutableStateOf<LocalDate?>(null) }   // null = hoy
    var eligiendoDia by remember { mutableStateOf(false) }
    val foco = remember { FocusRequester() }
    val vibrar = LocalHapticFeedback.current
    val monto = parseMonto(texto)
    val c = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(titulo, style = MaterialTheme.typography.headlineSmall)
                Text(subtitulo, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            }
            CampoMonto(texto, { texto = it; error = null }, "¿Cuánto le pagó?", Modifier.focusRequester(foco), error = error, ultimo = true)
            LaunchedEffect(Unit) { foco.requestFocus() }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                @Composable
                fun RowScope.rapido(etiqueta: String, valor: Long) {
                    FilledTonalButton(
                        onClick = { texto = montoParaCampo(valor); error = null },
                        modifier = Modifier.weight(1f).height(ALTO_BOTON),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) { Text(etiqueta) }
                }
                if (maximo >= 5_000) rapido("S/ 50", 5_000)
                if (maximo >= 10_000) rapido("S/ 100", 10_000)
                rapido("Todo", maximo)
            }

            if (fechaMinima != null) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Cambiar el día del pago") { eligiendoDia = true }.heightIn(min = ALTO_BOTON),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Event, null, tint = c.onSurfaceVariant)
                    Text(
                        "Pagó " + (otroDia?.let { "el ${fechaLarga(it)}" } ?: "hoy"),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text("Cambiar día", style = MaterialTheme.typography.labelLarge, color = c.primary)
                }
            }

            // Lo que pasará al guardar
            val (fondo, linea, cifra) = when {
                monto != null && monto > maximo -> Triple(c.errorContainer, "Es más de lo que debe", "Máximo ${soles(maximo)}")
                monto != null && monto == maximo -> Triple(c.primaryContainer, "¡Termina de pagar!", "Debe S/ 0")
                else -> Triple(c.inverseSurface, "Le quedará debiendo", soles(maximo - (monto ?: 0)))
            }
            Surface(color = fondo, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(linea, style = MaterialTheme.typography.bodyLarge)
                    Text(cifra, style = MaterialTheme.typography.displaySmall)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = alCerrar, modifier = Modifier.weight(1f).height(60.dp)) { Text("Cancelar") }
                Button(
                    onClick = {
                        error = errorPago(monto, maximo)
                        if (error == null && monto != null) {
                            vibrar.performHapticFeedback(HapticFeedbackType.LongPress)
                            // Un día pasado se guarda al mediodía de ese día
                            alGuardar(monto, otroDia?.let { inicioDia(it) + 12 * 3_600_000L })
                        }
                    },
                    modifier = Modifier.weight(1f).height(60.dp),
                ) { Text("Guardar") }
            }
        }
    }

    if (eligiendoDia && fechaMinima != null) {
        fun LocalDate.utc() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val hoy = dia(ahora)
        val desde = dia(fechaMinima)
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = (otroDia ?: hoy).utc(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in desde.utc()..hoy.utc()
                override fun isSelectableYear(year: Int) = year in desde.year..hoy.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { eligiendoDia = false },
            confirmButton = {
                TextButton(onClick = {
                    val elegido = estado.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    otroDia = elegido?.takeIf { it != hoy }
                    eligiendoDia = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { eligiendoDia = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = estado, title = { Text("¿Qué día pagó?", Modifier.padding(start = 24.dp, top = 16.dp)) }, showModeToggle = false)
        }
    }
}

/** Corregir nombre, celular o DNI de un cliente. */
@Composable
private fun HojaEditarCliente(
    cliente: Cliente,
    libro: Libro,
    alCerrar: () -> Unit,
    alGuardar: (nombre: String, celular: String, dni: String) -> Unit,
) {
    var nombre by remember { mutableStateOf(cliente.nombre) }
    var celular by remember { mutableStateOf(cliente.celular) }
    var dni by remember { mutableStateOf(cliente.dni) }
    var errores by remember { mutableStateOf(List<String?>(3) { null }) }

    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Editar datos", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = nombre, onValueChange = { nombre = it.take(60) },
                label = { Text("Nombre") }, singleLine = true,
                isError = errores[0] != null, supportingText = errores[0]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = celular, onValueChange = { celular = it.filter(Char::isDigit).take(9) },
                label = { Text("Celular") }, singleLine = true,
                isError = errores[1] != null, supportingText = errores[1]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dni, onValueChange = { dni = it.filter(Char::isDigit).take(8) },
                label = { Text("DNI") }, singleLine = true,
                isError = errores[2] != null, supportingText = errores[2]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = alCerrar, modifier = Modifier.weight(1f).height(60.dp)) { Text("Cancelar") }
                Button(
                    onClick = {
                        errores = listOf(
                            errorNombre(nombre),
                            errorCelular(celular),
                            errorDni(dni) ?: errorDniRepetido(dni, cliente.id, libro.clientes),
                        )
                        if (errores.all { it == null }) alGuardar(nombre, celular, dni)
                    },
                    modifier = Modifier.weight(1f).height(60.dp),
                ) { Text("Guardar") }
            }
        }
    }
}
