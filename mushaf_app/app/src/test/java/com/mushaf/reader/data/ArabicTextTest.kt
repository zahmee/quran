package com.mushaf.reader.data

import org.junit.Assert.assertEquals
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
}
