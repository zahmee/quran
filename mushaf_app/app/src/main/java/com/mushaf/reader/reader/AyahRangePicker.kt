package com.mushaf.reader.reader

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.share.AyahSpan
import com.mushaf.reader.share.SpanRules
import com.mushaf.reader.ui.components.MushafTopBar
import com.mushaf.reader.ui.theme.MushafPalette
import com.mushaf.reader.ui.theme.PageRecolor
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** Pages a shared run of ayahs may cover; see [SpanRules.maxPages]. */
internal const val MAX_SPAN_PAGES = 3

/**
 * Picks a run of ayahs for the share image: the mushaf, turned like the reader, with the run
 * highlighted and a handle at each end — at the start of its first ayah and the end of its last,
 * where a reader of right-to-left text expects them.
 *
 * Drag a handle to move it ayah by ayah; tap an ayah to reach out to it — on another page too,
 * which is how a run goes over a page break. A swipe anywhere but on a handle turns the page.
 * The run stays inside one surah and within [MAX_SPAN_PAGES] pages ([SpanRules]).
 *
 * Each page is drawn by [MushafPage] itself in its whole-page fit, so it looks as it does when read.
 * Its own long press is switched off by handing it no ayahs; the overlay here owns the touches.
 */
@Composable
internal fun AyahRangePicker(
    pageCount: Int,
    model: (Int) -> String,
    markersFor: (Int) -> List<AyahMarker>,
    recolor: PageRecolor,
    palette: MushafPalette,
    imageWidth: Int,
    imageHeight: Int,
    initial: List<AyahMarker>,
    onDone: (List<AyahMarker>) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    // Every ayah of the mushaf in reading order: the indices a span counts in.
    val all = remember { (1..pageCount).flatMap(markersFor) }
    val indexOf = remember(all) { all.withIndex().associate { (i, m) -> m.verseKey to i } }
    val rules = remember(all) {
        SpanRules(all.map { it.surahNumber }, all.map { it.page }, MAX_SPAN_PAGES)
    }
    var span by remember {
        mutableStateOf(
            AyahSpan(indexOf.getValue(initial.first().verseKey), indexOf.getValue(initial.last().verseKey))
        )
    }
    val pagerState = rememberPagerState(initialPage = initial.first().page - 1, pageCount = { pageCount })
    val coroutines = rememberCoroutineScope()
    BackHandler(onBack = onCancel)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            MushafTopBar(
                title = "اختر الآيات",
                subtitle = spanLabel(all.subList(span.start, span.end + 1)),
                onBack = onCancel,
                actions = {
                    Button(
                        onClick = { onDone(all.subList(span.start, span.end + 1).toList()) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("تم") }
                },
            )
            Text(
                text = "اسحب أحد الطرفين، أو اضغط آية ليمتد التحديد إليها — ولو في صفحة أخرى.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                beyondViewportPageCount = 1,
            ) { index ->
                val page = index + 1
                val local = remember(page) { markersFor(page) }
                val offset = local.firstOrNull()?.let { indexOf[it.verseKey] } ?: 0
                MushafPage(
                    model = model(page),
                    recolor = recolor,
                    palette = palette,
                    markers = emptyList(),
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    selectedAyah = null,
                    bookmarkedKeys = emptySet(),
                    bookmarkedKeys2 = emptySet(),
                    onLongPressAyah = { _, _, _ -> },
                    fillScreen = false,
                    pageOverlay = { scale, _, _ ->
                        if (local.isNotEmpty()) {
                            SpanOverlay(
                                local = local,
                                offset = offset,
                                rules = rules,
                                span = span,
                                scale = scale,
                                onSpan = { span = it },
                                onTooFar = {
                                    Toast.makeText(
                                        context,
                                        "يمتد التحديد إلى ثلاث صفحات على الأكثر",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        }
                    },
                )
            }
            PageTurnBar(
                page = pagerState.currentPage + 1,
                pageCount = pageCount,
                onTurn = { delta ->
                    coroutines.launch { pagerState.animateScrollToPage(pagerState.currentPage + delta) }
                },
            )
        }
    }
}

/** The page number between buttons that turn back and forth — a swipe does it too, but this one
 *  says so. Right to left: the previous page is to the right, the next to the left. */
@Composable
private fun PageTurnBar(page: Int, pageCount: Int, onTurn: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onTurn(-1) }, enabled = page > 1) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "الصفحة السابقة")
        }
        Text(
            text = "صفحة ${page.toArabicDigits()}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = { onTurn(1) }, enabled = page < pageCount) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "الصفحة التالية")
        }
    }
}

/** "البقرة • الآية ٢٥٥" for one ayah, "البقرة • الآيات ٢٨٥–٢٨٦" for several. */
internal fun spanLabel(ayahs: List<AyahMarker>): String {
    val a = ayahs.first()
    val z = ayahs.last()
    return if (a.verseKey == z.verseKey) "${a.surahNameAr} • الآية ${a.ayahNumber.toArabicDigits()}"
    else "${a.surahNameAr} • الآيات ${a.ayahNumber.toArabicDigits()}–${z.ayahNumber.toArabicDigits()}"
}

/**
 * One page's share of the run — its highlight, and a handle for each end of the run that falls on
 * this page — and the touches that move them. In page-content pixels: image pixels times [scale].
 *
 * [local] are this page's ayahs; the first of them is ayah [offset] of the whole run's context.
 */
