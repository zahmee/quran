package com.mushaf.reader.data

/**
 * Strip Arabic diacritics/tatweel and fold letter variants so search ignores tashkeel.
 *
 * The mushaf text is fully vocalised, so a plain `contains` would only ever match a query typed
 * with the exact same harakat. Both sides of the comparison go through here instead.
 *
 * Pure Kotlin (no Android types) so it can be unit-tested directly.
 */
internal fun normalizeArabic(s: String): String {
    val sb = StringBuilder(s.length)
    for (ch in s) {
        when (ch) {
            // harakat, tanwin, shadda, sukun, superscript alef, tatweel
            'ً', 'ٌ', 'ٍ', 'َ', 'ُ', 'ِ', 'ّ',
            'ْ', 'ٓ', 'ٔ', 'ٕ', 'ٰ', 'ـ' -> {}
            'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
            'ى' -> sb.append('ي')
            'ئ' -> sb.append('ي')
            'ؤ' -> sb.append('و')
            'ة' -> sb.append('ه')
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}
