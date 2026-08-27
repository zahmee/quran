package com.mushaf.reader.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.ui.theme.AyahSelectionColor
import com.mushaf.reader.ui.theme.BookmarkGoldColor
import com.mushaf.reader.ui.theme.MushafPalette
import com.mushaf.reader.ui.theme.BookmarkVioletColor
import com.mushaf.reader.ui.theme.PageRecolor
import kotlin.math.min

/**
 * A single mushaf page: the printed scan recolored for the reading theme, with the ayah highlight
 * overlay on top and a long-press hit test back onto the ayah under the finger.
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
 *
 * [recolor] repaints the printed page for the chosen reading theme — the page assets themselves
 * ship in one light version only. [palette] is that same theme, kept whole because [MoreBelowFade]
 * needs its ink and its light/dark side as well as its paper.
 */
@Composable
fun MushafPage(
    model: String,
    recolor: PageRecolor,
    palette: MushafPalette,
    markers: List<AyahMarker>,
    imageWidth: Int,
    imageHeight: Int,
    selectedAyah: AyahMarker?,
    bookmarkedKeys: Set<String>,
    bookmarkedKeys2: Set<String>,
    onLongPressAyah: (AyahMarker, Offset) -> Unit,
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
                model, recolor, markers, fitScale, wPx, hPx, imgW, imgH,
                bookmarked, bookmarked2, highlight, onLongPressAyah
            )

            widthConstrained -> StretchedPage(
                model, recolor, markers, fitScale, wPx, hPx, imgW, imgH,
                bookmarked, bookmarked2, highlight, onLongPressAyah
            )

            else -> FilledWidthPage(
                model, recolor, palette, markers, wPx, imgW, imgH,
                bookmarked, bookmarked2, highlight, onLongPressAyah
            )
        }
    }
}

/** Default: the whole page fitted and centered (entire page visible). */
@Composable
private fun WholePage(
    model: String,
    recolor: PageRecolor,
    markers: List<AyahMarker>,
    fitScale: Float,
    wPx: Float,
    hPx: Float,
    imgW: Float,
    imgH: Float,
    bookmarked: List<AyahMarker>,
    bookmarked2: List<AyahMarker>,
    highlight: AyahMarker?,
    onLongPressAyah: (AyahMarker, Offset) -> Unit,
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
                        if (markers.isNotEmpty() && fitScale > 0f) {
                            val ix = (tap.x - offsetX) / fitScale
                            val iy = (tap.y - offsetY) / fitScale
                            pickAyah(markers, ix, iy)?.let { onLongPressAyah(it, tap) }
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
            recolor = recolor,
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
    recolor: PageRecolor,
    markers: List<AyahMarker>,
    fitScale: Float,
    wPx: Float,
    hPx: Float,
    imgW: Float,
    imgH: Float,
    bookmarked: List<AyahMarker>,
    bookmarked2: List<AyahMarker>,
    highlight: AyahMarker?,
    onLongPressAyah: (AyahMarker, Offset) -> Unit,
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
                        if (markers.isNotEmpty() && fitScale > 0f) {
                            val ix = (tap.x - offsetX) / fitScale
                            val iy = tap.y / (fitScale * stretch)
                            pickAyah(markers, ix, iy)?.let { onLongPressAyah(it, tap) }
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
            recolor = recolor,
            baseScale = fitScale,
            bookmarked = bookmarked,
            bookmarked2 = bookmarked2,
            highlight = highlight
        )
    }
}

/**
 * Wide screens: fill the screen WIDTH and scroll vertically (starts at the top).
 *
 * This is the ONLY mode that scrolls, and the one place a reader can lose a line of the mushaf: the
 * viewport edge often falls in the generous gap between two of the page's fifteen lines, so a page
 * with more below it looks finished. [MoreBelowFade] is the cue that it is not.
 */
