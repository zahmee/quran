package com.mushaf.reader.data

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HizbMarkerTest {
    @Test
    fun `all printed anchors match actual division starts and unchanged source images`() {
        val parsed = HizbMarker.fromJson(anchors.toString(), ayahs, 1106, 1789)
        val records = anchors.getJSONArray("records")
        val omitted = anchors.getJSONArray("division_starts_without_star")
        assertEquals(199, parsed.size)
        assertEquals(240, parsed.size + omitted.length())
        assertEquals(hash(ayahFile), anchors.getString("source_sha256"))
        val accounted = parsed.map { it.verseKey }.toMutableSet()
        for (i in 0 until omitted.length()) {
            val key = omitted.getString(i)
            assertTrue(accounted.add(key))
            assertEquals(1, ayahs.single { it.verseKey == key }.ayahNumber)
        }
        assertEquals(ayahs.distinctBy { it.rub }.map { it.verseKey }.toSet(), accounted)
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            assertTrue(record.getDouble("match_score") >= 0.70)
            assertEquals(hash(File("src/main/assets/pages/${record.getInt("page")}.webp")), record.getString("image_sha256"))
        }
    }

    @Test
    fun `the star labels the new division even when the page starts in the previous one`() {
        val byPage = HizbMarker.fromJson(anchors.toString(), ayahs, 1106, 1789).associateBy { it.page }
        assertEquals(QuranPagePosition.Division(1, 2, 3), byPage.getValue(17).division)
        assertEquals(QuranPagePosition.Division(7, 13, 1), byPage.getValue(121).division)
        assertEquals(QuranPagePosition.Division(11, 21, 1), byPage.getValue(201).division)
        assertEquals(AyahRect(1058f, 76f, 24f, 24f), byPage.getValue(17).bounds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `stale coordinates for another page are rejected`() {
        val altered = JSONObject(anchors.toString())
        altered.getJSONArray("records").getJSONObject(0).put("page", 604)
        HizbMarker.fromJson(altered.toString(), ayahs, 1106, 1789)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `coordinates outside the image are rejected`() {
        val altered = JSONObject(anchors.toString())
        altered.getJSONArray("records").getJSONObject(0).put("y", 1789)
        HizbMarker.fromJson(altered.toString(), ayahs, 1106, 1789)
    }

    companion object {
        private val ayahFile = File("src/main/assets/data/ayah_regions.json")
        private val anchors = JSONObject(File("src/main/assets/data/hizb_markers.json").readText())
        private val ayahs = JSONObject(ayahFile.readText()).getJSONArray("records").let { records ->
            (0 until records.length()).map { index ->
                val r = records.getJSONObject(index)
                AyahMarker(
                    page = r.getInt("page"), verseKey = r.getString("verse_key"),
                    surahNumber = r.getInt("surah_number"), surahNameAr = "", surahNameEn = "",
                    ayahNumber = r.getInt("ayah_number"), textUthmani = "", textImlaei = "",
                    juz = r.getInt("juz"), hizb = r.getInt("hizb"), rub = r.getInt("rub"),
                    isSajdah = false, sajdahNumber = 0, centerX = 0f, centerY = 0f, rects = emptyList(),
                )
            }
        }

        private fun hash(file: File): String = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    }
}
