package com.mushaf.reader.data

/**
 * The ayah before or after another, in mushaf order: across the end of a surah into the next
 * one's first ayah, and back across its start into the previous one's last. The mushaf's own two
 * ends have nothing beyond them.
 */
object AyahNeighbours {

    /** The ayah [direction] steps away (+1 after, -1 before) from [surah]:[ayah], or null. */
    fun step(surah: Int, ayah: Int, direction: Int): Pair<Int, Int>? {
        val count = SurahAyahCounts.of(surah)
        if (count == 0 || ayah !in 1..count) return null
        return when {
            direction > 0 && ayah < count -> surah to ayah + 1
            direction > 0 && surah < SurahAyahCounts.SURAH_COUNT -> surah + 1 to 1
            direction < 0 && ayah > 1 -> surah to ayah - 1
            direction < 0 && surah > 1 -> (surah - 1).let { it to SurahAyahCounts.of(it) }
            else -> null
        }
    }
}
