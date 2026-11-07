package com.mushaf.reader.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TafsirPassageTest {

    private fun row(kind: TafsirRowKind, from: Int, to: Int, text: String) =
        TafsirPassageRow(kind, from, to, text)

    // Shaped like Sa'di's 4:77-79 passage: 4:78 is explained twice, either side of a passage cut.
    private val merged = listOf(
        row(TafsirRowKind.QuranText, 77, 77, "{نص ٧٧}"),
        row(TafsirRowKind.AyahTafsir, 77, 77, "{77} أ"),
        row(TafsirRowKind.AyahTafsir, 78, 78, "{78} ب"),
        row(TafsirRowKind.Prose, 0, 0, "ثم قال:"),
        row(TafsirRowKind.QuranText, 78, 79, "{نص ٧٨-٧٩}"),
        row(TafsirRowKind.AyahTafsir, 78, 78, "{78} ج"),
        row(TafsirRowKind.AyahTafsir, 79, 79, "{79} د"),
    )

    @Test
    fun `a note anchor stays out of what is copied or shared`() {
        val rows = listOf(
            TafsirPassageRow(TafsirRowKind.AyahTafsir, 5, 5, "{5} شرح⟦1⟧ ثم تتمة⟦2⟧.", listOf("حاشية", "أخرى")),
        )
        val passage = TafsirPassage(rows, ayah = 5)
        assertEquals("{5} شرح ثم تتمة.", passage.shareText)
        assertEquals(listOf("1", "2"), TafsirPassageRow.NoteAnchor.findAll(rows[0].text).map { it.groupValues[1] }.toList())
    }

    @Test
    fun `the notes column holds one note per line and may be empty`() {
        assertEquals(emptyList<String>(), TafsirPassageRow.parseNotes(""))
        assertEquals(listOf("أ", "ب"), TafsirPassageRow.parseNotes("أ\nب"))
    }

    @Test
    fun `a supplement counts as the ayah's own explanation`() {
        val rows = listOf(
            row(TafsirRowKind.QuranText, 143, 143, "نص"),
            row(TafsirRowKind.AyahTafsir, 143, 143, "{143} أ"),
            row(TafsirRowKind.Supplement, 143, 143, "تكملة"),
            row(TafsirRowKind.AyahTafsir, 143, 143, "ب"),
        )
        val passage = TafsirPassage(rows, ayah = 143)
        assertEquals(listOf(1, 2, 3), passage.focusRows)
        assertEquals("{143} أ\n\nتكملة\n\nب", passage.shareText)
        assertEquals(TafsirRowKind.Supplement, TafsirRowKind.fromSource("تكملة من الموسوعة"))
    }

    @Test
    fun `sharing names the second source only when its words go out`() {
        val rows = listOf(
            row(TafsirRowKind.AyahTafsir, 142, 142, "{142} أ"),
            row(TafsirRowKind.AyahTafsir, 143, 143, "{143} ب"),
            row(TafsirRowKind.Supplement, 143, 143, "تكملة"),
        )
        assertFalse(TafsirPassage(rows, ayah = 142).sharesSupplement)
        assertTrue(TafsirPassage(rows, ayah = 143).sharesSupplement)
    }

    @Test
    fun `an explanation extended over a neighbour focuses for both ayahs`() {
        // 2:50 has no row of its own in the Center's copy; it is explained together with 2:49.
        val rows = listOf(
            row(TafsirRowKind.QuranText, 49, 52, "نص"),
            row(TafsirRowKind.AyahTafsir, 49, 50, "{49} شرح"),
            row(TafsirRowKind.AyahTafsir, 51, 52, "{51} شرح"),
        )
        assertEquals(listOf(1), TafsirPassage(rows, ayah = 50).focusRows)
        assertEquals(listOf(2), TafsirPassage(rows, ayah = 52).focusRows)
    }

    @Test
    fun `source row types map to layout kinds`() {
        assertEquals(TafsirRowKind.QuranText, TafsirRowKind.fromSource("المقطع"))
        assertEquals(TafsirRowKind.AyahTafsir, TafsirRowKind.fromSource("تفسير آية"))
        assertEquals(TafsirRowKind.AyahTafsir, TafsirRowKind.fromSource("تكملة تفسير الآية"))
        assertEquals(TafsirRowKind.SurahTitle, TafsirRowKind.fromSource("اسم السورة"))
        assertEquals(TafsirRowKind.Divider, TafsirRowKind.fromSource("نجوم"))
        assertEquals(TafsirRowKind.Prose, TafsirRowKind.fromSource("تمهيد للمقطع"))
        assertEquals(TafsirRowKind.Prose, TafsirRowKind.fromSource("نوع لم يظهر من قبل"))
    }

    @Test
    fun `range-less rows cover no ayah`() {
        assertFalse(row(TafsirRowKind.Prose, 0, 0, "").covers(1))
        assertTrue(row(TafsirRowKind.AyahTafsir, 3, 4, "").covers(4))
        assertFalse(row(TafsirRowKind.AyahTafsir, 3, 4, "").covers(5))
    }

    @Test
    fun `an ayah explained twice focuses both rows and shares them in order`() {
        val passage = TafsirPassage(merged, ayah = 78)
        assertEquals(listOf(2, 5), passage.focusRows)
        assertEquals(2, passage.focusIndex)
        assertTrue(passage.hasOwnTafsir)
        assertEquals("{78} ب\n\n{78} ج", passage.shareText)
    }

    @Test
    fun `quran text rows never count as an ayah's own explanation`() {
        val passage = TafsirPassage(merged, ayah = 79)
        assertEquals(listOf(6), passage.focusRows)
    }

    @Test
    fun `an ayah the author passed over falls back to its passage`() {
        // Like 19:1 (كهيعص): inside the passage's Quran text, with no explanation of its own.
        val rows = listOf(
            row(TafsirRowKind.SurahTitle, 0, 0, "تفسير سورة مريم"),
            row(TafsirRowKind.QuranText, 1, 6, "{كهيعص (1) ...}"),
            row(TafsirRowKind.AyahTafsir, 2, 2, "{2} شرح"),
            row(TafsirRowKind.Prose, 0, 0, "ثم قال:"),
            row(TafsirRowKind.AyahTafsir, 3, 4, "{3 - 4} شرح"),
        )
        val passage = TafsirPassage(rows, ayah = 1)
        assertFalse(passage.hasOwnTafsir)
        assertEquals(1, passage.focusIndex)
        assertEquals("{2} شرح\n\nثم قال:\n\n{3 - 4} شرح", passage.shareText)
    }
}
