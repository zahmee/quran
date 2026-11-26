package com.mushaf.reader.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.ui.theme.StatusBlueColor
import com.mushaf.reader.ui.theme.StatusGoldColor
import com.mushaf.reader.ui.theme.StatusGreenColor
import com.mushaf.reader.ui.theme.StatusRedColor

/** Where one header action lives: a button on the bar itself, or a line in the More menu. */
@Composable
internal fun PlacementChoiceRow(
    inBar: Boolean,
    onSelected: (Boolean) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val choices = listOf(
        Triple(true, "الشريط العلوي", Icons.Outlined.Tune),
        Triple(false, "قائمة المزيد", Icons.Filled.MoreVert),
    )
    val current = choices.first { it.first == inBar }
    val color = if (inBar) accent else muted
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "المكان",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = current.third,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = current.second,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "اختيار المكان",
                        tint = color
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = choice.third,
                                    contentDescription = null,
                                    tint = if (choice.first == inBar) color
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = choice.second,
                                    color = if (choice.first == inBar) color
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (choice.first == inBar) FontWeight.SemiBold
                                    else FontWeight.Normal
                                )
                            }
                        },
                        trailingIcon = if (choice.first == inBar) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = color) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelected(choice.first)
                        }
                    )
                }
            }
        }
    }
}

/** Color picker rendered as a compact dropdown menu (label on one side, an anchored pill that
 *  opens the full list of colors). Same signature as before, so every color setting in the screen
 *  becomes a dropdown at once. */
@Composable
internal fun ColorChoiceRow(
    title: String,
    selected: String,
    onSelected: (String) -> Unit,
) {
    val choices = listOf(
        HeaderColorChoice("muted", "هادئ", MaterialTheme.colorScheme.onSurfaceVariant),
        HeaderColorChoice("green", "أخضر", StatusGreenColor),
        HeaderColorChoice("red", "أحمر", StatusRedColor),
        HeaderColorChoice("gold", "ذهبي", StatusGoldColor),
        HeaderColorChoice("blue", "أزرق", StatusBlueColor),
    )
    val current = choices.firstOrNull { it.id == selected } ?: choices.first()
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = current.color.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, current.color.copy(alpha = 0.6f)),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(modifier = Modifier.size(12.dp), shape = CircleShape, color = current.color) {}
                    Text(
                        text = current.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = current.color
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "اختيار اللون",
                        tint = current.color
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(modifier = Modifier.size(14.dp), shape = CircleShape, color = choice.color) {}
                                Text(
                                    text = choice.label,
                                    color = if (choice.id == selected) choice.color
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (choice.id == selected) FontWeight.SemiBold
                                    else FontWeight.Normal
                                )
                            }
                        },
                        trailingIcon = if (choice.id == selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = choice.color) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelected(choice.id)
                        }
                    )
                }
            }
        }
    }
}

private data class HeaderColorChoice(
    val id: String,
    val label: String,
    val color: Color,
)

/** Bar-thickness picker, same dropdown shape as [ColorChoiceRow]. Each choice carries a sample line
 *  drawn at its own thickness, so the pick is visible before it is applied to the page. */
private data class EdgeMarginChoice(
    val id: String,
    val label: String,
    val body: String,
)

/**
 * Chooses how far the corner controls and the page pull back from a curved screen edge.
 *
 * A dropdown rather than a toggle because "auto" cannot always be trusted: the corner radius is
 * only readable from Android 12 onwards, and some phones under-report it. The fixed steps are the
 * way out for a reader whose device the measurement fails.
 */
@Composable
internal fun EdgeMarginChoiceRow(selected: String, onSelected: (String) -> Unit) {
    val choices = listOf(
        EdgeMarginChoice(ReadingStore.EDGE_MARGIN_NONE, "بدون", "الوضع الحالي، بلا تغيير"),
        EdgeMarginChoice(ReadingStore.EDGE_MARGIN_AUTO, "تلقائي", "يقيسه من انحناء جهازك"),
        EdgeMarginChoice(ReadingStore.EDGE_MARGIN_SMALL, "صغير", "هامش خفيف"),
        EdgeMarginChoice(ReadingStore.EDGE_MARGIN_MEDIUM, "متوسط", "هامش معتدل"),
        EdgeMarginChoice(ReadingStore.EDGE_MARGIN_LARGE, "كبير", "لأشد الحواف انحناءً"),
    )
    val accent = MaterialTheme.colorScheme.primary
    val current = choices.firstOrNull { it.id == selected } ?: choices[0]
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp)) {
        Text(
            text = "تُبعد النقاط الثلاث وزر الإعدادات وأطراف الصفحة عن الزاوية المنحنية قليلاً، " +
                "فلا يقتطعها انحناء الشاشة. القياس التلقائي يحتاج أندرويد ١٢ فأحدث، وإلا عومل " +
                "معاملة «متوسط».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "هامش الحواف المنحنية",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                    modifier = Modifier.clickable { expanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = current.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "اختيار هامش الحواف",
                            tint = accent
                        )
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    choices.forEach { choice ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = choice.label,
                                        color = if (choice.id == selected) accent
                                        else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (choice.id == selected) FontWeight.SemiBold
                                        else FontWeight.Normal
                                    )
                                    Text(
                                        text = choice.body,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingIcon = if (choice.id == selected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, tint = accent) }
                            } else null,
                            onClick = {
                                expanded = false
                                onSelected(choice.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ThicknessChoiceRow(
    title: String,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val choices = listOf(
        BarThicknessChoice(2, "رفيع جداً"),
        BarThicknessChoice(4, "رفيع"),
        BarThicknessChoice(6, "متوسط"),
        BarThicknessChoice(9, "سميك"),
        BarThicknessChoice(12, "عريض"),
    )
    val accent = MaterialTheme.colorScheme.primary
    // Only this list ever writes the setting, so an unknown value can't normally show up; fall back
    // to the default thickness (4.dp) if it somehow does.
    val current = choices.firstOrNull { it.thickness == selected } ?: choices[1]
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThicknessSample(current.thickness, accent)
                    Text(
                        text = current.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "اختيار السمك",
                        tint = accent
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ThicknessSample(
                                    thickness = choice.thickness,
                                    color = if (choice.thickness == selected) accent
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = choice.label,
                                    color = if (choice.thickness == selected) accent
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (choice.thickness == selected) FontWeight.SemiBold
                                    else FontWeight.Normal
                                )
                            }
                        },
                        trailingIcon = if (choice.thickness == selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = accent) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelected(choice.thickness)
                        }
                    )
                }
            }
        }
    }
}

