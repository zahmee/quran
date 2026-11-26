package com.mushaf.reader.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Surah identity label (number · name · ayah count · progress), tappable to open the index.
 *  Shared by both header layouts. */
@Composable
internal fun SurahChip(
    surah: String,
    surahNumber: Int?,
    ayahCount: Int?,
    surahPercent: Int?,
    showSurahNumber: Boolean,
    showSurahAyahCount: Boolean,
    showSurahProgress: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val surahLabel = buildString {
        if (showSurahNumber && surahNumber != null) append("${surahNumber.toArabicDigits()} ")
        append(surah)
        if (showSurahAyahCount && ayahCount != null && ayahCount > 0) {
            append(" (${ayahCount.toArabicDigits()})")
        }
        if (showSurahProgress && surahPercent != null) append(" ${surahPercent.toArabicDigits()}٪")
    }
    Text(
        text = surahLabel,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 3.dp)
    )
}

/** Two-page mushaf badge with the current page number, tappable to open the page menu.
 *  Shared by both header layouts. */
@Composable
internal fun PageChip(
    page: Int,
    contentColor: Color,
    quietContentColor: Color,
    accentColor: Color,
    big: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        MushafPageBadge(
            page = page,
            // Paper-mushaf order: odd pages sit on the right, even pages on the left.
            onRightPage = page % 2 == 1,
            contentColor = contentColor,
            quietContentColor = quietContentColor,
            accentColor = accentColor,
            big = big
        )
    }
}

/** Juz position label with an optional progress ring, tappable to open the juz index.
 *  Shared by both header layouts. */
@Composable
internal fun JuzChip(
    juz: Int,
    juzPercent: Int,
    pageInJuz: Int,
    pagesInJuz: Int,
    showJuzProgressPercent: Boolean,
    showJuzProgressPages: Boolean,
    color: Color,
    accentColor: Color,
    big: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = buildString {
                append("الجزء ${juz.toArabicDigits()}")
                if (showJuzProgressPercent) append(" ${juzPercent.toArabicDigits()}٪")
            },
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1
        )
        if (showJuzProgressPages) {
            JuzRing(
                fraction = pageInJuz.toFloat() / pagesInJuz.coerceAtLeast(1),
                pageInJuz = pageInJuz,
                trackColor = color,
                accentColor = accentColor,
                big = big
            )
        }
    }
}

/** Compact donut gauge for the current position within the juz: a faint full-circle track
 *  with an accent arc swept by [fraction], drawn right→left over the top to mirror the paper
 *  mushaf's page flow, and the current page-in-juz number centered inside. */
