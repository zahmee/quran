package com.mushaf.reader.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.AyahMarker
import kotlinx.coroutines.delay

/**
 * Orange: unlike the green of a long-pressed ayah, the blue of the one being recited and the gold and
 * violet of the bookmarks, so a search result is never mistaken for any of them.
 */
private val SearchFlashColor = Color(0xFFF4511E)

/** How long the result stays lit after its flashes, before it fades. */
private const val SEARCH_FLASH_HOLD_MS = 5_000L

/**
 * The ayah a search result opened, lit over its lines: it flashes twice so the eye finds it, stays
 * lit a few seconds, then fades and calls [onDone]. Only the whole ayah can be lit — the page data
 * has the ayahs' lines, not each word's place.
 */
@Composable
internal fun SearchFlashHighlight(marker: AyahMarker, baseScale: Float, token: Long, onDone: () -> Unit) {
    val level = remember(token) { Animatable(0f) }
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(token) {
        repeat(2) {
            level.animateTo(1f, tween(durationMillis = 260))
            level.animateTo(0.3f, tween(durationMillis = 320))
        }
        level.animateTo(1f, tween(durationMillis = 260))
        delay(SEARCH_FLASH_HOLD_MS)
        level.animateTo(0f, tween(durationMillis = 900))
        currentOnDone()
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val a = level.value
        if (a <= 0f) return@Canvas
        val corner = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        marker.rects.forEach { r ->
            drawRoundRect(
                color = SearchFlashColor.copy(alpha = 0.32f * a),
                topLeft = Offset(r.x * baseScale, r.y * baseScale),
                size = Size(r.w * baseScale, r.h * baseScale),
                cornerRadius = corner,
            )
        }
    }
}
