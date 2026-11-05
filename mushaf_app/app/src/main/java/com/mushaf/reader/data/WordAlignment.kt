package com.mushaf.reader.data

/**
 * Pairs each word of an ayah's imlaa'i text with the Uthmani word or words it spells.
 *
 * The search matches mostly against the imlaa'i text, but the reader is shown the Uthmani one, so a
 * highlight found in the first has to be carried over to the second. Position alone does not do it:
 * the two texts disagree on the word count in 366 of the 6236 ayahs.
 *
 *  - The Uthmani script fuses the vocative particle onto its noun: يَـٰٓأَيُّهَا is one word where the
 *    imlaa'i text has يا أيها, and likewise يَـٰقَوْمِ، يَـٰبَنِىٓ، يَـٰمُوسَىٰ — and يَبْنَؤُمَّ is three.
 *  - The other way round, it writes بَعْدِ مَا where the imlaa'i text has بعدما.
 *
 * ## The skeleton
 *
 * Words are compared by their consonantal skeleton — the folded word with ا و ي ء removed — because
 * that is the part the two spellings share. الصلوة and الصلاة, إبرهيم and إبراهيم, ءامنوا and
 * آمنوا all reduce to the same letters. A run of imlaa'i words is merged onto one Uthmani word (or
 * the reverse) when their skeletons concatenate to the same string: يا + قوم → «قم» = يقوم → «قم».
 *
 * ## Why a merge needs a reason
 *
 * Skeletons are short, so they collide. الليل reduces to «لل» and so does اليل + ولا, and an
 * unconditional merge would pair the night with the word after it and shift every later word off by
 * one. A merge is therefore taken only when the word counts still owe one, or when the very next
 * pair agrees and so confirms it. Otherwise the two words are paired as they stand — that is just a
 * spelling difference (الليل / اليل, اللاتي / التي), which is the common case.
 *
 * Measured over the bundled mushaf, this aligns every ayah except 72:16, where وأن لو is written
 * وَأَلَّوِ and the nun is gone from the skeleton too. That ayah returns null and highlights only
 * what the Uthmani text matched itself; a missing highlight is better than one on the wrong word.
 *
 * Pure Kotlin (no Android types) so it can be unit-tested directly.
 *
 * @param imlaei the ayah's imlaa'i words, already folded by [normalizeArabic].
 * @param uthmani the ayah's Uthmani words, folded the same way, without the standalone waqf signs.
 * @return for imlaa'i word i, the first and last Uthmani word it maps to at [2i] and [2i + 1]; or
 *   null when the two texts could not be walked to their ends together.
 */
internal fun alignWords(imlaei: List<String>, uthmani: List<String>): IntArray? {
    val si = Array(imlaei.size) { skeleton(imlaei[it]) }
    val su = Array(uthmani.size) { skeleton(uthmani[it]) }
    val n = si.size
    val m = su.size
    val map = IntArray(2 * n)

    fun pair(a: Int, first: Int, last: Int) {
        map[2 * a] = first
        map[2 * a + 1] = last
    }

    /** True when the pair right after a candidate merge agrees, or when the merge ends both texts
     *  together. Ending only one of them is a merge that cannot be right. */
    fun nextAgrees(a: Int, b: Int): Boolean =
        if (a >= n || b >= m) a >= n && b >= m else si[a] == su[b]

    var a = 0
    var b = 0
    walk@ while (a < n && b < m) {
        if (si[a] == su[b]) {
            pair(a, b, b)
            a++; b++
            continue
        }
        // Positive when the imlaa'i text has words left over, i.e. still owes a merge.
        val extra = (n - a) - (m - b)

        // Several imlaa'i words spelled as one Uthmani word: يا قوم → يقوم.
        for (k in 2..3) {
            if (a + k > n || concat(si, a, k) != su[b]) continue
            if (extra < k - 1 && !nextAgrees(a + k, b + 1)) continue
            for (t in 0 until k) pair(a + t, b, b)
            a += k; b++
            continue@walk
        }
        // One imlaa'i word spelled as several Uthmani words: بعدما → بعد ما.
        for (k in 2..3) {
            if (b + k > m || si[a] != concat(su, b, k)) continue
            if (-extra < k - 1 && !nextAgrees(a + 1, b + k)) continue
            pair(a, b, b + k - 1)
            a++; b += k
            continue@walk
        }
        // A spelling difference within one word: pair the two as they stand.
        pair(a, b, b)
        a++; b++
    }
    return if (a == n && b == m) map else null
}

private fun skeleton(word: String): String =
    word.filterNot { it == 'ا' || it == 'و' || it == 'ي' || it == 'ء' }

private fun concat(words: Array<String>, from: Int, count: Int): String =
    buildString { for (i in from until from + count) append(words[i]) }
