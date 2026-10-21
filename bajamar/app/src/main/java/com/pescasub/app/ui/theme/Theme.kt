package com.pescasub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta oceánica propia: azul marino, azul profundo, turquesa y fondos neutros.
private val DeepNavy = Color(0xFF0B3C5D)
private val Ocean = Color(0xFF1D6A96)
private val Turquoise = Color(0xFF0E8F86)
private val TurquoiseLight = Color(0xFF4DD0C4)

private val LightColors = lightColorScheme(
    primary = DeepNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E8F5),
    onPrimaryContainer = Color(0xFF062B44),
    secondary = Turquoise,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDF1EE),
    onSecondaryContainer = Color(0xFF00322F),
    tertiary = Ocean,
    background = Color(0xFFF5F8FA),
    onBackground = Color(0xFF10212B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10212B),
    surfaceVariant = Color(0xFFE3ECF1),
    onSurfaceVariant = Color(0xFF3F535F),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CCBEF),
    onPrimary = Color(0xFF00304A),
    primaryContainer = Color(0xFF0F4A6E),
    onPrimaryContainer = Color(0xFFD3E8F5),
    secondary = TurquoiseLight,
    onSecondary = Color(0xFF00332F),
    secondaryContainer = Color(0xFF00514B),
    onSecondaryContainer = Color(0xFFCDF1EE),
    tertiary = Color(0xFF7FB8D6),
    background = Color(0xFF081820),
    onBackground = Color(0xFFDDE8EE),
    surface = Color(0xFF0F232D),
    onSurface = Color(0xFFDDE8EE),
    surfaceVariant = Color(0xFF1B3440),
    onSurfaceVariant = Color(0xFFB5C8D2),
    error = Color(0xFFF2B8B5)
)

/** Colores semánticos de la valoración (siempre acompañados de icono y texto). */
object RatingColors {
    val Good = Color(0xFF1B8A5A)
    val Fair = Color(0xFFB7791F)
    val Poor = Color(0xFFC0392B)
    val Unknown = Color(0xFF6B7C86)
}

/** Colores de series de gráficos, distinguibles en claro y oscuro. */
object ChartColors {
    val Primary = Color(0xFF1D6A96)
    val Secondary = Color(0xFF0E8F86)
    val Accent = Color(0xFFD98324)
}

@Composable
fun BajamarTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
