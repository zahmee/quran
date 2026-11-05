package com.mushaf.reader.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The cases [alignWords] exists for, as short runs of mushaf words. The words are folded here
 * exactly as the index folds them, so the fixtures exercise the same strings the index aligns.
 * [AyahSearchCorpusTest] runs the alignment over the whole mushaf.
 */
class WordAlignmentTest {

    private fun words(text: String): List<String> =
        normalizeArabic(text).split(' ').filter { it.isNotEmpty() }

    private fun align(imlaei: String, uthmani: String): IntArray? =
        alignWords(words(imlaei), words(uthmani))

    @Test
    fun `equal spellings pair one to one`() {
        assertArrayEquals(
            intArrayOf(0, 0, 1, 1, 2, 2, 3, 3),
            align("الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ", "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ"),
        )
    }

    /** 2:54 — the Uthmani script fuses يا onto its noun: يا قوم is the one word يَـٰقَوْمِ. */
    @Test
    fun `a vocative pair maps onto the one uthmani word`() {
        assertArrayEquals(
            intArrayOf(0, 0, 1, 1, 1, 1, 2, 2),
            align("لِقَوْمِهِ يَا قَوْمِ إِنَّكُمْ", "لِقَوْمِهِۦ يَـٰقَوْمِ إِنَّكُمْ"),
        )
    }

    /** 2:181 — the other direction: the imlaa'i بعدما is the Uthmani بَعْدَ مَا. */
    @Test
    fun `one imlaai word can span two uthmani words`() {
        assertArrayEquals(
            intArrayOf(0, 0, 1, 2, 3, 3),
            align("بَدَّلَهُ بَعْدَمَا سَمِعَهُ", "بَدَّلَهُۥ بَعْدَ مَا سَمِعَهُۥ"),
        )
    }

    /**
     * The trap the merge guard is for: الليل and اليل + ولا share the skeleton «لل». With the word
     * counts already equal, the two must pair as a spelling difference, not merge.
     */
    @Test
    fun `a skeleton collision does not merge when nothing is owed`() {
        assertArrayEquals(
            intArrayOf(0, 0, 1, 1, 2, 2),
            align("مِنَ اللَّيْلِ وَلَا", "مِّنَ ٱلَّيْلِ وَلَا"),
        )
    }

    /** 72:16 — وأن لو is written وَأَلَّوِ, and the nun is gone from the skeleton too. */
    @Test
    fun `an ayah that cannot be walked through returns null rather than a wrong map`() {
        assertNull(
            align(
                "وَأَن لَّوِ اسْتَقَامُوا عَلَى الطَّرِيقَةِ لَأَسْقَيْنَاهُم مَّاءً غَدَقًا",
                "وَأَلَّوِ ٱسْتَقَـٰمُوا۟ عَلَى ٱلطَّرِيقَةِ لَأَسْقَيْنَـٰهُم مَّآءً غَدَقًا",
            )
        )
    }
}
