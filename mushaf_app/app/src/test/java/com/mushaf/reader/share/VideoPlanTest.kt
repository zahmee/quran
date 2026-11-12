package com.mushaf.reader.share

import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.recitation.AyahTiming
import com.mushaf.reader.data.recitation.SurahTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlanTest {

    private fun ayah(n: Int, page: Int) = AyahMarker(
        page = page, verseKey = "2:$n", surahNumber = 2, surahNameAr = "البقرة", surahNameEn = "",
        ayahNumber = n, textUthmani = "", textImlaei = "", juz = 3, hizb = 5, rub = 1,
        isSajdah = false, sajdahNumber = 0, centerX = 0f, centerY = 0f, rects = emptyList(),
    )

    // 255 and 256 on page 42, 257 on page 43.
    private val timing = SurahTiming(
        2,
        listOf(
            AyahTiming(255, 100_000, 154_000),
            AyahTiming(256, 154_000, 180_000),
            AyahTiming(257, 180_000, 210_000),
        ),
    )
    private val run = listOf(ayah(255, 42), ayah(256, 42), ayah(257, 43))
    private val range = ClipRange(100_000, 210_000)

    @Test
    fun `one picture per ayah, each lit for its own recitation`() {
        assertEquals(
            listOf(
                VideoSegment(42, "2:255", 54_000),
                VideoSegment(42, "2:256", 26_000),
                VideoSegment(43, "2:257", 30_000),
            ),
            VideoPlan.segments(run, timing, range, highlight = true),
        )
    }

    @Test
    fun `without highlighting it is one picture per page`() {
        assertEquals(
            listOf(VideoSegment(42, null, 80_000), VideoSegment(43, null, 30_000)),
            VideoPlan.segments(run, timing, range, highlight = false),
        )
    }

    @Test
    fun `the pictures last exactly as long as the clip`() {
        val withOpening = ClipRange(0, 210_000)
        val segments = VideoPlan.segments(run, timing, withOpening, highlight = true)
        assertEquals(210_000, segments.sumOf { it.durationMs })
        // The opening belongs to the first ayah's picture.
        assertEquals(154_000, segments.first().durationMs)
    }

    @Test
    fun `a flash of a picture is folded into the one before it`() {
        val tight = SurahTiming(
            2,
            listOf(AyahTiming(255, 0, 5_000), AyahTiming(256, 5_000, 5_300), AyahTiming(257, 5_300, 9_000)),
        )
        val segments = VideoPlan.segments(run, tight, ClipRange(0, 9_000), highlight = true)
        assertTrue(segments.none { it.durationMs < VideoPlan.MIN_SEGMENT_MS })
        assertEquals(9_000, segments.sumOf { it.durationMs })
        assertEquals(listOf("2:255", "2:257"), segments.map { it.highlight })
    }

    @Test
    fun `a single ayah is a single picture`() {
        val one = VideoPlan.segments(run.take(1), timing, ClipRange(100_000, 154_000), highlight = true)
        assertEquals(listOf(VideoSegment(42, "2:255", 54_000)), one)
    }

    @Test
    fun `starts out of order never make a negative length`() {
        val jumbled = SurahTiming(
            2,
            listOf(AyahTiming(255, 100_000, 154_000), AyahTiming(256, 190_000, 200_000), AyahTiming(257, 180_000, 210_000)),
        )
        val segments = VideoPlan.segments(run, jumbled, range, highlight = true)
        assertTrue(segments.all { it.durationMs > 0 })
        assertEquals(110_000, segments.sumOf { it.durationMs })
    }
}
