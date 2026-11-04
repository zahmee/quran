package com.mushaf.reader.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.normalizeArabic
import com.mushaf.reader.data.recitation.Reciter
import com.mushaf.reader.data.recitation.RepeatPlan
import com.mushaf.reader.data.recitation.RepeatPlanner
import com.mushaf.reader.ui.theme.StatusBlueColor

/** Speeds offered in the bar; the pitch is kept at each. */
internal val RecitationSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** "١٫٢٥×" — Arabic digits and the Arabic decimal separator. */
internal fun formatSpeed(speed: Float): String {
    val hundredths = Math.round(speed * 100)
    val whole = hundredths / 100
    val frac = hundredths % 100
    val text = if (frac == 0) "$whole" else "$whole.${if (frac % 10 == 0) frac / 10 else frac}"
    return text.map { if (it == '.') '٫' else if (it.isDigit()) ('٠' + (it - '0')) else it }
        .joinToString("") + "×"
}

/**
 * The recitation bar under the page: what is being recited, then three small chips — the reciter
 * (opens the list), the speed (a menu) and repetition (opens the memorisation sheet; during a run
 * it shows which repetition this is) — and previous/next ayah, play/pause and close. In this
 * right-to-left screen the text sits on the right, "previous" points right and "next" left.
 */