@Composable
private fun JuzRing(
    fraction: Float,
    pageInJuz: Int,
    trackColor: Color,
    accentColor: Color,
    big: Boolean,
) {
    val ringSize = if (big) 24.dp else 21.dp
    // -90f = 12 o'clock start; negative sweep travels right→left over the top as the juz advances.
    // A small floor keeps the very first page reading as "started" rather than an empty ring.
    val sweep = -(fraction.coerceIn(0f, 1f) * 360f).coerceAtLeast(10f)
    Box(
        modifier = Modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 2.2.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor.copy(alpha = 0.20f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = pageInJuz.toArabicDigits(),
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MushafPageBadge(
    page: Int,
    onRightPage: Boolean,
    contentColor: Color,
    quietContentColor: Color,
    accentColor: Color,
    big: Boolean,
) {
    // Each leaf needs enough room for the widest mushaf page number (three digits).
    // Keep the number centred inside its own leaf so it never touches the spine or frame.
    val badgeWidth = if (big) 72.dp else 64.dp
    val badgeHeight = if (big) 36.dp else 31.dp
    val leafWidth = badgeWidth / 2
    val pageTextStyle = if (big) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier.size(width = badgeWidth, height = badgeHeight),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 1.35.dp.toPx()
                val radius = 4.5.dp.toPx()
                val centerX = size.width / 2f
                val pageTop = 1.5.dp.toPx()
                val pageHeight = size.height - pageTop * 2f
                val halfWidth = (size.width - stroke) / 2f
                val activeLeft = if (onRightPage) centerX else stroke / 2f

                drawRoundRect(
                    color = accentColor.copy(alpha = 0.13f),
                    topLeft = Offset(activeLeft, pageTop),
                    size = Size(halfWidth - stroke / 2f, pageHeight),
                    cornerRadius = CornerRadius(radius, radius)
                )
                drawRoundRect(
                    color = quietContentColor.copy(alpha = 0.46f),
                    topLeft = Offset(stroke / 2f, pageTop),
                    size = Size(size.width - stroke, pageHeight),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = stroke)
                )
                drawLine(
                    color = quietContentColor.copy(alpha = 0.62f),
                    start = Offset(centerX, pageTop + 1.dp.toPx()),
                    end = Offset(centerX, size.height - pageTop - 1.dp.toPx()),
                    strokeWidth = stroke
                )
                drawLine(
                    color = quietContentColor.copy(alpha = 0.22f),
                    start = Offset(centerX - 3.dp.toPx(), pageTop + 3.dp.toPx()),
                    end = Offset(centerX - 3.dp.toPx(), size.height - pageTop - 4.dp.toPx()),
                    strokeWidth = stroke / 1.6f
                )
                drawLine(
                    color = quietContentColor.copy(alpha = 0.22f),
                    start = Offset(centerX + 3.dp.toPx(), pageTop + 3.dp.toPx()),
                    end = Offset(centerX + 3.dp.toPx(), size.height - pageTop - 4.dp.toPx()),
                    strokeWidth = stroke / 1.6f
                )
            }
            Box(
                modifier = Modifier
                    .align(if (onRightPage) Alignment.CenterEnd else Alignment.CenterStart)
                    .width(leafWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = page.toArabicDigits(),
                    style = pageTextStyle,
                    color = contentColor,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Floating button shown while the header is hidden (full-screen reading): a small chip hugging the
 *  LEFT screen edge — a grabber notch over the current page number (red). Tap restores the header;
 *  the chip slides UP/DOWN along that edge and keeps the spot the user drags it to — across
 *  rotation, hiding/showing the header, and app restarts (persisted via [posFraction]). */
@Composable
internal fun ShowHeaderButton(
    page: Int,
    showPage: Boolean,
    pageColorId: String,
    opacity: Float,
    posFraction: Float,
    /** Keeps the chip's travel clear of a curved top/bottom corner. 0.dp by default. */
    edgeMargin: Dp,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val numberColor = headerStatusColor(pageColorId, muted) // default "red"
    val buttonAlpha = opacity.coerceIn(0.25f, 1f)

    // Position in LTR so the offset math is plain top-left-origin pixels.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val maxHpx = constraints.maxHeight.toFloat()
            // Drag floor: keep the chip below the camera / status band.
            val topInsetPx = with(density) {
                WindowInsets.statusBars.union(WindowInsets.displayCutout)
                    .asPaddingValues().calculateTopPadding().toPx()
            }
            var btnSize by remember { mutableStateOf(IntSize.Zero) }
            // The chip stays flush to the LEFT edge (x = 0) and slides UP/DOWN only. Its spot comes
            // in as a 0..1 fraction of the travel below the top inset (-1 = untouched -> top), so
            // the saved position lands in the same relative place on any screen size/orientation.
            val edgePx = with(density) { edgeMargin.toPx() }
            val floorPx = topInsetPx + edgePx
            val maxY = (maxHpx - btnSize.height - edgePx).coerceAtLeast(floorPx)
            val travel = maxY - floorPx
            val y = floorPx + posFraction.coerceIn(0f, 1f) * travel
            // Read inside the (long-lived) gesture coroutine so it sees the current values without
            // restarting the gesture — keying pointerInput on them would cancel the drag mid-move.
            val currentTravel by rememberUpdatedState(travel)
            val currentFraction by rememberUpdatedState(posFraction.coerceIn(0f, 1f))
            val currentOnDrag by rememberUpdatedState(onDrag)
            val currentOnDragEnd by rememberUpdatedState(onDragEnd)

            // Plain Surface + Modifier.clickable (NOT Surface(onClick=…)) so it does NOT get wrapped
            // in the 48.dp minimumInteractiveComponentSize, which would center the smaller visible
            // chip inside a 48.dp touch box and push it ~9.dp off the edge. This keeps it 100% flush.
            Surface(
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 11.dp, bottomEnd = 11.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f * buttonAlpha),
                contentColor = onSurface.copy(alpha = buttonAlpha),
                border = BorderStroke(1.dp, onSurface.copy(alpha = 0.12f * buttonAlpha)),
                shadowElevation = 4.dp * buttonAlpha,
                tonalElevation = if (buttonAlpha >= 0.85f) 1.dp else 0.dp,
                modifier = Modifier
                    .absoluteOffset { IntOffset(0, y.roundToInt()) }
                    .onSizeChanged { btnSize = it }
                    .pointerInput(Unit) {
                        // Accumulate the drag here rather than re-reading the fraction per event:
                        // several pointer events can land between recompositions, and reading a
                        // stale value would drop part of the movement.
                        var dragFraction = 0f
                        detectDragGestures(
                            onDragStart = { dragFraction = currentFraction },
                            onDragEnd = { currentOnDragEnd() },
                            onDragCancel = { currentOnDragEnd() },
                        ) { change, drag ->
                            change.consume()
                            if (currentTravel > 0f) {
                                dragFraction = (dragFraction + drag.y / currentTravel).coerceIn(0f, 1f)
                                currentOnDrag(dragFraction)
                            }
                        }
                    }
                    .clickable(onClick = onClick)
                    .defaultMinSize(minWidth = 30.dp, minHeight = 30.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .width(IntrinsicSize.Min) // hug the page number so the chip stays compact
                        .padding(start = 5.dp, end = 7.dp, top = 3.dp, bottom = 4.dp)
                ) {
                    // Small grabber notch — the "drag me up/down" cue.
                    Box(
                        modifier = Modifier
                            .size(width = 11.dp, height = 2.5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(onSurface.copy(alpha = 0.5f * buttonAlpha))
                    )
                    if (showPage) {
                        Text(
                            text = page.toArabicDigits(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = numberColor.copy(alpha = buttonAlpha)
                        )
                    }
                }
            }
        }
    }
}
