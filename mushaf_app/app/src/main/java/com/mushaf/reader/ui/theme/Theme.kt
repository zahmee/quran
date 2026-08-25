package com.mushaf.reader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = AccentContainerLight,
    onPrimaryContainer = Color(0xFF153D2D),
    inversePrimary = AccentDark,
    secondary = GoldLight,
    onSecondary = Color.White,
    secondaryContainer = GoldContainerLight,
    onSecondaryContainer = Color(0xFF4E3B17),
    tertiary = Color(0xFF5F7567),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5ECE7),
    onTertiaryContainer = Color(0xFF26382D),
    background = PaperLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceSoftLight,
    onSurfaceVariant = MutedInkLight,
    surfaceTint = AccentLight,
    inverseSurface = Color(0xFF2C312C),
    inverseOnSurface = Color(0xFFF1EEE6),
    error = ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFF5DEDB),
    onErrorContainer = Color(0xFF5B1B17),
    outline = Color(0xFF7D857E),
    outlineVariant = LineLight,
    scrim = Color.Black,
    surfaceDim = Color(0xFFE4DED3),
    surfaceBright = SurfaceLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF9F2),
    surfaceContainer = Color(0xFFF5F1E8),
    surfaceContainerHigh = Color(0xFFEFEAE1),
    surfaceContainerHighest = Color(0xFFE9E4DB),
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF0C3525),
    primaryContainer = AccentContainerDark,
    onPrimaryContainer = Color(0xFFC6EED7),
    inversePrimary = AccentLight,
    secondary = GoldDark,
    onSecondary = Color(0xFF3C2D06),
    secondaryContainer = GoldContainerDark,
    onSecondaryContainer = Color(0xFFEFD8A1),
    tertiary = Color(0xFFAFC9B8),
    onTertiary = Color(0xFF1C3528),
    tertiaryContainer = Color(0xFF314A3B),
    onTertiaryContainer = Color(0xFFD0E7D7),
    background = PaperDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceSoftDark,
    onSurfaceVariant = MutedInkDark,
    surfaceTint = AccentDark,
    inverseSurface = Color(0xFFE3E8E1),
    inverseOnSurface = Color(0xFF252A25),
    error = ErrorDark,
    onError = Color(0xFF561410),
    errorContainer = Color(0xFF752620),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF899188),
    outlineVariant = LineDark,
    scrim = Color.Black,
    surfaceDim = Color(0xFF0F120F),
    surfaceBright = Color(0xFF353A35),
    surfaceContainerLowest = Color(0xFF0C0F0C),
    surfaceContainerLow = Color(0xFF151815),
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = Color(0xFF222722),
    surfaceContainerHighest = Color(0xFF2C312C),
)

/**
 * Recolors the app chrome to [palette]. The accent roles (primary/secondary/tertiary/error) are
 * shared by every theme — only the neutral surfaces take the palette's hue, so the greens and
 * golds stay recognisable across all six.
 */
private fun schemeFor(palette: MushafPalette): ColorScheme {
    val base = if (palette.dark) DarkColors else LightColors
    if (!palette.tintsChrome) return base
    fun t(c: Color) = palette.tintNeutral(c)
    return base.copy(
        background = t(base.background),
        onBackground = t(base.onBackground),
        surface = t(base.surface),
        onSurface = t(base.onSurface),
        surfaceVariant = t(base.surfaceVariant),
        onSurfaceVariant = t(base.onSurfaceVariant),
        surfaceDim = t(base.surfaceDim),
        surfaceBright = t(base.surfaceBright),
        surfaceContainerLowest = t(base.surfaceContainerLowest),
        surfaceContainerLow = t(base.surfaceContainerLow),
        surfaceContainer = t(base.surfaceContainer),
        surfaceContainerHigh = t(base.surfaceContainerHigh),
        surfaceContainerHighest = t(base.surfaceContainerHighest),
        inverseSurface = t(base.inverseSurface),
        inverseOnSurface = t(base.inverseOnSurface),
        outline = t(base.outline),
        outlineVariant = t(base.outlineVariant),
    )
}

@Composable
fun MushafTheme(
    paletteId: String,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(paletteId)
    MaterialTheme(
        colorScheme = remember(palette) { schemeFor(palette) },
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
