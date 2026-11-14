@file:OptIn(ExperimentalMaterial3Api::class)

package pe.reizo.prestamos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.reizo.prestamos.core.Libro
import pe.reizo.prestamos.core.Periodo
import pe.reizo.prestamos.core.fechaCorta
import pe.reizo.prestamos.core.ganancia
import pe.reizo.prestamos.core.soles
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ResumenPestana(vm: AppVM, libro: Libro) {
    val rango = vm.rango
    val resumen = remember(libro, rango) { libro.resumen(rango) }
    var eligiendoFechas by remember { mutableStateOf(false) }
    var visibles by rememberSaveable(rango.desde, rango.hasta) { mutableIntStateOf(30) }
    val c = MaterialTheme.colorScheme
    val linea = c.surfaceContainerHigh

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        // Lo más importante primero y sin adornos: cuánto le deben hoy. No depende del periodo.
        item {
            Column(Modifier.padding(top = 4.dp, bottom = 28.dp)) {
                Text("Le deben hoy", style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant)
                Text(
                    soles(libro.leDebenHoy),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp, lineHeight = 56.sp),
                )
                Text(
                    "Cuando terminen de pagar, ganará ${soles(libro.ganara)} más",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                )
            }
        }
        item {
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
        }
        // Las tres cifras del periodo en un solo bloque. La ganancia se puede tocar para ver el detalle.
        item {
            Tarjeta(Modifier.padding(top = 12.dp)) {
                FilaGrande("Prestó", soles(resumen.presto), Modifier.padding(horizontal = 20.dp))
                HorizontalDivider(color = linea)
                FilaGrande("Cobró", soles(resumen.cobro), Modifier.padding(horizontal = 20.dp))
                HorizontalDivider(color = linea)
                Row(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Ver detalle de la ganancia") { vm.abrir(Pantalla.Ganancia) }
                        .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ganó", style = MaterialTheme.typography.titleMedium)
                        val n = resumen.terminados.size
                        Text(
                            when (n) {
                                0 -> "Ningún préstamo terminó en este periodo"
                                1 -> "De 1 préstamo terminado"
                                else -> "De $n préstamos terminados"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.onSurfaceVariant,
                        )
                    }
                    Text(soles(resumen.gano), style = MaterialTheme.typography.headlineSmall, color = c.primary)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(28.dp), tint = c.onSurfaceVariant)
                }
            }
        }
        item { Text("Movimientos", Modifier.padding(top = 32.dp, bottom = 4.dp), style = MaterialTheme.typography.titleLarge) }
        if (resumen.movimientos.isEmpty()) {
            item { Text("No hubo movimientos en este periodo.", color = c.onSurfaceVariant) }
        }
        val lista = resumen.movimientos.take(visibles)
        itemsIndexed(lista, key = { _, m -> m.id }) { i, m ->
            if (i > 0) HorizontalDivider(color = linea)
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (m.esPago) "Pagó ${m.nombre}" else "Prestó a ${m.nombre}", style = MaterialTheme.typography.bodyLarge)
                    Text(fechaCorta(m.fecha, vm.ahora), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                }
                Text(
                    (if (m.esPago) "+ " else "- ") + soles(m.monto),
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = if (m.esPago) c.primary else c.onSurface,
                )
            }
        }
        if (resumen.movimientos.size > visibles) {
            item {
                FilledTonalButton(
                    onClick = { visibles += 30 },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(ALTO_BOTON),
                ) { Text("Ver más") }
            }
        }
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
private fun FilaGrande(nombre: String, valor: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(nombre, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(valor, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun Ganancia(vm: AppVM, libro: Libro) {
    val rango = vm.rango
    val resumen = remember(libro, rango) { libro.resumen(rango) }
    val c = MaterialTheme.colorScheme
    Scaffold(topBar = { BarraInterna("Ganancia", vm::volver) }) { margen ->
        Box(Modifier.padding(margen).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 600.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Column(Modifier.padding(bottom = 20.dp)) {
                        Text("Ganó en total · ${rango.texto}", style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant)
                        Text(
                            soles(resumen.gano),
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp, lineHeight = 56.sp),
                            color = c.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (resumen.terminados.isEmpty()) "Ningún préstamo terminó de pagarse en este periodo, así que todavía no hay ganancia que contar."
                            else "Solo cuentan los préstamos que ya terminaron de pagarse. Toque uno para ver la cuenta.",
                            color = c.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(resumen.terminados, key = { _, p -> p.id }) { i, p ->
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
