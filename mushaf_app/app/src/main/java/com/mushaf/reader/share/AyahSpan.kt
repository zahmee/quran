package com.mushaf.reader.share

/**
 * A run of consecutive ayahs on one page: [start] to [end] inclusive, as indices into the page's
 * ayahs in reading order. It never crosses into another surah — a cut spanning two would carry the
 * printed surah header between them, which belongs to neither.
 *
 * Pure Kotlin: [surahs] is the surah number of each of the page's ayahs, in the same order.
 */
internal data class AyahSpan(val start: Int, val end: Int) {

    init {
        require(start <= end) { "start $start after end $end" }
    }

    /** Result of dragging one end: the new span, and whether the end under the finger is now its
     *  start — dragging past the other end hands the finger over to the opposite end. */
    data class Drag(val span: AyahSpan, val movingStart: Boolean)

    /** Drags the start (or the end) to [target]; the other end stays put as the anchor. */
    fun drag(movingStart: Boolean, target: Int, surahs: List<Int>): Drag {
        val anchor = if (movingStart) end else start
        val t = clampToSurahOf(anchor, target, surahs)
        return if (t <= anchor && (movingStart || t < anchor)) {
            Drag(AyahSpan(t, anchor), movingStart = true)
        } else {
            Drag(AyahSpan(anchor, t), movingStart = false)
        }
    }

    /**
     * A tap on the ayah at [target]: before the span extends its start to it, after it extends its
     * end, inside it pulls the end back to it. An ayah of another surah starts a new span there.
     */
    fun tap(target: Int, surahs: List<Int>): AyahSpan = when {
        surahs[target] != surahs[start] -> AyahSpan(target, target)
        target < start -> AyahSpan(target, end)
        else -> AyahSpan(start, target)
    }

    private fun clampToSurahOf(anchor: Int, target: Int, surahs: List<Int>): Int {
        val surah = surahs[anchor]
        var lo = anchor
        while (lo > 0 && surahs[lo - 1] == surah) lo--
        var hi = anchor
        while (hi < surahs.lastIndex && surahs[hi + 1] == surah) hi++
        return target.coerceIn(lo, hi)
    }
}
