package com.mushaf.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The folding that lets an unvocalised query match the fully vocalised mushaf text. */
class ArabicTextTest {

    @Test
    fun `harakat tanwin shadda and sukun are stripped`() {
        assertEquals("الحمد", normalizeArabic("الْحَمْدُ"))
        assertEquals("لله", normalizeArabic("لِلَّهِ"))
        assertEquals("علما", normalizeArabic("عِلْمًا"))
    }

    @Test
    fun `tatweel is removed`() {
        assertEquals("كتاب", normalizeArabic("كــتــاب"))
    }

    @Test
    fun `every hamza carrier on alef folds to bare alef`() {
        assertEquals("اااا", normalizeArabic("أإآٱ"))
    }

    @Test
    fun `alef maqsura and hamza on ya fold to ya`() {
        assertEquals("علي", normalizeArabic("على"))
        assertEquals("شيء", normalizeArabic("شيء"))
        assertEquals("ي", normalizeArabic("ئ"))
    }

    @Test
    fun `hamza on waw folds to waw and ta marbuta folds to ha`() {
        assertEquals("و", normalizeArabic("ؤ"))
        assertEquals("رحمه", normalizeArabic("رحمة"))
    }

    @Test
    fun `digits spaces and punctuation pass through untouched`() {
        assertEquals("", normalizeArabic(""))
        assertEquals("2:255", normalizeArabic("2:255"))
        // Only the ta marbuta is folded here; everything else is copied verbatim.
        assertEquals("سوره البقره 255", normalizeArabic("سورة البقرة 255"))
    }

    /** The property the search actually depends on: fold both sides, then a plain contains works. */
    @Test
    fun `an unvocalised query matches vocalised mushaf text once both are folded`() {
        val ayah = "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ"
        assertTrue(normalizeArabic(ayah).contains(normalizeArabic("الحمد لله")))
        assertTrue(normalizeArabic(ayah).contains(normalizeArabic("رب العالمين")))
    }

    @Test
    fun `folding is idempotent`() {
        val once = normalizeArabic("الرَّحْمَٰنِ الرَّحِيمِ")
        assertEquals(once, normalizeArabic(once))
    }

    // ── The Quranic annotation block, U+06D6..U+06ED ────────────────────────────

    /**
     * The bounds are asserted by codepoint because they are invisible as source literals: a
     * mangled paste would silently narrow the range and quietly break search again.
     */
    @Test
    fun `the stripped ranges sit on the codepoints they claim`() {
        assertEquals("", normalizeArabic("ًٕ"))          // tashkeel bounds
        assertEquals("", normalizeArabic("ۭۖ"))          // annotation bounds
        assertEquals("", normalizeArabic("ٰـ"))          // dagger alef, tatweel
        // One past each end must survive, or the range is too wide.
        assertEquals("ي", normalizeArabic("ي"))          // yeh, just below 064B
        assertEquals("ٖ", normalizeArabic("ٖ"))          // just above 0655
        assertEquals("ە", normalizeArabic("ە"))          // just below 06D6
        assertEquals("ۮ", normalizeArabic("ۮ"))          // just above 06ED
    }

    /** Waqf signs sit between words and break any query that spans them. */
    @Test
    fun `waqf signs are stripped`() {
        assertEquals("رب العلمين", normalizeArabic("رَبِّ ٱلْعَـٰلَمِينَ").replace("  ", " "))
        assertEquals("اهدنا الصرط", normalizeArabic("ٱهْدِنَا ٱلصِّرَٰطَ"))
    }

    /**
     * U+06E5 and U+06E6 are classed as LETTERS by Unicode, so a fold written in terms of character
     * category would keep them. They have to be stripped by range.
     */
    @Test
    fun `the small waw and small yeh are stripped though unicode calls them letters`() {
        assertEquals("داود", normalizeArabic("دَاوُۥدَ"))
        assertEquals("عبادته", normalizeArabic("عِبَادَتِهِۦ"))
    }

    /** The exact word from the reported bug: it used to keep a U+06DF and match nothing. */
    @Test
    fun `the word that broke search now folds to what a reader types`() {
        assertEquals(normalizeArabic("أولئك"), normalizeArabic("أُو۟لَـٰٓئِكَ"))
    }

    /**
     * Folding cannot bridge the two orthographies — that is [AyahSearchIndex]'s job, and this is
     * the assertion that says so out loud.
     */
    @Test
    fun `folding alone does not reconcile the uthmani and imlaai spellings`() {
        assertNotEquals(normalizeArabic("الملائكة"), normalizeArabic("ٱلْمَلَـٰٓئِكَةُ"))
        assertEquals(normalizeArabic("الملائكة"), normalizeArabic("الْمَلَائِكَةُ"))
    }
}
