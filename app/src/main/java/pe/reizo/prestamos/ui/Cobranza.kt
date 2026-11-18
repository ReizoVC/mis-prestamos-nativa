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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import pe.reizo.prestamos.R
import pe.reizo.prestamos.core.Cliente
import pe.reizo.prestamos.core.Libro
import pe.reizo.prestamos.core.Pago
import pe.reizo.prestamos.core.Prestamo
import pe.reizo.prestamos.core.fechaCorta
import pe.reizo.prestamos.core.mensajeWhatsapp
import pe.reizo.prestamos.core.soles
import java.io.File

private const val POR_FILA = 5

/** Casillas de la tarjeta: los pagos hechos y, si todavía debe, una fila con espacio para los que vienen. */
fun casillasTarjeta(pagos: Int, termino: Boolean): Int {
    val llenas = (pagos + POR_FILA - 1) / POR_FILA * POR_FILA
    return if (termino) llenas.coerceAtLeast(POR_FILA) else maxOf(llenas + if (pagos % POR_FILA == 0) POR_FILA else 0, 2 * POR_FILA)
}

/** La tarjeta de cobranza de papel, hecha con los pagos registrados: cada casilla es un pago. */
@Composable
fun TarjetaCobranza(vm: AppVM, libro: Libro, prestamoId: String) {
    val p = libro.prestamo(prestamoId)
    val cliente = p?.let { libro.cliente(it.clienteId) }
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    Scaffold(topBar = { BarraInterna("Tarjeta de cobranza", vm::volver) }) { margen ->
        if (p == null || cliente == null) return@Scaffold
        val pagos = libro.pagosDe(p.id).reversed()   // el primer pago es la casilla 1
        val total = casillasTarjeta(pagos.size, libro.falta(p) == 0L)
        Box(Modifier.padding(margen).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column {
                    Text(cliente.nombre, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Préstamo del ${fechaCorta(p.creadoEn, vm.ahora)} · va a pagar ${soles(p.montoAPagar)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until total).chunked(POR_FILA).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            fila.forEach { i -> Casilla(i + 1, pagos.getOrNull(i), vm.ahora, Modifier.weight(1f)) }
                        }
                    }
                }
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
                    onClick = { enviarTarjeta(ctx, libro, p, cliente, vm.ahora) },
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
private fun Casilla(numero: Int, pago: Pago?, ahora: Long, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val forma = MaterialTheme.shapes.small
    Surface(
        color = if (pago != null) c.primaryContainer else c.surface,
        contentColor = if (pago != null) c.onPrimaryContainer else c.onSurfaceVariant,
        shape = forma,
        modifier = modifier.heightIn(min = 76.dp).let { if (pago == null) it.border(1.dp, c.outlineVariant, forma) else it },
    ) {
        Column(Modifier.padding(vertical = 6.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$numero", style = MaterialTheme.typography.labelMedium)
            if (pago != null) {
                Text(fechaCorta(pago.fecha, ahora), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 1)
                Text(soles(pago.monto).removePrefix("S/ "), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** Dibuja la tarjeta como imagen (siempre en colores claros, como el papel) para mandarla al cliente. */
fun imagenTarjeta(ctx: Context, libro: Libro, p: Prestamo, cliente: Cliente, ahora: Long): Bitmap {
    val pagos = libro.pagosDe(p.id).reversed()
    val total = casillasTarjeta(pagos.size, libro.falta(p) == 0L)
    val ancho = 1080f
    val m = 56f
    val hueco = 14f
    val lado = (ancho - 2 * m - hueco * (POR_FILA - 1)) / POR_FILA
    val alto = lado * 0.85f
    val filas = total / POR_FILA
    val inicioGrilla = 330f
    val altoTotal = inicioGrilla + filas * (alto + hueco) + 230f

    val normal = ResourcesCompat.getFont(ctx, R.font.atkinson_regular)
    val negrita = ResourcesCompat.getFont(ctx, R.font.atkinson_bold)
    fun pincel(tam: Float, color: Int, bold: Boolean = false, centro: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tam; this.color = color; typeface = if (bold) negrita else normal
        if (centro) textAlign = Paint.Align.CENTER
    }
    val tinta = 0xFF1C1B1F.toInt()
    val gris = 0xFF5F5E5A.toInt()
    val verde = 0xFF1B5E20.toInt()
    val rojo = 0xFFB3261E.toInt()

    val bmp = Bitmap.createBitmap(ancho.toInt(), altoTotal.toInt(), Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.drawColor(0xFFFFFDF5.toInt())
    cv.drawText("Tarjeta de cobranza", ancho / 2, 110f, pincel(64f, tinta, bold = true, centro = true))
    cv.drawText(cliente.nombre, m, 200f, pincel(46f, tinta, bold = true))
    cv.drawText("Préstamo del ${fechaCorta(p.creadoEn, ahora)} · va a pagar ${soles(p.montoAPagar)}", m, 262f, pincel(36f, gris))

    val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFFB4B2A9.toInt() }
    val relleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDCEFD4.toInt() }
    val num = pincel(28f, gris, centro = true)
    val fecha = pincel(30f, verde, centro = true)
    val monto = pincel(36f, verde, bold = true, centro = true)
    for (i in 0 until total) {
        val x = m + (i % POR_FILA) * (lado + hueco)
        val y = inicioGrilla + (i / POR_FILA) * (alto + hueco)
        val r = RectF(x, y, x + lado, y + alto)
        val pago = pagos.getOrNull(i)
        if (pago != null) cv.drawRoundRect(r, 14f, 14f, relleno)
        cv.drawRoundRect(r, 14f, 14f, borde)
        cv.drawText("${i + 1}", r.centerX(), y + 38f, num)
        if (pago != null) {
            cv.drawText(fechaCorta(pago.fecha, ahora), r.centerX(), y + 86f, fecha)
            cv.drawText(soles(pago.monto), r.centerX(), y + 134f, monto)
        }
    }

    val pie = inicioGrilla + filas * (alto + hueco) + 70f
    cv.drawText("Pagó ${soles(libro.pagado(p))}", m, pie, pincel(46f, tinta, bold = true))
    val falta = libro.falta(p)
    cv.drawText(
        if (falta > 0) "Le falta ${soles(falta)}" else "Terminó de pagar",
        ancho - m, pie,
        pincel(46f, if (falta > 0) rojo else verde, bold = true).apply { textAlign = Paint.Align.RIGHT },
    )
    cv.drawText("Al ${fechaCorta(ahora, 0)}", ancho / 2, pie + 90f, pincel(32f, gris, centro = true))
    return bmp
}

/** Guarda la imagen y la abre en el chat del cliente en WhatsApp; si no se puede, deja elegir con qué enviarla. */
fun enviarTarjeta(ctx: Context, libro: Libro, p: Prestamo, cliente: Cliente, ahora: Long) {
    val carpeta = File(ctx.cacheDir, "tarjetas").apply { mkdirs() }
    val archivo = File(carpeta, "tarjeta.png")
    archivo.outputStream().use { imagenTarjeta(ctx, libro, p, cliente, ahora).compress(Bitmap.CompressFormat.PNG, 100, it) }
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
