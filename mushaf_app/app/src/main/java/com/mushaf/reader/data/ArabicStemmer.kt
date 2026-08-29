package com.mushaf.reader.data

/**
 * The definite-article family. Kept apart from the other prefixes because ال is the one affix that
 * is almost never the opening of the word itself, so it is allowed to cut closer to the bone.
 */
private val ARTICLE_PREFIXES = arrayOf("وال", "فال", "بال", "كال", "لل", "ال")

/** The single-letter particles. Each is also an ordinary first letter of thousands of words. */
private val PARTICLE_PREFIXES = arrayOf("و", "ف", "ب", "ك", "ل")

/** Suffixes, longest first: pronouns, duals, the sound plurals, and the verbal «وا». */
private val SUFFIXES = arrayOf(
    "هما", "كما", "هم", "هن", "كم", "كن", "نا", "ها", "ون", "ين", "ات", "ان", "وا", "ه", "ي",
)

/**
 * Shortest stem a trim may leave behind.
 *
 * Four, not three. Three is the floor of Arabic word shape, so a trim that lands exactly on it is
 * usually a trim that ate a real letter: كتاب would become تاب, بيوت would become يوت. Requiring
 * one letter of headroom is what keeps those whole.
 */
private const val MIN_STEM = 4

/** ال may leave three, since it is the one affix rarely confusable with the word's own opening. */
private const val MIN_STEM_AFTER_ARTICLE = 3

/**
 * Light stemming: trim the affixes glued onto each word, so different inflections of the same
 * surface word compare equal.
 *
 * This is NOT root extraction, and the difference matters. It touches only the two ENDS of a word
 * and never its middle, so it carries صابرين → صابر but never صابر → صبر: that alef is an infix,
 * and reaching it needs a morphological analyser and a root database.
 *
 * What it buys is exactly the case a substring scan cannot serve — a query whose affix DIFFERS
 * from the one in the mushaf, like المقربين typed against ٱلْمُقَرَّبُونَ. A query that is merely
 * shorter (مقرب) already matches without any of this.
 *
 * ## Why the suffix comes off first
 *
 * The two trims are not independent. Take كتابهم: strip the kaf first and five letters remain, so
 * the trim looks safe, and هم then leaves تاب — a different word, and one that no longer matches
 * the كتاب it came from. Removing the suffix first leaves كتاب, and the kaf is then correctly
 * refused for want of room. The order is what makes the length rule mean anything.
 *
 * ## Why the floor is four
 *
 * Blind trimming mutilates real words. A stem that declines to trim costs one widened match; a
 * stem that over-trims invents a wrong one, and a wrong ayah shown against the Quran is the worse
 * failure by far. That asymmetry, not recall, set [MIN_STEM]; [ArabicStemmerTest] pins the words
 * that must survive whole.
 *
 * One suffix and one prefix at most, never applied repeatedly, so the result stays predictable.
 *
 * Pure Kotlin (no Android types) so it can be unit-tested directly.
 */
internal fun lightStemArabic(text: String): String {
    val sb = StringBuilder(text.length)
    for (word in text.split(' ')) {
        if (word.isEmpty()) continue
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(stemWord(word))
    }
    return sb.toString()
}

private fun stemWord(word: String): String {
    var w = word
    for (suffix in SUFFIXES) {
        if (!w.endsWith(suffix)) continue
        if (w.length - suffix.length >= MIN_STEM) w = w.substring(0, w.length - suffix.length)
        break
    }
    for (prefix in ARTICLE_PREFIXES) {
        if (!w.startsWith(prefix)) continue
        if (w.length - prefix.length >= MIN_STEM_AFTER_ARTICLE) return w.substring(prefix.length)
        return w
    }
    for (prefix in PARTICLE_PREFIXES) {
        if (!w.startsWith(prefix)) continue
        if (w.length - prefix.length >= MIN_STEM) return w.substring(prefix.length)
        return w
    }
    return w
}
