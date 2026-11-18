package pe.reizo.prestamos.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import pe.reizo.prestamos.R
import pe.reizo.prestamos.core.Cliente
import pe.reizo.prestamos.core.DiaTarjeta
import pe.reizo.prestamos.core.EstadoDia
import pe.reizo.prestamos.core.Libro
import pe.reizo.prestamos.core.Prestamo
import pe.reizo.prestamos.core.dia
import pe.reizo.prestamos.core.diasTarjeta
import pe.reizo.prestamos.core.fechaCorta
import pe.reizo.prestamos.core.mensajeWhatsapp
import pe.reizo.prestamos.core.mesLargo
import pe.reizo.prestamos.core.mesesTarjeta
import pe.reizo.prestamos.core.soles
import java.io.File
import java.time.YearMonth

private val SEMANA = listOf("L", "M", "M", "J", "V", "S", "D")

private fun nombreMes(mes: YearMonth) = mesLargo(mes.atDay(1))

private fun resumenMes(dias: List<DiaTarjeta>): String {
    val pagos = dias.filter { it.estado == EstadoDia.PAGO }
    val sinPago = dias.count { it.estado == EstadoDia.SIN_PAGO }
    val pago = if (pagos.size == 1) "Pagó 1 día" else "Pagó ${pagos.size} días"
    val falto = if (sinPago == 1) "no pagó 1 día" else "no pagó $sinPago días"
    return "$pago (${soles(pagos.sumOf { it.monto })}) · $falto"
}

