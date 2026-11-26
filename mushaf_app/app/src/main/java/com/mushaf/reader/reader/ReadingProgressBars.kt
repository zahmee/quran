package com.mushaf.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/** A thin juz-progress bar pinned to the bottom of the page (optional, off by default).
 *  Fills from the right (mushaf reading direction) by the page's position within its juz.
 *  [thickness] is the user-chosen bar height, [opacity] how solid it is drawn (0..1). */
@Composable
internal fun BottomJuzBar(
    modifier: Modifier,
    fraction: Float,
    colorId: String,
    thickness: Dp,
    opacity: Float,
) {
    val barColor = headerStatusColor(colorId, MaterialTheme.colorScheme.onSurfaceVariant)
    // Wrap in LTR so CenterEnd reliably means the right edge regardless of the screen's RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(thickness)
                .background(barColor.copy(alpha = TRACK_ALPHA * opacity)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(barColor.copy(alpha = opacity))
            )
        }
    }
}

/** A thin surah-progress bar laid out directly above the mushaf page (optional, off by default).
 *  Fills from the right, like [BottomJuzBar], by the page's position within the current surah.
 *  Unlike the juz bar it is part of the column rather than an overlay, so it never covers the page
 *  text. It intentionally does not pad for the top inset: in full-screen it hugs the very top edge
 *  and ignores the camera cutout, so the page fills the screen with no gap above the bar. */
@Composable
internal fun TopSurahBar(
    fraction: Float,
    colorId: String,
    thickness: Dp,
    opacity: Float,
) {
    val barColor = headerStatusColor(colorId, MaterialTheme.colorScheme.onSurfaceVariant)
    // Wrap in LTR so CenterEnd reliably means the right edge regardless of the screen's RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thickness)
                .background(barColor.copy(alpha = TRACK_ALPHA * opacity)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(barColor.copy(alpha = opacity))
            )
        }
    }
}

/** The unread part of a progress bar is a faint wash of the same color; the user's opacity setting
 *  scales this too, so dialing a bar down fades the whole thing rather than just its filled part. */
private const val TRACK_ALPHA = 0.18f

/** Full-screen-only edge marker for the current page's side of the spread: a small rounded bar
 *  flush to the RIGHT edge for a right-side (odd) page, and to the LEFT edge for a left-side
 *  (even) page — matching the MushafPageBadge parity. Vertically centered. [thickness] is the bar's
 *  width, [length] its height, [opacity] how solid it is drawn (0..1); only the two corners facing
 *  away from the screen edge are rounded. */
@Composable
internal fun PageSideIndicator(
    page: Int,
    colorId: String,
    thickness: Dp,
    length: Dp,
    opacity: Float,
) {
    val color = headerStatusColor(colorId, MaterialTheme.colorScheme.onSurfaceVariant)
    val onRight = page % 2 == 1 // odd pages sit on the right of the paper mushaf
    val radius = thickness / 2 // a pill cap at any thickness
    // Wrap in LTR so CenterStart/CenterEnd mean plain left/right regardless of the reader's RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (onRight) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(width = thickness, height = length)
                    .clip(
                        if (onRight) RoundedCornerShape(topStart = radius, bottomStart = radius)
                        else RoundedCornerShape(topEnd = radius, bottomEnd = radius)
                    )
                    .background(color.copy(alpha = opacity))
            )
        }
    }
}
