package com.mushaf.reader.reader

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
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
 * What the recitation panel shows: what is being recited, then three small chips — the reciter
 * (opens the list), the speed (a menu) and repetition (opens the memorisation sheet; during a run
 * it shows which repetition this is) — and previous/next ayah, play/pause and close. In this
 * right-to-left screen the text sits on the right, "previous" points right and "next" left.
 */
@Composable
private fun RecitationControls(
    surahName: String,
    ayah: Int?,
    reciterName: String?,
    isPlaying: Boolean,
    buffering: Boolean,
    error: String?,
    speed: Float,
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
        Row(
            modifier = modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
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

/** How long the panel stays open after it opened or was last touched. */
private const val AUTO_COLLAPSE_MS = 5_000L

/**
 * Recitation as a drawer on the screen's right edge, floating over the page so the page never
 * resizes for it. Folded, it is one small handle — see-through while reciting, showing whether it
 * plays — that can be dragged up or down the edge, away from the line being read; tapping it slides
 * the controls out to the left, tapping again folds them back. It opens by itself when a
 * recitation starts or something goes wrong, and folds itself after a few untouched seconds. The
 * caller shows it only while a recitation is loaded.
 */
@Composable
internal fun FloatingRecitationBar(
    surahName: String,
    ayah: Int?,
    reciterName: String?,
    isPlaying: Boolean,
    buffering: Boolean,
    error: String?,
    speed: Float,
    repeatLabel: String?,
    /** Changes each time a recitation is started, to open the panel for it. */
    startKey: Int,
    /** Place along the edge: 0 top .. 1 bottom. */
    handlePos: Float,
    onHandleDrag: (Float) -> Unit,
    onHandleDragEnd: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onPickReciter: () -> Unit,
    onSpeed: (Float) -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(startKey) {
        expanded = true
        touches++
    }
    LaunchedEffect(error) { if (error != null) expanded = true }
    LaunchedEffect(expanded, touches, error) {
        if (expanded && error == null) {
            delay(AUTO_COLLAPSE_MS)
            expanded = false
        }
    }
    fun touched(action: () -> Unit): () -> Unit = {
        touches++
        action()
    }
    val folded = !expanded && error == null
    val alpha by animateFloatAsState(if (folded) 0.55f else 1f, label = "recitation handle alpha")
    val currentPos by rememberUpdatedState(handlePos)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        var barHeightPx by remember { mutableIntStateOf(with(density) { HANDLE_SIZE.roundToPx() }) }
        val travel = (constraints.maxHeight - barHeightPx).coerceAtLeast(1).toFloat()
        val controlsWidth = (minOf(maxWidth - 8.dp, 440.dp) - HANDLE_SIZE).coerceAtLeast(0.dp)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopRight) {
            Surface(
                modifier = Modifier
                    .offset { IntOffset(0, (handlePos.coerceIn(0f, 1f) * travel).roundToInt()) }
                    .onSizeChanged { barHeightPx = it.height }
                    .alpha(alpha),
                // Docked to the right edge: rounded on the inner side only.
                shape = AbsoluteRoundedCornerShape(topLeft = 22.dp, bottomLeft = 22.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 6.dp,
            ) {
                // In this right-to-left row the handle comes first, so it sits against the edge
                // and the controls slide out to its left.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(HANDLE_SIZE)
                            .semantics {
                                contentDescription = if (expanded) "طيّ شريط التلاوة" else "فتح شريط التلاوة"
                            }
                            .pointerInput(travel) {
                                detectVerticalDragGestures(onDragEnd = onHandleDragEnd) { change, dy ->
                                    change.consume()
                                    onHandleDrag(currentPos + dy / travel)
                                }
                            }
                            .clickable(
                                onClickLabel = if (expanded) "طيّ شريط التلاوة" else "فتح شريط التلاوة",
                            ) {
                                expanded = !expanded
                                touches++
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        HandleIcon(isPlaying = isPlaying, buffering = buffering, error = error)
                    }
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                    ) {
                        RecitationControls(
                            surahName = surahName,
                            ayah = ayah,
                            reciterName = reciterName,
                            isPlaying = isPlaying,
                            buffering = buffering,
                            error = error,
                            speed = speed,
                            repeatLabel = repeatLabel,
                            onToggle = touched(onToggle),
                            onPrevious = touched(onPrevious),
                            onNext = touched(onNext),
                            onClose = onClose,
                            onPickReciter = touched(onPickReciter),
                            onSpeed = { touches++; onSpeed(it) },
                            onRepeat = touched(onRepeat),
                            modifier = Modifier.width(controlsWidth),
                        )
                    }
                }
            }
        }
    }
}

private val HANDLE_SIZE = 44.dp

/** The folded handle's face: what the recitation is doing, at a glance. */
@Composable
private fun HandleIcon(isPlaying: Boolean, buffering: Boolean, error: String?) {
    when {
        error != null -> Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        buffering -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        isPlaying -> {
            val pulse by rememberInfiniteTransition(label = "recitation pulse").animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "recitation pulse alpha",
            )
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(pulse),
            )
        }
        else -> Icon(
            Icons.Filled.Headphones,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

/** Arabic counted noun: آية واحدة، آيتان، 3–10 آيات، 11+ آية. Shared with the search screen. */
internal fun ayahsLabel(n: Int): String = when {
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
