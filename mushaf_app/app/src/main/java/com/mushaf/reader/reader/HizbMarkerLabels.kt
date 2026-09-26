package com.mushaf.reader.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.HizbMarker
import com.mushaf.reader.ui.theme.MushafPalette
import kotlin.math.roundToInt

/** Measured inside the page, so the label follows its fit, stretch and vertical scroll. */
@Composable
internal fun HizbMarkerLabels(
    markers: List<HizbMarker>,
    baseScale: Float,
    viewportTop: () -> Float,
    viewportHeight: Float,
    allowed: Boolean,
    palette: MushafPalette,
) {
    markers.forEach { marker ->
        key(marker.verseKey) {
            val starTop = marker.bounds.y * baseScale
            val starBottom = (marker.bounds.y + marker.bounds.h) * baseScale
            // In width-fill mode a lower star may enter the viewport long after the page opens.
            // Start its three seconds when it is actually visible, not while it is off screen.
            val inView by remember(marker, baseScale, viewportTop, viewportHeight) {
                derivedStateOf {
                    val top = viewportTop()
                    starTop >= top && starBottom <= top + viewportHeight
                }
            }
            val visible = rememberReadingPositionVisible(marker, allowed && inView)
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(180)),
                    ) {
                        val shape = RoundedCornerShape(8.dp)
                        Column(
                            modifier = Modifier
                                .background(lerp(palette.paper, palette.ink, 0.08f).copy(alpha = 0.97f), shape)
                                .border(0.5.dp, palette.ink.copy(alpha = 0.18f), shape)
                                .semantics(mergeDescendants = true) { }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                marker.division.hizbLabel(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = palette.ink,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "الجزء ${marker.division.juz.toArabicDigits()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.ink.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                },
            ) { measurables, constraints ->
                // AnimatedVisibility emits no child before entering and after its exit.
                val measurable = measurables.singleOrNull()
                    ?: return@Layout layout(constraints.maxWidth, constraints.maxHeight) { }
                val inset = 4.dp.roundToPx().coerceAtMost(constraints.maxWidth / 2)
                val label = measurable.measure(
                    constraints.copy(
                        minWidth = 0, minHeight = 0,
                        maxWidth = minOf(220.dp.roundToPx(), constraints.maxWidth - 2 * inset),
                    )
                )
                val starCenterX = (marker.bounds.x + marker.bounds.w / 2f) * baseScale
                val gap = 4.dp.toPx()
                val below = starBottom + gap
                // The final-line star on page 371 has no room underneath. Keep the label
                // next to its star, above it, rather than clipping it or covering the star.
                val viewportBottom = viewportTop() + viewportHeight
                val y = if (below + label.height <= minOf(constraints.maxHeight.toFloat(), viewportBottom)) {
                    below
                } else {
                    starTop - gap - label.height
                }
                val x = (starCenterX - label.width / 2f).roundToInt()
                    .coerceIn(inset, (constraints.maxWidth - label.width - inset).coerceAtLeast(inset))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // Absolute placement: page-image coordinates must not be mirrored in RTL.
                    label.place(x, y.roundToInt().coerceAtLeast(0))
                }
            }
        }
    }
}
