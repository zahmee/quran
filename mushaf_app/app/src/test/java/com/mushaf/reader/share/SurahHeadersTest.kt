package com.mushaf.reader.share

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SurahHeadersTest {

    /** Each surah's first ayah: its page and the top of its first line. */
    private val openings: Map<Int, Pair<Int, Int>> by lazy {
        val records = JSONObject(File("src/main/assets/data/ayah_regions.json").readText()).getJSONArray("records")
        (0 until records.length()).map { records.getJSONObject(it) }
            .filter { it.getInt("ayah_number") == 1 }
            .associate { r ->
                val rects = r.getJSONArray("rects")
                r.getInt("surah_number") to (r.getInt("page") to (0 until rects.length()).minOf { rects.getJSONObject(it).getInt("y") })
            }
    }

    @Test
    fun `every header sits on its surah's opening page, above its first ayah`() {
        for (surah in 3..114) {
            val header = SurahHeaders.source(surah) as SurahHeaders.OnPage
            val (page, firstLine) = openings.getValue(surah)
            assertEquals("surah $surah", page, header.page)
            assertTrue("surah $surah: header runs into the first ayah", header.bottom < firstLine)
            // With the basmala between them, no more than two lines up; al-Tawba has none.
            assertTrue("surah $surah: header too far above", firstLine - header.top < 2 * 118 + 40)
        }
    }

    @Test
    fun `every header is one printed line tall and spans the text block`() {
        for (surah in 3..114) {
            val h = SurahHeaders.source(surah) as SurahHeaders.OnPage
            assertTrue("surah $surah: height ${h.bottom - h.top}", h.bottom - h.top in 120..128)
            assertTrue("surah $surah: width ${h.right - h.left}", h.right - h.left in 1080..1095)
        }
    }

    @Test
    fun `the two decorated openings use the headers made for them, and those ship`() {
        for (surah in 1..2) {
            val asset = SurahHeaders.source(surah) as SurahHeaders.Asset
            assertTrue(asset.path, File("src/main/assets/${asset.path}").isFile)
        }
    }
}
