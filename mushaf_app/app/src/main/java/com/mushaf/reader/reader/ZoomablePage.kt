package com.mushaf.reader.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import com.mushaf.reader.data.AyahMarker
import kotlin.math.min

private val BookmarkColor = Color(0xFFD4A017) // amber: persistent bookmark #1 highlight
private val BookmarkColor2 = Color(0xFF7E57C2) // violet: persistent bookmark #2 highlight
private val SelectionColor = Color(0xFF1F7A5A) // green: transient long-press selection

/**
 * A single mushaf page.
 *
 * Default ([fillScreen] = false): the WHOLE page is fitted to the screen and centered, so the
 * entire page is always visible (with a margin on whichever side the screen aspect leaves spare).
 *
 * [fillScreen] = true makes the page fill the screen by closing that margin:
 * - Wide / near-square screens (the unfolded foldable, landscape) — the page fills the WIDTH and
 *   scrolls vertically. No distortion.
 * - Tall / narrow screens (phone portrait, the folded cover) — the page is STRETCHED vertically
 *   (height only) to fill top-to-bottom; the full width stays visible (no side cropping), the
 *   glyphs just get a little taller.
 *
 * Bookmarked ayahs stay highlighted (amber); a long-pressed ayah is highlighted (green).
 */
@Composable
fun ZoomablePage(
    model: String,
    markers: List<AyahMarker>,
    imageWidth: Int,
    imageHeight: Int,
    selectedAyah: AyahMarker?,
    bookmarkedKeys: Set<String>,
    bookmarkedKeys2: Set<String>,
    onSelectAyah: (AyahMarker, Offset) -> Unit,
    onEmptyTap: () -> Unit,
    fillScreen: Boolean = false,
) {
    val highlight = selectedAyah?.takeIf { markers.contains(it) }
    val bookmarked = remember(markers, bookmarkedKeys) {
        if (bookmarkedKeys.isEmpty()) emptyList()
        else markers.filter { bookmarkedKeys.contains(it.verseKey) }
    }
    val bookmarked2 = remember(markers, bookmarkedKeys2) {
        if (bookmarkedKeys2.isEmpty()) emptyList()
        else markers.filter { bookmarkedKeys2.contains(it.verseKey) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val imgW = imageWidth.toFloat()
        val imgH = imageHeight.toFloat()

        // Whole-page fit, and whether that fit fills the width (→ spare space is top/bottom).
        val fitScale = min(wPx / imgW, hPx / imgH)
        val widthConstrained = wPx / imgW <= hPx / imgH

        when {
            !fillScreen -> WholePage(
                model, markers, fitScale, wPx, hPx, imgW, imgH,
                bookmarked, bookmarked2, highlight, onSelectAyah, onEmptyTap
            )

            widthConstrained -> StretchedPage(
                model, markers, fitScale, wPx, hPx, imgW, imgH,
                bookmarked, bookmarked2, highlight, onSelectAyah, onEmptyTap
            )

            else -> FilledWidthPage(
                model, markers, wPx, imgW, imgH,
                bookmarked, bookmarked2, highlight, onSelectAyah, onEmptyTap
            )
        }
    }
}

/** Default: the whole page fitted and centered (entire page visible). */
@Composable
private fun WholePage(
    model: String,
    markers: List<AyahMarker>,
    fitScale: Float,
    wPx: Float,
    hPx: Float,
    imgW: Float,
    imgH: Float,
    bookmarked: List<AyahMarker>,
    bookmarked2: List<AyahMarker>,
    highlight: AyahMarker?,
    onSelectAyah: (AyahMarker, Offset) -> Unit,
    onEmptyTap: () -> Unit,
) {
    val density = LocalDensity.current
    val contentW = imgW * fitScale
    val contentH = imgH * fitScale
    val offsetX = (wPx - contentW) / 2f
    val offsetY = (hPx - contentH) / 2f
    val contentWDp = with(density) { contentW.toDp() }
    val contentHDp = with(density) { contentH.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(model, markers, fitScale, offsetX, offsetY) {
                detectTapGestures(
                    onLongPress = { tap ->
                        if (markers.isEmpty() || fitScale <= 0f) {
                            onEmptyTap()
                        } else {
                            val ix = (tap.x - offsetX) / fitScale
                            val iy = (tap.y - offsetY) / fitScale
                            pickAyah(markers, ix, iy)?.let { onSelectAyah(it, tap) } ?: onEmptyTap()
                        }
                    }
                )
            }
    ) {
        PageContent(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(contentWDp, contentHDp),
            model = model,
            baseScale = fitScale,
            bookmarked = bookmarked,
            bookmarked2 = bookmarked2,
            highlight = highlight
        )
    }
}

/** Tall screens: stretch the height (only) to fill top-to-bottom; full width stays visible. */
@Composable
private fun StretchedPage(
    model: String,
    markers: List<AyahMarker>,
    fitScale: Float,
    wPx: Float,
    hPx: Float,
    imgW: Float,
    imgH: Float,
    bookmarked: List<AyahMarker>,
    bookmarked2: List<AyahMarker>,
    highlight: AyahMarker?,
    onSelectAyah: (AyahMarker, Offset) -> Unit,
    onEmptyTap: () -> Unit,
) {
    val density = LocalDensity.current
    val contentW = imgW * fitScale
    val contentH = imgH * fitScale
    val offsetX = (wPx - contentW) / 2f
    val stretch = if (contentH > 0f) (hPx / contentH).coerceAtLeast(1f) else 1f
    val contentWDp = with(density) { contentW.toDp() }
    val contentHDp = with(density) { contentH.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(model, markers, fitScale, offsetX, stretch) {
                detectTapGestures(
                    onLongPress = { tap ->
                        if (markers.isEmpty() || fitScale <= 0f) {
                            onEmptyTap()
                        } else {
                            val ix = (tap.x - offsetX) / fitScale
                            val iy = tap.y / (fitScale * stretch)
                            pickAyah(markers, ix, iy)?.let { onSelectAyah(it, tap) } ?: onEmptyTap()
                        }
                    }
                )
            }
    ) {
        PageContent(
            modifier = Modifier
                .requiredSize(contentWDp, contentHDp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = offsetX
                    translationY = 0f
                    scaleX = 1f
                    scaleY = stretch
                },
            model = model,
            baseScale = fitScale,
            bookmarked = bookmarked,
            bookmarked2 = bookmarked2,
            highlight = highlight
        )
    }
}

/** Wide screens: fill the screen WIDTH and scroll vertically (starts at the top). */
@Composable
private fun FilledWidthPage(
    model: String,
    markers: List<AyahMarker>,
    wPx: Float,
    imgW: Float,
    imgH: Float,
    bookmarked: List<AyahMarker>,
    bookmarked2: List<AyahMarker>,
    highlight: AyahMarker?,
    onSelectAyah: (AyahMarker, Offset) -> Unit,
    onEmptyTap: () -> Unit,
) {
    val density = LocalDensity.current
    val scale = wPx / imgW
    val contentW = imgW * scale
    val contentH = imgH * scale
    val contentWDp = with(density) { contentW.toDp() }
    val contentHDp = with(density) { contentH.toDp() }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(model, markers, scale) {
                detectTapGestures(
                    onLongPress = { tap ->
                        if (markers.isEmpty() || scale <= 0f) {
                            onEmptyTap()
                        } else {
                            val ix = tap.x / scale
                            val iy = (tap.y + scrollState.value) / scale
                            pickAyah(markers, ix, iy)?.let { onSelectAyah(it, tap) } ?: onEmptyTap()
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PageContent(
                modifier = Modifier.requiredSize(contentWDp, contentHDp),
                model = model,
                baseScale = scale,
                bookmarked = bookmarked,
                bookmarked2 = bookmarked2,
                highlight = highlight
            )
        }
    }
}

/** Hit-test a tap (in image pixels) to an ayah: inside a region, else the nearest center. */
private fun pickAyah(markers: List<AyahMarker>, ix: Float, iy: Float): AyahMarker? {
    val inside = markers.filter { m -> m.rects.any { it.contains(ix, iy) } }
    return (inside.ifEmpty { markers }).minByOrNull { m ->
        val dx = m.centerX - ix
        val dy = m.centerY - iy
        dx * dx + dy * dy
    }
}

@Composable
private fun PageContent(
    modifier: Modifier,
    model: String,
    baseScale: Float,
    bookmarked: List<AyahMarker>,
    bookmarked2: List<AyahMarker>,
    highlight: AyahMarker?,
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        if (bookmarked.isNotEmpty() || bookmarked2.isNotEmpty() || highlight != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val corner = CornerRadius(8f, 8f)
                val bm = BookmarkColor.copy(alpha = 0.22f)
                bookmarked.forEach { m ->
                    m.rects.forEach { r ->
                        drawRoundRect(
                            color = bm,
                            topLeft = Offset(r.x * baseScale, r.y * baseScale),
                            size = Size(r.w * baseScale, r.h * baseScale),
                            cornerRadius = corner
                        )
                    }
                }
                val bm2 = BookmarkColor2.copy(alpha = 0.22f)
                bookmarked2.forEach { m ->
                    m.rects.forEach { r ->
                        drawRoundRect(
                            color = bm2,
                            topLeft = Offset(r.x * baseScale, r.y * baseScale),
                            size = Size(r.w * baseScale, r.h * baseScale),
                            cornerRadius = corner
                        )
                    }
                }
                if (highlight != null) {
                    val sel = SelectionColor.copy(alpha = 0.20f)
                    highlight.rects.forEach { r ->
                        drawRoundRect(
                            color = sel,
                            topLeft = Offset(r.x * baseScale, r.y * baseScale),
                            size = Size(r.w * baseScale, r.h * baseScale),
                            cornerRadius = corner
                        )
                    }
                }
            }
        }
    }
}
