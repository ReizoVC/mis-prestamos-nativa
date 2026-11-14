@file:OptIn(ExperimentalMaterial3Api::class)

package pe.reizo.prestamos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.reizo.prestamos.core.Libro
import pe.reizo.prestamos.core.Periodo
import pe.reizo.prestamos.core.Resumen
import pe.reizo.prestamos.core.dia
import pe.reizo.prestamos.core.diasEntre
import pe.reizo.prestamos.core.fechaCorta
import pe.reizo.prestamos.core.ganancia
import pe.reizo.prestamos.core.soles
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Las tres cifras del periodo. Cada una abre su propio detalle. */
enum class TipoDesglose(val titulo: String) { PRESTO("Prestó"), COBRO("Cobró"), GANO("Ganó") }

@Composable
fun ResumenPestana(vm: AppVM, libro: Libro) {
    val rango = vm.rango
    val resumen = remember(libro, rango) { libro.resumen(rango) }
    var eligiendoFechas by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme

    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        // Lo más importante primero y sin adornos: cuánto le deben hoy. No depende del periodo.
        Column(Modifier.padding(top = 4.dp, bottom = 28.dp)) {
            Text("Le deben hoy", style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant)
            Text(soles(libro.leDebenHoy), style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp, lineHeight = 56.sp))
            Text(
                "Cuando terminen de pagar, ganará ${soles(libro.ganara)} más",
                style = MaterialTheme.typography.bodyLarge,
                color = c.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val cortos = listOf(Periodo.HOY, Periodo.SEMANA, Periodo.MES, Periodo.ANIO)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(ALTO_BOTON)) {
                cortos.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = vm.periodo == p,
                        onClick = { vm.periodo = p },
                        shape = SegmentedButtonDefaults.itemShape(i, cortos.size),
                        icon = {},
                        modifier = Modifier.fillMaxHeight(),
                    ) { Text(p.etiqueta, maxLines = 1) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = vm.periodo == Periodo.FECHAS,
                    onClick = { eligiendoFechas = true },
                    label = { Text("Elegir fechas") },
                    leadingIcon = { Icon(Icons.Default.DateRange, null, Modifier.size(22.dp)) },
                    modifier = Modifier.height(52.dp),
                )
                Text(rango.texto, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            }
        }

        // Las tres cifras del periodo, todas iguales: se tocan para ver de dónde sale cada una.
        Tarjeta(Modifier.padding(top = 12.dp)) {
            FilaCifra("Prestó", soles(resumen.presto)) { vm.abrir(Pantalla.Desglose(TipoDesglose.PRESTO)) }
            HorizontalDivider(color = c.surfaceContainerHigh)
            FilaCifra("Cobró", soles(resumen.cobro)) { vm.abrir(Pantalla.Desglose(TipoDesglose.COBRO)) }
            HorizontalDivider(color = c.surfaceContainerHigh)
            FilaCifra("Ganó", soles(resumen.gano), color = c.primary) { vm.abrir(Pantalla.Desglose(TipoDesglose.GANO)) }
        }
        Text(
            "Toque una cifra para ver el detalle.",
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant,
        )
    }

    if (eligiendoFechas) {
        fun LocalDate.utc() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        fun Long.fecha() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
        val estado = rememberDateRangePickerState(
            initialSelectedStartDateMillis = vm.desde.utc(),
            initialSelectedEndDateMillis = vm.hasta.utc(),
        )
        DatePickerDialog(
            onDismissRequest = { eligiendoFechas = false },
            confirmButton = {
                TextButton(
                    enabled = estado.selectedStartDateMillis != null,
                    onClick = {
                        val a = estado.selectedStartDateMillis
                        if (a != null) {
                            vm.desde = a.fecha()
                            vm.hasta = (estado.selectedEndDateMillis ?: a).fecha()
                            vm.periodo = Periodo.FECHAS
                        }
                        eligiendoFechas = false
                    },
                ) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { eligiendoFechas = false }) { Text("Cancelar") } },
        ) {
            DateRangePicker(
                state = estado,
                title = { Text("Elegir fechas", Modifier.padding(start = 24.dp, top = 16.dp)) },
                showModeToggle = false,
                modifier = Modifier.heightIn(max = 520.dp),
            )
        }
    }
}

