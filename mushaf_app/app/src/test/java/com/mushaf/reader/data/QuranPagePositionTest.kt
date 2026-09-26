package com.mushaf.reader.data

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuranPagePositionTest {
    @Test
    fun `the supplied page seventeen is the half of the second hizb`() {
        assertEquals(
            QuranPagePosition(QuranPagePosition.Division(1, 2, 3), null),
            QuranPagePosition.fromAyahs(ayahsByPage.getValue(17)),
        )
    }

    @Test
    fun `juz boundaries inside pages are not confused with the equal page plan`() {
        for ((page, oldJuz, oldHizb) in listOf(Triple(121, 6, 12), Triple(201, 10, 20))) {
            assertEquals(
                QuranPagePosition(
                    start = QuranPagePosition.Division(oldJuz, oldHizb, 4),
                    boundary = QuranPagePosition.Division(oldJuz + 1, oldHizb + 1, 1),
                ),
                QuranPagePosition.fromAyahs(ayahsByPage.getValue(page).reversed()),
            )
        }
    }

    @Test
    fun `the last page stays in the fourth quarter of hizb sixty`() {
        assertEquals(
            QuranPagePosition(QuranPagePosition.Division(30, 60, 4), null),
            QuranPagePosition.fromAyahs(ayahsByPage.getValue(604)),
        )
    }

    @Test
    fun `every bundled page has usable and consistent division metadata`() {
        assertEquals((1..604).toSet(), ayahsByPage.keys)
        for ((page, ayahs) in ayahsByPage) {
            assertNotNull("page $page", QuranPagePosition.fromAyahs(ayahs))
        }
    }

    @Test
    fun `missing or inconsistent data does not invent a position`() {
        assertNull(QuranPagePosition.fromAyahs(emptyList()))
        val ayah = ayahsByPage.getValue(17).first()
        assertNull(QuranPagePosition.fromAyahs(listOf(ayah.copy(rub = 0))))
        assertNull(QuranPagePosition.fromAyahs(listOf(ayah.copy(juz = 2))))
        assertNull(QuranPagePosition.fromAyahs(listOf(ayah.copy(hizb = 3))))
    }

    companion object {
        // Read the shipped Quran metadata, so boundary checks also catch asset regressions.
        private val ayahsByPage: Map<Int, List<AyahMarker>> by lazy {
            val records = JSONObject(
                File("src/main/assets/data/ayah_regions.json").readText(Charsets.UTF_8)
            ).getJSONArray("records")
            (0 until records.length()).map { index ->
                val value = records.getJSONObject(index)
                AyahMarker(
                    page = value.getInt("page"),
                    verseKey = value.getString("verse_key"),
                    surahNumber = value.getInt("surah_number"),
                    surahNameAr = value.getString("surah_name_ar"),
                    surahNameEn = value.getString("surah_name_en"),
                    ayahNumber = value.getInt("ayah_number"),
                    textUthmani = "",
                    textImlaei = "",
                    juz = value.getInt("juz"),
                    hizb = value.getInt("hizb"),
                    rub = value.getInt("rub"),
                    isSajdah = false,
                    sajdahNumber = 0,
                    centerX = 0f,
                    centerY = 0f,
                    rects = emptyList(),
                )
            }.groupBy { it.page }
        }
    }
}
