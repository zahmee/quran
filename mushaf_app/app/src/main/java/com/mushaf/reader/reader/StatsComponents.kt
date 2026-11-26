package com.mushaf.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mushaf.reader.ui.components.MushafPanel
import kotlin.math.roundToInt

@Composable
internal fun StatsCard(colors: StatsColors, content: @Composable ColumnScope.() -> Unit) {
    MushafPanel(containerColor = colors.card, content = content)
}

@Composable
internal fun SectionHeader(title: String, subtitle: String, colors: StatsColors) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.ink)
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.muted)
    }
}

@Composable
internal fun CompactLine(label: String, value: String, colors: StatsColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = colors.ink, textAlign = TextAlign.Start)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.muted, textAlign = TextAlign.Start)
    }
}

@Composable
internal fun IconBubble(icon: ImageVector, colors: StatsColors) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(colors.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun statsPalette(): StatsColors {
    val scheme = MaterialTheme.colorScheme
    return StatsColors(
        page = scheme.background,
        card = scheme.surface,
        tile = scheme.surfaceContainerHigh,
        subtle = scheme.surfaceContainerLow,
        track = scheme.outlineVariant,
        divider = scheme.outlineVariant,
        ink = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        primary = scheme.primary,
        secondary = scheme.tertiary,
        warm = scheme.secondary,
        onPrimary = scheme.onPrimary,
        heroStart = scheme.primaryContainer,
        heroEnd = scheme.primaryContainer,
        heroInk = scheme.onPrimaryContainer,
        heroMuted = scheme.onPrimaryContainer.copy(alpha = 0.72f),
        heroAccent = scheme.secondary,
        heroButton = scheme.surfaceVariant,
        heroFact = scheme.surfaceVariant,
        heroFactInk = scheme.onSurface,
        heroFactMuted = scheme.onSurfaceVariant,
    )
}

internal data class StatsColors(
    val page: Color,
    val card: Color,
    val tile: Color,
    val subtle: Color,
    val track: Color,
    val divider: Color,
    val ink: Color,
    val muted: Color,
    val primary: Color,
    val secondary: Color,
    val warm: Color,
    val onPrimary: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val heroInk: Color,
    val heroMuted: Color,
    val heroAccent: Color,
    val heroButton: Color,
    val heroFact: Color,
    val heroFactInk: Color,
    val heroFactMuted: Color,
)

internal fun formatPace(pagesPerMinute: Double): String {
    if (pagesPerMinute <= 0.0) return "-"
    if (pagesPerMinute >= 1.0) return "${pagesPerMinute.roundToInt().toArabicDigits()} صفحة في الدقيقة"
    val minutesPerPage = (1.0 / pagesPerMinute).roundToInt().coerceAtLeast(1)
    return "صفحة كل ${minutesPerPage.toArabicDigits()} دقيقة"
}

internal fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60000L).toInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        totalMin <= 0 -> "أقل من دقيقة"
        h == 0 -> "${m.toArabicDigits()} دقيقة"
        m == 0 -> "${h.toArabicDigits()} ساعة"
        else -> "${h.toArabicDigits()} ساعة و ${m.toArabicDigits()} دقيقة"
    }
}
