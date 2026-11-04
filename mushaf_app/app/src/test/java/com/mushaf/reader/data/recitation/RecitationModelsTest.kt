package com.mushaf.reader.data.recitation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecitationModelsTest {

    // Trimmed from the live /api/v3/ayat_timing/reads answer (2026-11-04), plus broken entries.
    private val readsJson = """
        [
          {"id":123,"name":"مشاري العفاسي","rewaya":"حفص عن عاصم",
           "folder_url":"https://cdn.mp3quran.net/audio/alafasy/r1/","soar_count":114,"soar_link":"x"},
          {"id":137,"name":"قارئ ناقص","rewaya":"حفص عن عاصم",
           "folder_url":"https://cdn.mp3quran.net/audio/partial","soar_count":107},
          {"id":9,"name":"بلا تشفير","folder_url":"http://example.com/a/","soar_count":114},
          {"id":0,"name":"بلا رقم","folder_url":"https://example.com/b/","soar_count":114},
          {"id":123,"name":"مكرر","folder_url":"https://example.com/c/","soar_count":114}
        ]
    """.trimIndent()

    // Al-Fatihah by Alafasy (read 123), live values: ayah 1 starts after the isti'adha.
    private val fatihahJson = """
        [{"ayah":1,"start_time":6420,"end_time":11120},{"ayah":2,"start_time":11120,"end_time":17640},
         {"ayah":3,"start_time":17640,"end_time":21280},{"ayah":4,"start_time":21280,"end_time":26800},
         {"ayah":5,"start_time":26800,"end_time":33280},{"ayah":6,"start_time":33280,"end_time":38960},
         {"ayah":7,"start_time":38960,"end_time":51200}]
    """.trimIndent()

    @Test
    fun `reciters keep valid https entries once and complete only when all 114 surahs exist`() {
        val reciters = RecitationJson.parseReciters(readsJson)
        assertEquals(listOf(123, 137), reciters.map { it.id })
        assertTrue(reciters[0].complete)
        assertEquals(false, reciters[1].complete)
        // A folder without the trailing slash still builds a proper file URL.
        assertEquals("https://cdn.mp3quran.net/audio/partial/002.mp3", reciters[1].surahUrl(2))
        assertEquals("https://cdn.mp3quran.net/audio/alafasy/r1/114.mp3", reciters[0].surahUrl(114))
    }

    @Test
    fun `ayah at a position follows the table and the opening is no ayah`() {
        val t = RecitationJson.parseTiming(1, fatihahJson)
        assertNull(t.ayahAt(0))          // isti'adha
        assertNull(t.ayahAt(6419))
        assertEquals(1, t.ayahAt(6420))  // boundaries belong to the ayah that starts there
        assertEquals(1, t.ayahAt(11119))
        assertEquals(2, t.ayahAt(11120))
        assertEquals(7, t.ayahAt(51200))
        assertEquals(7, t.ayahAt(60000)) // the file's tail stays on the last ayah
    }

    @Test
    fun `ayah one starts at the top of the file so the opening is heard`() {
        val t = RecitationJson.parseTiming(1, fatihahJson)
        assertEquals(0L, t.startOf(1))
        assertEquals(17640L, t.startOf(3))
        assertEquals(21280L, t.endOf(3))
        assertNull(t.startOf(8))
    }

    @Test
    fun `an ayah zero intro and unsorted rows are handled`() {
        val t = RecitationJson.parseTiming(
            2,
            """[{"ayah":2,"start_time":9400,"end_time":15760},
                {"ayah":0,"start_time":0,"end_time":5587},
                {"ayah":1,"start_time":5587,"end_time":9400}]""",
        )
        assertEquals(listOf(0, 1, 2), t.ayahs.map { it.ayah })
        assertNull(t.ayahAt(100))
        assertEquals(1, t.ayahAt(5600))
        assertEquals(2, t.ayahAt(9400))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty or ayah-less answer is rejected`() {
        RecitationJson.parseTiming(1, """[{"ayah":0,"start_time":0,"end_time":10}]""")
    }

    @Test
    fun `steps cross surah boundaries and stop at the ends of the mushaf`() {
        assertEquals(AyahRef(1, 2), AyahSteps.next(AyahRef(1, 1)))
        assertEquals(AyahRef(2, 1), AyahSteps.next(AyahRef(1, 7)))
        assertNull(AyahSteps.next(AyahRef(114, 6)))
        assertEquals(AyahRef(1, 7), AyahSteps.previous(AyahRef(2, 1)))
        assertEquals(AyahRef(2, 285), AyahSteps.previous(AyahRef(2, 286)))
        assertNull(AyahSteps.previous(AyahRef(1, 1)))
        assertEquals("2:255", AyahRef(2, 255).verseKey)
    }
}