/** La tarjeta de cobranza de papel como calendario del mes: verde si pagó, rojo si no pagó. */
@Composable
fun TarjetaCobranza(vm: AppVM, libro: Libro, prestamoId: String) {
    val p = libro.prestamo(prestamoId)
    val cliente = p?.let { libro.cliente(it.clienteId) }
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    Scaffold(topBar = { BarraInterna("Tarjeta de cobranza", vm::volver) }) { margen ->
        if (p == null || cliente == null) return@Scaffold
        val hoy = dia(vm.ahora)
        val meses = mesesTarjeta(p, hoy)
        var indice by rememberSaveable(prestamoId) { mutableIntStateOf(meses.lastIndex) }
        val mes = meses[indice.coerceIn(meses.indices)]
        val dias = diasTarjeta(p, libro.pagosDe(p.id), mes, hoy)
        Box(Modifier.padding(margen).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column {
                    Text(cliente.nombre, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Préstamo del ${fechaCorta(p.creadoEn, vm.ahora)} · va a pagar ${soles(p.montoAPagar)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.onSurfaceVariant,
                    )
                }

                // Mes: con flechas para ver los anteriores
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { indice-- }, enabled = indice > 0, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior")
                    }
                    Text(nombreMes(mes), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { indice++ }, enabled = indice < meses.lastIndex, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SEMANA.forEach {
                            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    val vacios = mes.atDay(1).dayOfWeek.value - 1   // lunes = 0
                    val celdas: List<DiaTarjeta?> = List(vacios) { null } + dias
                    celdas.chunked(7).forEach { semana ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            semana.forEach { d -> if (d == null) Spacer(Modifier.weight(1f)) else DiaCasilla(d, Modifier.weight(1f)) }
                            repeat(7 - semana.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Leyenda(c.primaryContainer, "Pagó")
                    Leyenda(c.errorContainer, "No pagó")
                    Leyenda(c.tertiaryContainer, "Prestó")
                }
                Text(resumenMes(dias), style = MaterialTheme.typography.bodyLarge)

                HorizontalDivider(color = c.surfaceContainerHigh)
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Ya pagó", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                        Text(soles(libro.pagado(p)), style = MaterialTheme.typography.headlineSmall)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Le falta", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                        Text(soles(libro.falta(p)), style = MaterialTheme.typography.headlineSmall, color = c.error)
                    }
                }
                Button(
                    onClick = { enviarTarjeta(ctx, libro, p, cliente, vm.ahora, mes) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Enviar por WhatsApp", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun DiaCasilla(d: DiaTarjeta, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val forma = MaterialTheme.shapes.small
    val (fondo, texto) = when (d.estado) {
        EstadoDia.PAGO -> c.primaryContainer to c.onPrimaryContainer
        EstadoDia.SIN_PAGO -> c.errorContainer to c.onErrorContainer
        EstadoDia.PRESTO -> c.tertiaryContainer to c.onTertiaryContainer
        EstadoDia.HOY -> c.surface to c.onSurface
        EstadoDia.FUERA, EstadoDia.FUTURO -> Color.Transparent to c.outline
    }
    Surface(
        color = fondo,
        contentColor = texto,
        shape = forma,
        modifier = modifier.heightIn(min = 56.dp).let { if (d.estado == EstadoDia.HOY) it.border(2.dp, c.primary, forma) else it },
    ) {
        Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${d.fecha.dayOfMonth}", style = MaterialTheme.typography.labelLarge)
            when (d.estado) {
                EstadoDia.PAGO -> Text(soles(d.monto).removePrefix("S/ "), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                EstadoDia.SIN_PAGO -> Text("✕", style = MaterialTheme.typography.labelMedium)
                else -> {}
            }
        }
    }
}

@Composable
private fun Leyenda(color: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(color = color, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.size(18.dp)) {}
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Dibuja la tarjeta del mes como imagen (siempre en colores claros, como el papel) para mandarla al cliente. */
fun imagenTarjeta(ctx: Context, libro: Libro, p: Prestamo, cliente: Cliente, ahora: Long, mes: YearMonth): Bitmap {
    val dias = diasTarjeta(p, libro.pagosDe(p.id), mes, dia(ahora))
    val vacios = mes.atDay(1).dayOfWeek.value - 1
    val semanas = (vacios + dias.size + 6) / 7
    val ancho = 1080f
    val m = 48f
    val hueco = 10f
    val lado = (ancho - 2 * m - hueco * 6) / 7
    val alto = lado * 0.95f
    val inicioGrilla = 420f
    val pie = inicioGrilla + semanas * (alto + hueco) + 40f
    val altoTotal = pie + 300f

    val normal = ResourcesCompat.getFont(ctx, R.font.atkinson_regular)
    val negrita = ResourcesCompat.getFont(ctx, R.font.atkinson_bold)
    fun pincel(tam: Float, color: Int, bold: Boolean = false, alinear: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tam; this.color = color; typeface = if (bold) negrita else normal; textAlign = alinear
    }
    val centro = Paint.Align.CENTER
    val tinta = 0xFF1C1B1F.toInt()
    val gris = 0xFF6F6E69.toInt()
    val verdeFondo = 0xFFD6EDCF.toInt(); val verde = 0xFF1B5E20.toInt()
    val rojoFondo = 0xFFF9D6D3.toInt(); val rojo = 0xFFB3261E.toInt()
    val ambarFondo = 0xFFFBE3B8.toInt(); val ambar = 0xFF7A4B00.toInt()

    val bmp = Bitmap.createBitmap(ancho.toInt(), altoTotal.toInt(), Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.drawColor(0xFFFFFDF5.toInt())
    cv.drawText("Tarjeta de cobranza", ancho / 2, 100f, pincel(60f, tinta, bold = true, alinear = centro))
    cv.drawText(cliente.nombre, m, 185f, pincel(44f, tinta, bold = true))
    cv.drawText("Préstamo del ${fechaCorta(p.creadoEn, ahora)} · va a pagar ${soles(p.montoAPagar)}", m, 240f, pincel(34f, gris))
    cv.drawText(nombreMes(mes), ancho / 2, 320f, pincel(44f, tinta, bold = true, alinear = centro))
    val sem = pincel(30f, gris, bold = true, alinear = centro)
    SEMANA.forEachIndexed { i, s -> cv.drawText(s, m + i * (lado + hueco) + lado / 2, 395f, sem) }

    val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFFD3D1C7.toInt() }
    val relleno = Paint(Paint.ANTI_ALIAS_FLAG)
    dias.forEachIndexed { i, d ->
        val pos = vacios + i
        val x = m + (pos % 7) * (lado + hueco)
        val y = inicioGrilla + (pos / 7) * (alto + hueco)
        val r = RectF(x, y, x + lado, y + alto)
        val (fondo, letra) = when (d.estado) {
            EstadoDia.PAGO -> verdeFondo to verde
            EstadoDia.SIN_PAGO -> rojoFondo to rojo
            EstadoDia.PRESTO -> ambarFondo to ambar
            else -> null to gris
        }
        if (fondo != null) cv.drawRoundRect(r, 12f, 12f, relleno.apply { color = fondo }) else cv.drawRoundRect(r, 12f, 12f, borde)
        cv.drawText("${d.fecha.dayOfMonth}", r.centerX(), y + 48f, pincel(32f, letra, alinear = centro))
        when (d.estado) {
            EstadoDia.PAGO -> cv.drawText(soles(d.monto).removePrefix("S/ "), r.centerX(), y + 100f, pincel(32f, letra, bold = true, alinear = centro))
            EstadoDia.SIN_PAGO -> cv.drawText("✕", r.centerX(), y + 100f, pincel(30f, letra, bold = true, alinear = centro))
            EstadoDia.PRESTO -> cv.drawText("Prestó", r.centerX(), y + 98f, pincel(22f, letra, alinear = centro))
            else -> {}
        }
    }

    cv.drawText(resumenMes(dias), m, pie + 20f, pincel(34f, tinta))
    cv.drawText("En total pagó ${soles(libro.pagado(p))}", m, pie + 110f, pincel(46f, tinta, bold = true))
    val falta = libro.falta(p)
    cv.drawText(
        if (falta > 0) "Le falta ${soles(falta)}" else "Terminó de pagar",
        ancho - m, pie + 110f,
        pincel(46f, if (falta > 0) rojo else verde, bold = true, alinear = Paint.Align.RIGHT),
    )
    cv.drawText("Verde: pagó · Rojo: no pagó · Al ${fechaCorta(ahora, 0)}", ancho / 2, pie + 200f, pincel(30f, gris, alinear = centro))
    return bmp
}

/** Guarda la imagen y la abre en el chat del cliente en WhatsApp; si no se puede, deja elegir con qué enviarla. */
fun enviarTarjeta(ctx: Context, libro: Libro, p: Prestamo, cliente: Cliente, ahora: Long, mes: YearMonth = mesesTarjeta(p, dia(ahora)).last()) {
    val carpeta = File(ctx.cacheDir, "tarjetas").apply { mkdirs() }
    val archivo = File(carpeta, "tarjeta.png")
    archivo.outputStream().use { imagenTarjeta(ctx, libro, p, cliente, ahora, mes).compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.archivos", archivo)
    fun envio() = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, mensajeWhatsapp(cliente.nombre, libro.deuda(cliente.id)))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // "jid" no es oficial: WhatsApp lo usa para ir directo al chat; si lo ignora, pide elegir el contacto
    for (app in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        try {
            ctx.startActivity(envio().setPackage(app).putExtra("jid", "51${cliente.celular}@s.whatsapp.net"))
            return
        } catch (_: ActivityNotFoundException) {}
    }
    runCatching { ctx.startActivity(Intent.createChooser(envio(), "Enviar tarjeta")) }
}
