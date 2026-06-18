package com.mushaf.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Accent,
    background = PaperLight,
    surface = PaperLight,
    onBackground = InkLight,
    onSurface = InkLight,
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = PaperDark,
    surface = PaperDark,
    onBackground = InkDark,
    onSurface = InkDark,
)

@Composable
fun MushafTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
