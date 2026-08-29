package com.mushaf.reader.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins down what light stemming may and may not do.
 *
 * The "must survive whole" tests are the important half. Over-trimming is the failure that puts a
 * wrong ayah in front of a reader, so every word here is one the trimmer would mangle if a
 * threshold were loosened.
 */
class ArabicStemmerTest {

    // ── What it is for ──────────────────────────────────────────────────────────

    /** The one case a substring scan cannot serve: the query's affix DIFFERS from the mushaf's. */
    @Test
    fun `inflections of one word reduce to the same stem`() {
        val stem = lightStemArabic("الصابرين")
        assertEquals(stem, lightStemArabic("صابرين"))
        assertEquals(stem, lightStemArabic("والصابرين"))
        assertEquals(stem, lightStemArabic("بالصابرين"))
        assertEquals(stem, lightStemArabic("الصابرون"))
    }

    @Test
    fun `a pronoun suffix is trimmed`() {
        assertEquals(lightStemArabic("كتاب"), lightStemArabic("كتابهم"))
        assertEquals(lightStemArabic("بيوت"), lightStemArabic("بيوتنا"))
    }

    @Test
    fun `each word of a phrase is stemmed independently`() {
        assertEquals("ذين امنوا", lightStemArabic("الذين امنوا"))
    }

    /** The headline case: the same word, pluralised differently in query and mushaf. */
    @Test
    fun `the two sound plurals meet`() {
        assertEquals(lightStemArabic("المقربون"), lightStemArabic("المقربين"))
        assertEquals(lightStemArabic("الصالحات"), lightStemArabic("الصالحين"))
    }

    // ── What it must NOT do ─────────────────────────────────────────────────────

    /**
     * كتاب is the canonical casualty: trim the kaf as a prefix and it becomes تاب, a different
     * word entirely. The four-letter floor is the rule that stops it.
     */
    @Test
    fun `single-letter prefixes do not eat short words`() {
        for (word in listOf("كتاب", "كلمه", "ولد", "بيت", "لسان", "فجر", "وعد")) {
            assertEquals(word, lightStemArabic(word))
        }
    }

    @Test
    fun `the definite article is not taken out of words that merely start with it`() {
        for (word in listOf("الذي", "التي", "الي", "اله")) {
            assertEquals(word, lightStemArabic(word))
        }
    }

    @Test
    fun `short words keep their final letter`() {
        for (word in listOf("في", "به", "له", "علي", "هذا", "يده")) {
            assertEquals(word, lightStemArabic(word))
        }
    }

    /** It trims ends only. Reaching an infix is what a root database is for, and this is not one. */
    @Test
    fun `an infix is never touched, so a stem is not a root`() {
        assertEquals("صابر", lightStemArabic("الصابرين"))
        assertEquals("صبر", lightStemArabic("صبر"))
    }

    // ── Shape ───────────────────────────────────────────────────────────────────

    @Test
    fun `at most one prefix and one suffix come off`() {
        // ين then وال, and then it stops — «صابر» is not trimmed again.
        assertEquals("صابر", lightStemArabic("والصابرين"))
    }

    /**
     * The order that makes the floor mean anything: kaf-first would leave تاب and lose the tie to
     * كتاب. Suffix-first leaves كتاب, and the kaf is then refused for want of room.
     */
    @Test
    fun `the suffix comes off before the prefix`() {
        assertEquals("كتاب", lightStemArabic("كتابهم"))
        assertEquals("كتاب", lightStemArabic("كتاب"))
    }

    @Test
    fun `blank input stays blank and repeated spaces collapse`() {
        assertEquals("", lightStemArabic(""))
        assertEquals("", lightStemArabic("   "))
        assertEquals("رب علوم", lightStemArabic("رب  العلوم"))
    }

    @Test
    fun `stemming is idempotent`() {
        val once = lightStemArabic("والصابرين بيوتنا الذين امنوا")
        assertEquals(once, lightStemArabic(once))
    }
}