@Composable
internal fun RecitationBar(
    surahName: String,
    ayah: Int?,
    reciterName: String?,
    isPlaying: Boolean,
    buffering: Boolean,
    error: String?,
    speed: Float,
    /** "٣/٧" during a run that repeats the ayah; null otherwise. */
    repeatLabel: String?,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onPickReciter: () -> Unit,
    onSpeed: (Float) -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = error ?: buildString {
                        append("سورة ").append(surahName)
                        if (ayah != null) append(" • الآية ").append(ayah.toArabicDigits())
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    // An error says what to do about it, so it gets a second line.
                    maxLines = if (error != null) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    BarChip(
                        onClick = onPickReciter,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = reciterName ?: "اختيار القارئ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "تغيير القارئ",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SpeedChip(speed = speed, onSpeed = onSpeed)
                    BarChip(onClick = onRepeat) {
                        Icon(
                            imageVector = Icons.Filled.Repeat,
                            contentDescription = "التكرار والحفظ",
                            modifier = Modifier.size(14.dp),
                            tint = if (repeatLabel != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (repeatLabel != null) {
                            Text(
                                text = repeatLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "الآية السابقة")
            }
            FilledIconButton(onClick = onToggle, modifier = Modifier.size(44.dp)) {
                when {
                    buffering && error == null -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    error != null -> Icon(Icons.Filled.Refresh, contentDescription = "إعادة المحاولة")
                    isPlaying -> Icon(Icons.Filled.Pause, contentDescription = "إيقاف مؤقت")
                    else -> Icon(Icons.Filled.PlayArrow, contentDescription = "تشغيل")
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "الآية التالية")
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "إغلاق التلاوة",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BarChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) { content() }
}

@Composable
private fun SpeedChip(speed: Float, onSpeed: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarChip(onClick = { open = true }) {
            Icon(
                imageVector = Icons.Filled.Speed,
                contentDescription = "سرعة التلاوة",
                modifier = Modifier.size(14.dp),
                tint = if (speed != 1f) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatSpeed(speed),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (speed != 1f) FontWeight.Bold else FontWeight.Normal,
                color = if (speed != 1f) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RecitationSpeeds.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatSpeed(option) + if (option == 1f) "  (عادية)" else "",
                            fontWeight = if (option == speed) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    trailingIcon = if (option == speed) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        open = false
                        onSpeed(option)
                    },
                )
            }
        }
    }
}

/**
 * Memorisation: from [fromAyah] of the surah to an ayah the reader picks, each ayah a number of
 * times, then the whole passage a number of times (or without end). It opens on the counts used
 * last time.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RepeatSheet(
    surahName: String,
    surahAyahCount: Int,
    fromAyah: Int,
    /** Last ayah of this surah on the page [fromAyah] is on, for the "end of page" shortcut. */
    pageLastAyah: Int?,
    initialAyahTimes: Int,
    initialRangeTimes: Int,
    onStart: (toAyah: Int, ayahTimes: Int, rangeTimes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var toAyah by remember { mutableIntStateOf(fromAyah) }
    var ayahTimes by remember { mutableIntStateOf(initialAyahTimes) }
    var rangeTimes by remember { mutableIntStateOf(initialRangeTimes) }
    val tooLong = RepeatPlanner.clipCount(
        RepeatPlan(1, fromAyah, toAyah, ayahTimes, rangeTimes)
    ) > RepeatPlanner.MAX_CLIPS

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "التكرار والحفظ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "سورة $surahName، من الآية ${fromAyah.toArabicDigits()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SheetLabel("إلى الآية")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = { toAyah = (toAyah - 1).coerceAtLeast(fromAyah) },
                    enabled = toAyah > fromAyah,
                ) { Icon(Icons.Filled.Remove, contentDescription = "آية أقل") }
                Text(
                    text = toAyah.toArabicDigits(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = { toAyah = (toAyah + 1).coerceAtMost(surahAyahCount) },
                    enabled = toAyah < surahAyahCount,
                ) { Icon(Icons.Filled.Add, contentDescription = "آية أكثر") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = toAyah == fromAyah,
                    onClick = { toAyah = fromAyah },
                    label = { Text("هذه الآية") },
                )
                if (pageLastAyah != null && pageLastAyah > fromAyah) {
                    FilterChip(
                        selected = toAyah == pageLastAyah,
                        onClick = { toAyah = pageLastAyah },
                        label = { Text("نهاية الصفحة") },
                    )
                }
                if (surahAyahCount > fromAyah) {
                    FilterChip(
                        selected = toAyah == surahAyahCount,
                        onClick = { toAyah = surahAyahCount },
                        label = { Text("نهاية السورة") },
                    )
                }
            }

            SheetLabel("تكرار كل آية")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 2, 3, 5, 7, 10, 20).forEach { n ->
                    FilterChip(
                        selected = ayahTimes == n,
                        onClick = { ayahTimes = n },
                        label = { Text(timesLabel(n, dual = "مرتان")) },
                    )
                }
            }

            SheetLabel("تكرار المقطع كله")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 2, 3, 5, 10, RepeatPlan.ENDLESS).forEach { n ->
                    FilterChip(
                        selected = rangeTimes == n,
                        onClick = { rangeTimes = n },
                        label = {
                            Text(
                                if (n == RepeatPlan.ENDLESS) "بلا توقف"
                                else timesLabel(n, dual = "مرتان")
                            )
                        },
                    )
                }
            }

            Text(
                text = if (tooLong) {
                    "المقطع طويل على هذا العدد من التكرار. قلّل الآيات أو مرات التكرار."
                } else {
                    repeatSummary(toAyah - fromAyah + 1, ayahTimes, rangeTimes)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (tooLong) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = { onStart(toAyah, ayahTimes, rangeTimes) },
                enabled = !tooLong,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("ابدأ", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Arabic counted noun: 1 مرة، 2 مرتين، 3–10 مرات، 11+ مرة. */
private fun timesLabel(n: Int, dual: String = "مرتين"): String = when {
    n == 1 -> "مرة"
    n == 2 -> dual
    n in 3..10 -> "${n.toArabicDigits()} مرات"
    else -> "${n.toArabicDigits()} مرة"
}

private fun ayahsLabel(n: Int): String = when {
    n == 1 -> "آية واحدة"
    n == 2 -> "آيتان"
    n in 3..10 -> "${n.toArabicDigits()} آيات"
    else -> "${n.toArabicDigits()} آية"
}

private fun repeatSummary(ayahs: Int, ayahTimes: Int, rangeTimes: Int): String {
    val each = if (ayahTimes == 1) "" else "، كل آية ${timesLabel(ayahTimes)}"
    val whole = when (rangeTimes) {
        RepeatPlan.ENDLESS -> "، ويُعاد المقطع حتى تُوقفه"
        1 -> ""
        else -> "، ويُتلى المقطع كله ${timesLabel(rangeTimes)}"
    }
    return "تُتلى ${ayahsLabel(ayahs)}$each$whole."
}

/** Choosing the reciter: the recitations that have ayah timing, searchable by name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReciterPickerSheet(
    reciters: List<Reciter>,
    selectedId: Int?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onPick: (Reciter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val shown = remember(reciters, query) {
        val q = normalizeArabic(query.trim())
        if (q.isEmpty()) reciters else reciters.filter { normalizeArabic(it.name).contains(q) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "اختيار القارئ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "القرّاء الذين تتوفر لتلاواتهم مواضع الآيات، فتتبع الصفحة التلاوة آيةً آية.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("ابحث باسم القارئ") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            reciters.isEmpty() && loading -> Box(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            reciters.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = error ?: "لا توجد قائمة قرّاء بعد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text("إعادة المحاولة") }
            }

            else -> LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                items(shown, key = { it.id }) { reciter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(reciter) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reciter.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (reciter.id == selectedId) FontWeight.Bold
                                else FontWeight.Normal,
                            )
                            if (reciter.rewaya.isNotBlank()) {
                                Text(
                                    text = reciter.rewaya,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (reciter.id == selectedId) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "القارئ المختار",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

/** The ayah being recited, drawn over the page in its own color (selection is green, the
 *  bookmarks gold and violet). Coordinates are page-image pixels times [baseScale]. */
@Composable
internal fun RecitationHighlight(marker: AyahMarker, baseScale: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val color = StatusBlueColor.copy(alpha = 0.16f)
        val corner = CornerRadius(8f, 8f)
        marker.rects.forEach { r ->
            drawRoundRect(
                color = color,
                topLeft = Offset(r.x * baseScale, r.y * baseScale),
                size = Size(r.w * baseScale, r.h * baseScale),
                cornerRadius = corner,
            )
        }
    }
}
