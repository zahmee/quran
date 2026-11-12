package com.mushaf.reader.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.share.AyahSpan
import com.mushaf.reader.ui.components.MushafTopBar
import com.mushaf.reader.ui.theme.MushafPalette
import com.mushaf.reader.ui.theme.PageRecolor

/**
 * Picks a run of ayahs on one page for the share image: the page, whole, with the run highlighted
 * and a handle at each end — at the start of its first ayah and the end of its last, where a reader
 * of right-to-left text expects them.
 *
 * A drag anywhere moves whichever handle is nearer, snapping from ayah to ayah; a tap on an ayah
 * reaches out to it. The run stays inside one surah ([AyahSpan]).
 *
 * The page is drawn by [MushafPage] itself in its whole-page fit, so it looks as it does when read.
 * Its own long press is switched off by handing it no ayahs; this overlay owns the touches.
 */
@Composable
internal fun AyahRangePicker(
    model: String,
    recolor: PageRecolor,
    palette: MushafPalette,
    markers: List<AyahMarker>,
    imageWidth: Int,
    imageHeight: Int,
    initial: AyahSpan,
    onDone: (AyahSpan) -> Unit,
    onCancel: () -> Unit,
) {
    var span by remember { mutableStateOf(initial) }
    val surahs = remember(markers) { markers.map { it.surahNumber } }
    BackHandler(onBack = onCancel)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            MushafTopBar(
                title = "اختر الآيات",
                subtitle = spanLabel(markers.subList(span.start, span.end + 1)),
                onBack = onCancel,
                actions = {
                    Button(onClick = { onDone(span) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("تم")
                    }
                },
            )
            Text(
                text = "اسحب أحد الطرفين، أو اضغط آية ليمتد التحديد إليها.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            ) {
                MushafPage(
                    model = model,
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
                        SpanOverlay(markers, surahs, span, scale, onSpan = { span = it })
                    },
                )
            }
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

/** The run's highlight and handles, and the touches that move them. In page-content pixels:
 *  image pixels times [scale]. */
@Composable
private fun SpanOverlay(
    markers: List<AyahMarker>,
    surahs: List<Int>,
    span: AyahSpan,
    scale: Float,
    onSpan: (AyahSpan) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val knob = with(density) { 9.dp.toPx() }
    val stem = with(density) { 2.dp.toPx() }
    val currentSpan by rememberUpdatedState(span)
    val currentOnSpan by rememberUpdatedState(onSpan)

    fun update(next: AyahSpan) {
        if (next == currentSpan) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentOnSpan(next)
    }

    fun indexAt(p: Offset): Int? =
        pickAyah(markers, p.x / scale, p.y / scale)?.let { markers.indexOf(it) }?.takeIf { it >= 0 }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(markers, scale) {
                detectTapGestures(onTap = { p -> indexAt(p)?.let { update(currentSpan.tap(it, surahs)) } })
            }
            .pointerInput(markers, scale) {
                var movingStart = false
                detectDragGestures(
                    onDragStart = { p ->
                        val s = startHandle(markers[currentSpan.start], scale)
                        val e = endHandle(markers[currentSpan.end], scale)
                        movingStart = (p - s).getDistanceSquared() <= (p - e).getDistanceSquared()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val target = indexAt(change.position) ?: return@detectDragGestures
                        val d = currentSpan.drag(movingStart, target, surahs)
                        movingStart = d.movingStart
                        update(d.span)
                    },
                )
            },
    ) {
        val fill = accent.copy(alpha = 0.18f)
        val corner = CornerRadius(8f, 8f)
        for (i in span.start..span.end) {
            markers[i].rects.forEach { r ->
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(r.x * scale, r.y * scale),
                    size = Size(r.w * scale, r.h * scale),
                    cornerRadius = corner,
                )
            }
        }
        // A line that runs to the page's edge would cut its knob in half; the knob steps inside.
        fun knobX(x: Float) = x.coerceIn(knob, size.width - knob)
        markers[span.start].rects.firstOrNull()?.let { r ->
            val x = (r.x + r.w) * scale
            drawLine(accent, Offset(x, r.y * scale), Offset(x, (r.y + r.h) * scale), stem)
            drawCircle(accent, knob, Offset(knobX(x), r.y * scale - knob * 0.7f))
        }
        markers[span.end].rects.lastOrNull()?.let { r ->
            val x = r.x * scale
            drawLine(accent, Offset(x, r.y * scale), Offset(x, (r.y + r.h) * scale), stem)
            drawCircle(accent, knob, Offset(knobX(x), (r.y + r.h) * scale + knob * 0.7f))
        }
    }
}

/** Where the start handle sits: the right end of the ayah's first line (text runs right to left). */
private fun startHandle(m: AyahMarker, scale: Float): Offset =
    m.rects.firstOrNull()?.let { Offset((it.x + it.w) * scale, it.y * scale) }
        ?: Offset(m.centerX * scale, m.centerY * scale)

/** Where the end handle sits: the left end of the ayah's last line. */
private fun endHandle(m: AyahMarker, scale: Float): Offset =
    m.rects.lastOrNull()?.let { Offset(it.x * scale, (it.y + it.h) * scale) }
        ?: Offset(m.centerX * scale, m.centerY * scale)
