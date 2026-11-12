package com.mushaf.reader.share

/**
 * What a run of ayahs may cover. [surahs] and [pages] give the surah and the page of every ayah in
 * the run's context, in reading order — the indices an [AyahSpan] counts in.
 *
 * A run never crosses into another surah: a cut spanning two would carry the printed surah header
 * between them, which belongs to neither. It covers at most [maxPages] pages: each one adds a page's
 * height to the image, and past a few the image is too tall to read once a messaging app shrinks it.
 */
internal class SpanRules(
    val surahs: List<Int>,
    val pages: List<Int> = List(surahs.size) { 1 },
    val maxPages: Int = Int.MAX_VALUE,
) {
    init {
        require(pages.size == surahs.size) { "${pages.size} pages for ${surahs.size} ayahs" }
    }

    fun pageCount(start: Int, end: Int): Int = pages[end] - pages[start] + 1

    /** [target], pulled toward [anchor] until it shares the anchor's surah and is close enough
     *  in pages. Indices run in reading order, so both hold over a contiguous stretch. */
    fun clamp(anchor: Int, target: Int): Int {
        var t = target
        while (t > anchor && (surahs[t] != surahs[anchor] || pageCount(anchor, t) > maxPages)) t--
        while (t < anchor && (surahs[t] != surahs[anchor] || pageCount(t, anchor) > maxPages)) t++
        return t
    }
}

/**
 * A run of consecutive ayahs: [start] to [end] inclusive, as indices into the ayahs a [SpanRules]
 * describes, in reading order.
 */
internal data class AyahSpan(val start: Int, val end: Int) {

    init {
        require(start <= end) { "start $start after end $end" }
    }

    /** Result of dragging one end: the new span, and whether the end under the finger is now its
     *  start — dragging past the other end hands the finger over to the opposite end. */
    data class Drag(val span: AyahSpan, val movingStart: Boolean)

    /** Drags the start (or the end) to [target]; the other end stays put as the anchor. Past what
     *  [rules] allow, the end stops at the last ayah it may reach. */
    fun drag(movingStart: Boolean, target: Int, rules: SpanRules): Drag {
        val anchor = if (movingStart) end else start
        val t = rules.clamp(anchor, target)
        return if (t <= anchor && (movingStart || t < anchor)) {
            Drag(AyahSpan(t, anchor), movingStart = true)
        } else {
            Drag(AyahSpan(anchor, t), movingStart = false)
        }
    }

    /**
     * A tap on the ayah at [target]: before the span extends its start to it, after it extends its
     * end, inside it pulls the end back to it. An ayah of another surah starts a new span there.
     *
     * Null when reaching [target] would take the run over [SpanRules.maxPages]: a tap names one
     * ayah, so stopping short of it would select something the reader did not ask for.
     */
    fun tap(target: Int, rules: SpanRules): AyahSpan? {
        val next = when {
            rules.surahs[target] != rules.surahs[start] -> return AyahSpan(target, target)
            target < start -> AyahSpan(target, end)
            else -> AyahSpan(start, target)
        }
        return next.takeIf { rules.pageCount(it.start, it.end) <= rules.maxPages }
    }
}