/** A short line rendered at [thickness] dp — the visual half of a thickness choice. */
@Composable
private fun ThicknessSample(thickness: Int, color: Color) {
    Surface(
        modifier = Modifier
            .width(24.dp)
            .height(thickness.dp),
        shape = RoundedCornerShape(50),
        color = color
    ) {}
}

private data class BarThicknessChoice(
    val thickness: Int,
    val label: String,
)

/** Length picker for the page-side marker — same dropdown shape as [ThicknessChoiceRow], but the
 *  sample stands upright like the marker itself. */
@Composable
internal fun LengthChoiceRow(
    title: String,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val choices = listOf(
        BarLengthChoice(24, "قصير جداً"),
        BarLengthChoice(40, "قصير"),
        BarLengthChoice(64, "متوسط"),
        BarLengthChoice(96, "طويل"),
        BarLengthChoice(140, "ممتد"),
    )
    val accent = MaterialTheme.colorScheme.primary
    // Only this list ever writes the setting; fall back to the default length (40.dp) otherwise.
    val current = choices.firstOrNull { it.length == selected } ?: choices[1]
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LengthSample(current.length, accent)
                    Text(
                        text = current.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "اختيار الطول",
                        tint = accent
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LengthSample(
                                    length = choice.length,
                                    color = if (choice.length == selected) accent
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = choice.label,
                                    color = if (choice.length == selected) accent
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (choice.length == selected) FontWeight.SemiBold
                                    else FontWeight.Normal
                                )
                            }
                        },
                        trailingIcon = if (choice.length == selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = accent) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelected(choice.length)
                        }
                    )
                }
            }
        }
    }
}

/** An upright bar standing for a marker [length]. Drawn at a quarter scale so even the longest
 *  choice still fits on one settings row while the choices stay visibly different from each other. */
@Composable
private fun LengthSample(length: Int, color: Color) {
    Surface(
        modifier = Modifier
            .width(4.dp)
            .height((length / 4).dp),
        shape = RoundedCornerShape(50),
        color = color
    ) {}
}

private data class BarLengthChoice(
    val length: Int,
    val label: String,
)

/** Opacity picker (percent), same dropdown shape as the others. The sample swatch is drawn at the
 *  chosen opacity over the row's own background, so «شفاف» reads as barely-there and «معتم» as solid. */
@Composable
internal fun OpacityChoiceRow(
    title: String,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val choices = listOf(
        BarOpacityChoice(25, "شفاف جداً"),
        BarOpacityChoice(50, "شفاف"),
        BarOpacityChoice(70, "متوسط"),
        BarOpacityChoice(85, "خفيف"),
        BarOpacityChoice(100, "معتم"),
    )
    val accent = MaterialTheme.colorScheme.primary
    // Only this list ever writes the setting; fall back to fully opaque if an odd value appears.
    val current = choices.firstOrNull { it.opacity == selected } ?: choices.last()
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OpacitySample(current.opacity, accent)
                    Text(
                        text = current.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "اختيار الشفافية",
                        tint = accent
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OpacitySample(
                                    opacity = choice.opacity,
                                    color = if (choice.opacity == selected) accent
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${choice.label} (${choice.opacity.toArabicDigits()}٪)",
                                    color = if (choice.opacity == selected) accent
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (choice.opacity == selected) FontWeight.SemiBold
                                    else FontWeight.Normal
                                )
                            }
                        },
                        trailingIcon = if (choice.opacity == selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = accent) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelected(choice.opacity)
                        }
                    )
                }
            }
        }
    }
}

/** A small filled dot drawn at [opacity] percent — the visual half of an opacity choice. */
@Composable
private fun OpacitySample(opacity: Int, color: Color) {
    Surface(
        modifier = Modifier.size(16.dp),
        shape = CircleShape,
        color = color.copy(alpha = opacity / 100f)
    ) {}
}

private data class BarOpacityChoice(
    val opacity: Int,
    val label: String,
)
