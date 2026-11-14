package pe.reizo.prestamos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val VerdeWhatsapp = Color(0xFF1F7A4D)

// Colores fijos (no los del fondo de pantalla del celular): el verde y el rojo siempre significan lo mismo.
private val Claro = lightColorScheme(
    primary = Color(0xFF1D6B48), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCF0E2), onPrimaryContainer = Color(0xFF0B3D27),
    secondary = Color(0xFF4A6355), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8DF), onSecondaryContainer = Color(0xFF1A2B22),
    tertiary = Color(0xFF3B6470), onTertiary = Color.White,
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFFBE6E4), onErrorContainer = Color(0xFF8C1D18),
    background = Color(0xFFF7F8F5), onBackground = Color(0xFF1A1F1C),
    surface = Color(0xFFF7F8F5), onSurface = Color(0xFF1A1F1C),
    surfaceVariant = Color(0xFFE2E6E0), onSurfaceVariant = Color(0xFF4A524D),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF2F4EF),
    surfaceContainer = Color(0xFFEEF0EC), surfaceContainerHigh = Color(0xFFE8EBE5),
    surfaceContainerHighest = Color(0xFFE2E6E0),
    outline = Color(0xFF727A74), outlineVariant = Color(0xFFC3C9C3),
    inverseSurface = Color(0xFF1A1F1C), inverseOnSurface = Color(0xFFF2F4EF), inversePrimary = Color(0xFF8FD6AE),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF8FD6AE), onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF0F5236), onPrimaryContainer = Color(0xFFC9F0D7),
    secondary = Color(0xFFB4CCBB), onSecondary = Color(0xFF203529),
    secondaryContainer = Color(0xFF33473B), onSecondaryContainer = Color(0xFFD5E5D9),
    tertiary = Color(0xFFA3CDDB), onTertiary = Color(0xFF033541),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1512), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111512), onBackground = Color(0xFFE1E4DE),
    surface = Color(0xFF111512), onSurface = Color(0xFFE1E4DE),
    surfaceVariant = Color(0xFF414942), onSurfaceVariant = Color(0xFFC1C8C1),
    surfaceContainerLowest = Color(0xFF1B201C), surfaceContainerLow = Color(0xFF191D1A),
    surfaceContainer = Color(0xFF1D211E), surfaceContainerHigh = Color(0xFF272B28),
    surfaceContainerHighest = Color(0xFF323633),
    outline = Color(0xFF8B938C), outlineVariant = Color(0xFF414942),
    inverseSurface = Color(0xFFE1E4DE), inverseOnSurface = Color(0xFF2E322F), inversePrimary = Color(0xFF1D6B48),
)

// Letra grande: nada baja de 16. Los números usan cifras del mismo ancho para que las columnas cuadren.
private val b = Typography()
private const val TNUM = "tnum"
private val Tipos = Typography(
    displaySmall = b.displaySmall.copy(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TNUM),
    headlineMedium = b.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = b.headlineSmall.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TNUM),
    titleLarge = b.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TNUM),
    titleMedium = b.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = b.titleSmall.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = b.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = b.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodySmall = b.bodySmall.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    labelLarge = b.labelLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = b.labelMedium.copy(fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelSmall = b.labelSmall.copy(fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
)

private val Formas = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TemaPrestamos(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        typography = Tipos,
        shapes = Formas,
        content = contenido,
    )
}
