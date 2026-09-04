package com.mushaf.reader.reader

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.waterfall
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.ReadingStore

/** What "auto" cannot see, the reader picks by hand. */
private val SMALL = 8.dp
private val MEDIUM = 14.dp
private val LARGE = 22.dp

/**
 * A rounded corner eats a wedge out of anything drawn into it, but Android reports it through
 * neither the status-bar nor the display-cutout inset — the two this app pads against. So a phone
 * with generously curved corners clips exactly what sits in them: the overflow dots in one corner,
 * the settings gear in the other, and the outer sliver of the page.
 *
 * [setting] is one of the ReadingStore.EDGE_MARGIN_* ids and resolves to how far to pull back.
 * "none" is 0.dp — today's layout to the pixel — and is the default, so no existing reader's
 * screen shifts under them.
 */
@Composable
fun curvedEdgeMargin(setting: String): Dp = when (setting) {
    ReadingStore.EDGE_MARGIN_SMALL -> SMALL
    ReadingStore.EDGE_MARGIN_MEDIUM -> MEDIUM
    ReadingStore.EDGE_MARGIN_LARGE -> LARGE
    ReadingStore.EDGE_MARGIN_AUTO -> measuredEdgeMargin()
    else -> 0.dp
}

/**
 * The margin this device's own geometry asks for.
 *
 * Two things are measured. A waterfall edge — a screen that curves down the sides — is a real
 * inset and Compose hands it over directly. A rounded corner is not an inset at all; its radius
 * has to be asked for one corner at a time, and only from Android 12 onwards.
 *
 * A control tucked into a circular corner of radius r first touches the arc at about `0.29 r` in
 * from both edges, so that fraction is what the radius is worth here rather than the whole of it.
 * Anything under 4.dp is treated as a square-cornered screen and left alone; the cap keeps a
 * phone that reports an unusually large radius from eating the page.
 *
 * Below Android 12 the radius is simply unknowable, so "auto" settles on [MEDIUM] — which is why
 * the manual steps exist beside it.
 */
@Composable
private fun measuredEdgeMargin(): Dp {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val waterfall = WindowInsets.waterfall
    val curvedSidePx = maxOf(
        waterfall.getLeft(density, layoutDirection),
        waterfall.getRight(density, layoutDirection),
    )
    val view = LocalView.current

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val curved = with(density) { curvedSidePx.toDp() }
        return if (curved >= 4.dp) curved.coerceAtMost(LARGE) else MEDIUM
    }

    val insets = view.rootWindowInsets ?: return MEDIUM
    val radiusPx = intArrayOf(
        RoundedCorner.POSITION_TOP_LEFT,
        RoundedCorner.POSITION_TOP_RIGHT,
        RoundedCorner.POSITION_BOTTOM_LEFT,
        RoundedCorner.POSITION_BOTTOM_RIGHT,
    ).maxOf { insets.getRoundedCorner(it)?.radius ?: 0 }

    val fromCorners = with(density) { (radiusPx * 0.29f).toDp() }
    val fromCurve = with(density) { curvedSidePx.toDp() }
    val margin = maxOf(fromCorners, fromCurve)
    return if (margin >= 4.dp) margin.coerceAtMost(LARGE) else 0.dp
}
