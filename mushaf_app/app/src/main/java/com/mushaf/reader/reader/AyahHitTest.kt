package com.mushaf.reader.reader

import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.AyahRect

/**
 * Which ayah a press at ([ix], [iy]) — in page-image pixels — means.
 *
 * Inside a highlight band: that ayah (if two bands ever overlapped, the one whose end marker is
 * nearer). Anywhere else: the ayah whose band is NEAREST to the press, ties going to the nearer
 * end marker.
 *
 * Why the nearest band and not the nearest marker: the bands are ~102 px tall on a ~118 px line
 * pitch, so a 16 px strip between every two lines belongs to no ayah, and it is full of vowel marks
 * and letter tails a finger lands on. Picking the nearest MARKER there chose an ayah that was neither
 * the one on the line above nor the one below in 43% of the ink measured in those strips on pages
 * 3-604 (audit docs/audits/ayah-regions-2026-11-05): a marker at the far end of the line is often
 * nearer than the marker of the ayah under the finger. The nearest band is always the line above or
 * the line below.
 */
internal fun pickAyah(markers: List<AyahMarker>, ix: Float, iy: Float): AyahMarker? {
    val inside = markers.filter { m -> m.rects.any { it.contains(ix, iy) } }
    if (inside.isNotEmpty()) return inside.minByOrNull { it.centreDistanceSq(ix, iy) }
    return markers.minWithOrNull(
        compareBy<AyahMarker> { it.bandDistanceSq(ix, iy) }.thenBy { it.centreDistanceSq(ix, iy) }
    )
}

private fun AyahMarker.centreDistanceSq(px: Float, py: Float): Float {
    val dx = centerX - px
    val dy = centerY - py
    return dx * dx + dy * dy
}

/** Squared distance from the point to the nearest of this ayah's bands (its marker if it has none). */
private fun AyahMarker.bandDistanceSq(px: Float, py: Float): Float =
    rects.minOfOrNull { it.distanceSq(px, py) } ?: centreDistanceSq(px, py)

private fun AyahRect.distanceSq(px: Float, py: Float): Float {
    val dx = maxOf(x - px, 0f, px - (x + w))
    val dy = maxOf(y - py, 0f, py - (y + h))
    return dx * dx + dy * dy
}