@Composable
private fun FilaCifra(nombre: String, valor: String, color: Color = Color.Unspecified, alTocar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Ver detalle", onClick = alTocar)
            .heightIn(min = 72.dp).padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(nombre, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(valor, style = MaterialTheme.typography.headlineSmall, color = color)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Detalle de una cifra del Resumen: el total arriba y debajo las filas que lo forman. */
@Composable
fun Desglose(vm: AppVM, libro: Libro, tipo: TipoDesglose) {
    val rango = vm.rango
    val resumen = remember(libro, rango) { libro.resumen(rango) }
    val c = MaterialTheme.colorScheme
    val total = when (tipo) {
        TipoDesglose.PRESTO -> resumen.presto
        TipoDesglose.COBRO -> resumen.cobro
        TipoDesglose.GANO -> resumen.gano
    }
    val explicacion = when (tipo) {
        TipoDesglose.PRESTO ->
            if (resumen.prestados.isEmpty()) "No dio préstamos en este periodo." else "Los préstamos que dio en este periodo."
        TipoDesglose.COBRO ->
            if (resumen.cobrados.isEmpty()) "No recibió pagos en este periodo." else "Los pagos que recibió, día por día."
        TipoDesglose.GANO ->
            if (resumen.terminados.isEmpty()) "Ningún préstamo terminó de pagarse en este periodo, así que todavía no hay ganancia que contar."
            else "Solo cuentan los préstamos que ya terminaron de pagarse. Toque uno para ver la cuenta."
    }

    Scaffold(topBar = { BarraInterna(tipo.titulo, vm::volver) }) { margen ->
        Box(Modifier.padding(margen).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 600.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Column(Modifier.padding(bottom = 20.dp)) {
                        Text(rango.texto, style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant)
                        Text(
                            soles(total),
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp, lineHeight = 56.sp),
                            color = if (tipo == TipoDesglose.GANO) c.primary else c.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(explicacion, color = c.onSurfaceVariant)
                    }
                }
                when (tipo) {
                    TipoDesglose.PRESTO -> itemsIndexed(resumen.prestados, key = { _, p -> p.id }) { i, p ->
                        FilaDesglose(
                            titulo = libro.cliente(p.clienteId)?.nombre ?: "—",
                            detalle = fechaCorta(p.creadoEn, vm.ahora),
                            monto = soles(p.montoPrestado),
                            posicion = i, total = resumen.prestados.size,
                        ) { vm.abrir(Pantalla.Ficha(p.clienteId)) }
                    }
                    TipoDesglose.COBRO -> filasCobro(vm, libro, resumen)
                    TipoDesglose.GANO -> itemsIndexed(resumen.terminados, key = { _, p -> p.id }) { i, p ->
                        var abierto by rememberSaveable(p.id) { mutableStateOf(false) }
                        Surface(color = c.surfaceContainerLowest, shape = formaEnGrupo(i, resumen.terminados.size)) {
                            Column {
                                Row(
                                    Modifier.fillMaxWidth().clickable { abierto = !abierto }.heightIn(min = 68.dp).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(libro.cliente(p.clienteId)?.nombre ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                    Text("+ ${soles(ganancia(p))}", style = MaterialTheme.typography.titleMedium, color = c.primary, fontWeight = FontWeight.Bold)
                                    Icon(if (abierto) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (abierto) "Cerrar" else "Abrir")
                                }
                                AnimatedVisibility(abierto) {
                                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                        Text(
                                            "Préstamo del ${fechaCorta(p.creadoEn, vm.ahora)}, terminó de pagar el ${fechaCorta(p.pagadoEn ?: 0, vm.ahora)}.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = c.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        FilaDato("Le pagó", soles(p.montoAPagar))
                                        FilaDato("Le prestó", "− ${soles(p.montoPrestado)}")
                                        HorizontalDivider(color = c.outlineVariant)
                                        FilaDato("Ganó", soles(ganancia(p)), colorDerecha = c.primary, negrita = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pagos agrupados por día, cada día con su subtotal: sirve para cuadrar lo cobrado contra el efectivo. */
private fun LazyListScope.filasCobro(vm: AppVM, libro: Libro, resumen: Resumen) {
    val porDia = resumen.cobrados.groupBy { dia(it.fecha) }
    porDia.forEach { (fecha, pagos) ->
        item(key = "dia-$fecha") {
            val nombreDia = when (diasEntre(pagos.first().fecha, vm.ahora)) {
                0 -> "Hoy"
                1 -> "Ayer"
                else -> fechaCorta(pagos.first().fecha, vm.ahora)
            }
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(nombreDia, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                // Con un solo pago en el día, el subtotal repetiría la misma cifra de la fila
                if (pagos.size > 1) {
                    Text(soles(pagos.sumOf { it.monto }), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(pagos, key = { _, p -> p.id }) { i, p ->
            val clienteId = libro.prestamo(p.prestamoId)?.clienteId
            FilaDesglose(
                titulo = clienteId?.let { libro.cliente(it)?.nombre } ?: "—",
                detalle = null,
                monto = soles(p.monto),
                posicion = i, total = pagos.size,
            ) { if (clienteId != null) vm.abrir(Pantalla.Ficha(clienteId)) }
        }
    }
}

/** Fila de un desglose: nombre a la izquierda, monto a la derecha. Al tocarla abre al cliente. */
@Composable
private fun FilaDesglose(titulo: String, detalle: String?, monto: String, posicion: Int, total: Int, alTocar: () -> Unit) {
    val forma = formaEnGrupo(posicion, total)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = forma,
        modifier = Modifier.fillMaxWidth().clip(forma).clickable(onClickLabel = "Ver cliente", onClick = alTocar),
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                if (detalle != null) Text(detalle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(monto, style = MaterialTheme.typography.titleMedium)
        }
    }
}
