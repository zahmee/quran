package com.mushaf.reader.reader

import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.AyahRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins down what a press between two lines selects.
 *
 * The bug this guards: a press in the strip between two highlight bands used to go to the ayah with
 * the nearest END MARKER, which is often an ayah on neither line — here 2:6, whose marker sits near the
 * right end of line 2, won presses that were plainly on 2:7.
 *
 * The three ayahs are the real page-3 records from ayah_regions.json.
 */
class AyahHitTestTest {

    private fun ayah(key: String, cx: Float, cy: Float, vararg rects: AyahRect) = AyahMarker(
        page = 3, verseKey = key, surahNumber = 2, surahNameAr = "", surahNameEn = "",
        ayahNumber = key.substringAfter(':').toInt(), textUthmani = "", textImlaei = "",
        juz = 1, hizb = 1, rub = 1, isSajdah = false, sajdahNumber = 0,
        centerX = cx, centerY = cy, rects = rects.toList(),
    )

    private val a6 = ayah("2:6", 868f, 198.25f, AyahRect(8f, 32f, 1090f, 102f), AyahRect(838f, 148f, 260f, 102f))
    private val a7 = ayah("2:7", 306f, 318.5f, AyahRect(8f, 148f, 830f, 102f), AyahRect(276f, 268f, 822f, 102f))
    private val a8 = ayah("2:8", 43f, 436.5f, AyahRect(8f, 268f, 268f, 102f), AyahRect(13f, 386f, 1085f, 102f))
    private val page = listOf(a6, a7, a8)

    @Test
    fun `a press inside a band selects that band's ayah`() {
        assertEquals("2:7", pickAyah(page, 500f, 200f)?.verseKey)
        assertEquals("2:6", pickAyah(page, 900f, 200f)?.verseKey)
        assertEquals("2:8", pickAyah(page, 100f, 300f)?.verseKey)
    }

    @Test
    fun `a press between two lines of one ayah stays on that ayah`() {
        // Line 2 above and line 3 below are both 2:7 here; 2:6's marker is the nearest marker.
        assertEquals("2:7", pickAyah(page, 784f, 259f)?.verseKey)
    }

    @Test
    fun `between two different ayahs the nearer line wins`() {
        // x=1000: line 2 is 2:6 (band ends at y=250), line 3 is 2:7 (band starts at y=268).
        assertEquals("2:6", pickAyah(page, 1000f, 255f)?.verseKey)
        assertEquals("2:7", pickAyah(page, 1000f, 265f)?.verseKey)
    }

    @Test
    fun `above the first line goes to the first line`() {
        assertEquals("2:6", pickAyah(page, 500f, 10f)?.verseKey)
    }

    @Test
    fun `an exact tie between two bands goes to the nearer end marker`() {
        val upper = ayah("2:1", 900f, 50f, AyahRect(0f, 0f, 1000f, 100f))
        val lower = ayah("2:2", 100f, 170f, AyahRect(0f, 120f, 1000f, 100f))
        assertEquals("2:1", pickAyah(listOf(upper, lower), 800f, 110f)?.verseKey)
        assertEquals("2:2", pickAyah(listOf(upper, lower), 200f, 110f)?.verseKey)
    }

    @Test
    fun `no ayahs, no selection`() {
        assertNull(pickAyah(emptyList(), 10f, 10f))
    }
}
