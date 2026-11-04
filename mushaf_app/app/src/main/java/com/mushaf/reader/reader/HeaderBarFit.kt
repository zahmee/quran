package com.mushaf.reader.reader

import kotlin.math.floor

/**
 * The actions that keep their seat when the bar has fewer slots than the reader asked for.
 *
 * Fill-screen and hide-header are the controls that change how the page itself is shown, and they
 * were bar buttons before the placement setting existed; the gold bookmark is the one jump a reader
 * reaches for from the page. Everything else gives way first, in [HeaderActions] order.
 *
 * Kept apart from [HeaderActions] (which builds icons) so the fitting rule can be unit-tested.
 */
internal val BarKeepFirst = listOf("fill", "hide", "bookmark")

/**
 * How many bar slots of [slot] width fit in [room]. Both are in the same unit (dp values in the
 * header); a sliver of rounding slack keeps an exact fit from losing its last slot to float noise.
 */
internal fun barSlots(room: Float, slot: Float): Int {
    if (slot <= 0f || room <= 0f) return 0
    return floor((room + 0.01f) / slot).toInt()
}

/**
 * Which of the [requested] bar actions (ids, in display order) are drawn on a bar with [slots] free
 * slots. When they do not all fit, [BarKeepFirst] keeps its members first and the rest fill what is
 * left in display order; the result keeps display order either way. Whatever is not returned is
 * listed in the More menu instead.
 */
internal fun fitBarActions(requested: List<String>, slots: Int): Set<String> {
    if (slots <= 0) return emptySet()
    if (requested.size <= slots) return requested.toCollection(LinkedHashSet())
    // sortedBy is stable, so actions outside BarKeepFirst keep their display order among themselves.
    val kept = requested
        .sortedBy { id -> BarKeepFirst.indexOf(id).let { if (it < 0) BarKeepFirst.size else it } }
        .take(slots)
        .toSet()
    return requested.filterTo(LinkedHashSet()) { it in kept }
}
