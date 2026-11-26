package com.mushaf.reader.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import com.mushaf.reader.ui.theme.ReadingType
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.WidthFull
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mushaf.reader.ui.components.MushafIconBadge
import com.mushaf.reader.ui.components.MushafPanel
import com.mushaf.reader.ui.components.MushafSectionHeader
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.ui.components.MushafSoftDivider
import com.mushaf.reader.ui.theme.MushafPalette
import com.mushaf.reader.ui.theme.MushafPalettes

@Composable
internal fun HeaderLayoutPreview(
    showClock: Boolean,
    showSessionTimer: Boolean,
    showFillButton: Boolean,
    showHideButton: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallIconBadge(Icons.Filled.Settings)
                if (showClock) StatusPill("الساعة")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showSessionTimer) StatusPill("الجلسة")
                if (showFillButton) SmallIconBadge(Icons.Outlined.WidthFull)
                if (showHideButton) SmallIconBadge(Icons.Filled.KeyboardArrowUp)
                SmallIconBadge(Icons.Filled.MoreVert)
            }
        }
        Text(
            text = "الإعدادات ثابتة دائماً، أما البحث والفهرس وباقي الأدوات ففي قائمة المزيد.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun MenuLocationHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallIconBadge(Icons.Filled.MoreVert)
        Text(
            text = "كل عملية إمّا مخفية، أو زرّاً ملوّناً في الشريط عند توفر المساحة، أو سطراً داخل قائمة النقاط الثلاث.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
internal fun SettingsPanel(
    title: String,
    body: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Panel {
        MushafSectionHeader(title = title, subtitle = body, icon = icon)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
internal fun ToggleSettingRow(
    icon: ImageVector,
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallIconBadge(icon)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun ActionRow(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallIconBadge(icon)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The reading themes as a grid of page thumbnails: each swatch is painted in that theme's own
 *  paper and ink, so the choice is made by looking rather than by reading a color name. */
@Composable
internal fun ThemeChoiceGrid(
    selected: String,
    onSelected: (String) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MushafPalettes.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { palette ->
                    ThemeChoiceTile(
                        palette = palette,
                        selected = palette.id == selected,
                        accent = accent,
                        onClick = { onSelected(palette.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeChoiceTile(
    palette: MushafPalette,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = palette.paper,
            border = BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) accent else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clickable(onClick = onClick)
        ) {
            // Three ruled lines standing in for the page's text, in that theme's ink.
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf(1f, 1f, 0.55f).forEach { width ->
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = palette.ink.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth(width)
                            .height(4.dp)
                    ) {}
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = palette.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Shown under an action placed on the bar that the reader's window has no room for, so the
 *  choice does not look as if it never saved. */
@Composable
internal fun BarOverflowNote() {
    Text(
        text = "لا يتّسع له الشريط على هذه الشاشة الآن، فهو في قائمة المزيد. انقل زرّاً آخر إلى القائمة ليظهر في الشريط.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    )
}

@Composable
internal fun DangerPanel(onClear: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "سجل القراءة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "سجل الجلسات محفوظ دائماً. المسح نهائي ويتطلب تأكيداً بكتابة كلمة «مسح». سجل الختمات لا يتأثر.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            FilledTonalButton(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("مسح سجل القراءة", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    MushafIconBadge(icon = icon)
}

@Composable
private fun SmallIconBadge(icon: ImageVector) {
    MushafIconBadge(icon = icon, size = 32.dp, iconSize = 16.dp)
}

@Composable
internal fun SettingsDivider() {
    MushafSoftDivider(verticalPadding = 4.dp)
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    MushafPanel(content = content)
}

/**
 * How strongly the note mark shows, with the mark itself drawn live on a line of the mushaf's
 * text at that strength — over a word on purpose, so the reader sees that the letters and their
 * harakat stay readable underneath. The setting is written when the slider is let go.
 */
@Composable
internal fun NoteMarkStrengthRow(percent: Int, onPercentChange: (Int) -> Unit) {
    val range = ReadingStore.NOTE_MARK_OPACITY_RANGE
    var live by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    val paper = MaterialTheme.colorScheme.surface
    val darkPage = paper.luminance() < 0.5f
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "قوة العلامة",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${live.roundToInt().toArabicDigits()}٪",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = live,
            onValueChange = { live = it },
            onValueChangeFinished = { onPercentChange(live.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / 5 - 1,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(paper)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .drawWithContent {
                    drawContent()
                    drawNoteMark(
                        center = Offset(size.width * 0.34f, size.height * 0.42f),
                        radius = size.height * 0.26f,
                        alpha = live / 100f,
                        darkPage = darkPage,
                    )
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "إِنَّ ٱللَّهَ كَانَ عَلَيْكُمْ رَقِيبًا",
                style = ReadingType.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
