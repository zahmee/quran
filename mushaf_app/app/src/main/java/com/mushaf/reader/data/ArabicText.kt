package com.mushaf.reader.data

// Every range bound below is a combining mark or an invisible sign, so as a source literal it
// renders on top of the surrounding quote and a mangled character would not show in a diff. Each
// one therefore names its codepoint in the doc above it, and ArabicTextTest asserts the bounds
// against those codepoints so a silent corruption fails the build rather than the search.

/** Tashkeel and the two hamza seats: fathatan (U+064B) through hamza-below (U+0655). */
private const val TASHKEEL_FIRST = 'ً'
private const val TASHKEEL_LAST = 'ٕ'

/**
 * The Quranic annotation block, U+06D6..U+06ED.
 *
 * Waqf signs, the rub-el-hizb and sajdah symbols, and the small superscript letters (U+06E5 small
 * waw, U+06E6 small yeh) that spell out the Uthmani ligatures. Unicode classes those two as
 * LETTERS rather than marks, so no filter phrased in terms of character category catches them —
 * the block has to be named by range.
 *
 * This range is why «أولئك» used to find nothing. The mushaf writes that word with a U+06DF
 * (small high rounded zero) between the waw and the lam, and it survived every fold: the ayah
 * folded to «او۟ليك» while the typed query folded to «اوليك», and the two never met. 4964 of the
 * 6236 ayahs carry at least one character from this block.
 */
private const val QURANIC_ANNOTATION_FIRST = 'ۖ'
private const val QURANIC_ANNOTATION_LAST = 'ۭ'

/** Tatweel (U+0640) — the kashida that stretches a letter; never part of a word. */
private const val TATWEEL = 'ـ'

/** Superscript "dagger" alef (U+0670) — the Uthmani script's long /aa/. See [AyahSearchIndex]. */
private const val SUPERSCRIPT_ALEF = 'ٰ'

/**
 * Strip Arabic diacritics and Quranic annotation, and fold letter variants, so a query typed on an
 * ordinary keyboard can be compared against the mushaf.
 *
 * The mushaf text is fully vocalised and annotated, so a plain `contains` would only ever match a
 * query typed with the exact same harakat and waqf marks. Both sides of the comparison go through
 * here instead.
 *
 * The ranges above are not a guess: they are every non-letter codepoint that actually occurs in
 * the bundled mushaf text, plus the two annotation characters Unicode mislabels as letters.
 *
 * Folding alone does NOT make search work. It cannot reconcile the two orthographies, because that
 * is a property of the spelling and not of the marks — see [AyahSearchIndex].
 *
 * Pure Kotlin (no Android types) so it can be unit-tested directly.
 */
internal fun normalizeArabic(s: String): String {
    val sb = StringBuilder(s.length)
    for (ch in s) {
        when {
            ch in TASHKEEL_FIRST..TASHKEEL_LAST -> {}
            ch in QURANIC_ANNOTATION_FIRST..QURANIC_ANNOTATION_LAST -> {}
            ch == SUPERSCRIPT_ALEF || ch == TATWEEL -> {}
            ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> sb.append('ا')
            ch == 'ى' || ch == 'ئ' -> sb.append('ي')
            ch == 'ؤ' -> sb.append('و')
            ch == 'ة' -> sb.append('ه')
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}
