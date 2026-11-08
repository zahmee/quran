package com.mushaf.reader.data.content

/**
 * Text copied or shared out of the explanation sheet. A long tafsir row (Tabari runs to 170,000
 * characters for one ayah) is too big for the share intent, which fails above roughly a megabyte, and
 * too long for any chat app anyway, so it is cut at a word and marked as cut.
 */
object SharedText {
    const val MAX_CHARS = 30_000
    const val CUT_MARK = " … (مقتطع)"

    fun limit(text: String, max: Int = MAX_CHARS): String {
        if (text.length <= max) return text
        val head = text.take(max)
        val space = head.lastIndexOfAny(charArrayOf(' ', '\n'))
        return (if (space > max / 2) head.take(space) else head).trimEnd() + CUT_MARK
    }
}