@Composable
private fun SpanOverlay(
    local: List<AyahMarker>,
    offset: Int,
    rules: SpanRules,
    span: AyahSpan,
    scale: Float,
    onSpan: (AyahSpan) -> Unit,
    onTooFar: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val knob = with(density) { 9.dp.toPx() }
    val stem = with(density) { 2.dp.toPx() }
    val currentSpan by rememberUpdatedState(span)
    val currentOnSpan by rememberUpdatedState(onSpan)
    val currentOnTooFar by rememberUpdatedState(onTooFar)
    val here = offset until offset + local.size

    fun update(next: AyahSpan) {
        if (next == currentSpan) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentOnSpan(next)
    }

    fun indexAt(p: Offset): Int? =
        pickAyah(local, p.x / scale, p.y / scale)?.let { local.indexOf(it) }?.takeIf { it >= 0 }?.plus(offset)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(local, offset, scale) {
                detectTapGestures(onTap = { p ->
                    val t = indexAt(p) ?: return@detectTapGestures
                    val next = currentSpan.tap(t, rules)
                    if (next == null) currentOnTooFar() else update(next)
                })
            }
            // Inner to the tap detector, so it sees a touch first and can claim it. It claims only a
            // touch on a handle: anything else is left to the tap above and the pager's swipe.
            .pointerInput(local, offset, scale) {
                val grab = 36.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val s = currentSpan
                    val start = if (s.start in here) startHandle(local[s.start - offset], scale, knob, size.width.toFloat()) else null
                    val end = if (s.end in here) endHandle(local[s.end - offset], scale, knob, size.width.toFloat()) else null
                    val toStart = start?.distanceTo(down.position) ?: Float.MAX_VALUE
                    val toEnd = end?.distanceTo(down.position) ?: Float.MAX_VALUE
                    if (minOf(toStart, toEnd) > grab) return@awaitEachGesture

                    var movingStart = toStart <= toEnd
                    val handle = if (movingStart) start!! else end!!
                    // The finger sits on the knob, off the line; read the ayah where the handle's
                    // own point is, carried along by the drag.
                    val lead = handle.point - down.position
                    down.consume()
                    drag(down.id) { change ->
                        change.consume()
                        val t = indexAt(change.position + lead) ?: return@drag
                        val d = currentSpan.drag(movingStart, t, rules)
                        movingStart = d.movingStart
                        update(d.span)
                    }
                }
            },
    ) {
        val fill = accent.copy(alpha = 0.18f)
        val corner = CornerRadius(8f, 8f)
        for (i in maxOf(span.start, here.first)..minOf(span.end, here.last)) {
            local[i - offset].rects.forEach { r ->
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(r.x * scale, r.y * scale),
                    size = Size(r.w * scale, r.h * scale),
                    cornerRadius = corner,
                )
            }
        }
        if (span.start in here) {
            val h = startHandle(local[span.start - offset], scale, knob, size.width)
            drawLine(accent, Offset(h.x, h.top), Offset(h.x, h.bottom), stem)
            drawCircle(accent, knob, h.knob)
        }
        if (span.end in here) {
            val h = endHandle(local[span.end - offset], scale, knob, size.width)
            drawLine(accent, Offset(h.x, h.top), Offset(h.x, h.bottom), stem)
            drawCircle(accent, knob, h.knob)
        }
    }
}

/**
 * A handle on screen: its stem from [top] to [bottom] at [x], its round [knob], and [point], the
 * spot just inside the ayah that says which ayah the handle is on.
 */
private class Handle(val x: Float, val top: Float, val bottom: Float, val knob: Offset, val point: Offset) {
    /** Distance from [p] to the knob or the stem, whichever is nearer. */
    fun distanceTo(p: Offset): Float {
        val toKnob = (p - knob).getDistance()
        val dy = when {
            p.y < top -> top - p.y
            p.y > bottom -> p.y - bottom
            else -> 0f
        }
        return minOf(toKnob, hypot(p.x - x, dy))
    }
}

/** Where the start handle sits: the right end of the ayah's first line (text runs right to left),
 *  knob above. A line that runs to the page's edge would cut the knob in half; it steps inside. */
private fun startHandle(m: AyahMarker, scale: Float, knob: Float, width: Float): Handle {
    val r = m.rects.firstOrNull()
        ?: return centreHandle(m, scale, knob)
    val x = (r.x + r.w) * scale
    val top = r.y * scale
    val bottom = (r.y + r.h) * scale
    return Handle(
        x, top, bottom,
        knob = Offset(x.coerceIn(knob, width - knob), top - knob * 0.7f),
        point = Offset(x - 3f, (top + bottom) / 2),
    )
}

/** Where the end handle sits: the left end of the ayah's last line, knob below. */
private fun endHandle(m: AyahMarker, scale: Float, knob: Float, width: Float): Handle {
    val r = m.rects.lastOrNull()
        ?: return centreHandle(m, scale, knob)
    val x = r.x * scale
    val top = r.y * scale
    val bottom = (r.y + r.h) * scale
    return Handle(
        x, top, bottom,
        knob = Offset(x.coerceIn(knob, width - knob), bottom + knob * 0.7f),
        point = Offset(x + 3f, (top + bottom) / 2),
    )
}

/** An ayah without rectangles: both handles on its marker. */
private fun centreHandle(m: AyahMarker, scale: Float, knob: Float): Handle {
    val c = Offset(m.centerX * scale, m.centerY * scale)
    return Handle(c.x, c.y - knob, c.y + knob, c, c)
}