@Composable
private fun FilledWidthPage(
    model: String,
    recolor: PageRecolor,
    palette: MushafPalette,
    markers: List<AyahMarker>,
    wPx: Float,
    imgW: Float,
    imgH: Float,
    bookmarked: List<AyahMarker>,
    bookmarked2: List<AyahMarker>,
    highlight: AyahMarker?,
    onLongPressAyah: (AyahMarker, Offset) -> Unit,
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
                        if (markers.isNotEmpty() && scale > 0f) {
                            val ix = tap.x / scale
                            val iy = (tap.y + scrollState.value) / scale
                            pickAyah(markers, ix, iy)?.let { onLongPressAyah(it, tap) }
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
                recolor = recolor,
                baseScale = scale,
                bookmarked = bookmarked,
                bookmarked2 = bookmarked2,
                highlight = highlight
            )
        }

        // Drawn last, and outside the scrolling Column, so it stays pinned to the screen edge.
        // It has no pointer modifier, so a long press still falls through to the page below it.
        MoreBelowFade(
            palette = palette,
            scrollState = scrollState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** How tall the fade is. A line of the mushaf lands around 90dp on the screens this mode runs on,
 *  so this is about two thirds of a line: enough to read as "continues" without dimming a whole
 *  line of text. */
private val MoreBelowFadeHeight = 64.dp

/**
 * Strongest the band gets at the screen edge, as a fraction of full ink.
 *
 * Two values, not one. On a light theme this is a shadow over white paper and can be firm. On a
 * dark theme the same ramp is a PALE band on a black page — read at night, in the dark, where a
 * bright bar at the bottom of the screen glares. So the dark side is deliberately gentler; against
 * pure black it still separates clearly at this strength.
 */
private const val ScrimMaxLight = 0.60f
private const val ScrimMaxDark = 0.38f

/**
 * The one cue that a scrolled page has more of itself below the screen edge.
 *
 * Bottom edge only, on purpose. Present means "there is more", absent means "you reached the end" —
 * two states, no ambiguity. A matching top fade would only report text already read, at the cost of
 * covering more of the page.
 *
 * The band is drawn in the palette's INK, not its paper. Fading toward paper made the text dissolve
 * into blankness, and blankness is what the bottom of a mushaf page already looks like — so however
 * tall or opaque that version got, its strongest possible state was still "empty paper", and it
 * never read as a mark. Ink is additive: it puts something on the page that was not there. It is
 * also why the color has to follow the theme rather than be a fixed black — see [ScrimMaxLight].
 *
 * It stops short of full ink on purpose. A solid bar would read as a UI panel laid over the mushaf,
 * and would bury the half-cut line completely; a scrim reads as the page continuing under a shade.
 *
 * Every stop is built from the same ink color at differing alpha, never from [Color.Transparent] —
 * transparent is transparent BLACK, and interpolating to it would drag a light-inked theme through
 * a dirty smudge.
 */
@Composable
private fun MoreBelowFade(
    palette: MushafPalette,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    // derivedStateOf so this recomposes when the answer flips, not on every scrolled pixel.
    val moreBelow by remember(scrollState) {
        derivedStateOf { scrollState.value < scrollState.maxValue }
    }
    val alpha by animateFloatAsState(
        targetValue = if (moreBelow) 1f else 0f,
        label = "moreBelowFade",
    )
    if (alpha <= 0f) return

    val ink = palette.ink
    val peak = (if (palette.dark) ScrimMaxDark else ScrimMaxLight) * alpha

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MoreBelowFadeHeight)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to ink.copy(alpha = 0f),
                        // Held low through the first half so the band darkens late and steeply:
                        // that is what makes it read as an edge rather than a general dimming.
                        0.5f to ink.copy(alpha = 0.30f * peak),
                        1f to ink.copy(alpha = peak),
                    )
                )
            }
    )
}

/** Hit-test a pointer position (in image pixels) to an ayah: inside a region, else nearest center. */
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
    recolor: PageRecolor,
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
            colorFilter = recolor.filter,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // Multiplied over the page rather than folded into the filter, so the surah
                    // header's printed green and gold survive the tint. Safe without an offscreen
                    // layer: the page is opaque and covers these exact bounds.
                    if (recolor.multiply != null) {
                        Modifier.drawWithContent {
                            drawContent()
                            drawRect(recolor.multiply, blendMode = BlendMode.Multiply)
                        }
                    } else Modifier
                )
        )

        if (bookmarked.isNotEmpty() || bookmarked2.isNotEmpty() || highlight != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val corner = CornerRadius(8f, 8f)
                val bm = BookmarkGoldColor.copy(alpha = 0.22f)
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
                val bm2 = BookmarkVioletColor.copy(alpha = 0.22f)
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
                    val sel = AyahSelectionColor.copy(alpha = 0.20f)
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
