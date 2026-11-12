package com.mushaf.reader.share

import com.mushaf.reader.data.recitation.AyahTiming
import com.mushaf.reader.data.recitation.SurahTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioClipsTest {

    // Opens with 6 s of isti'adha and basmala, listed as "ayah 0", before ayah 1.
    private val opened = SurahTiming(
        2,
        listOf(
            AyahTiming(0, 0, 6_000),
            AyahTiming(1, 6_000, 9_000),
            AyahTiming(2, 9_000, 20_000),
            AyahTiming(3, 20_000, 31_500),
        ),
    )

    // Ayah 1 starts the file.
    private val plain = SurahTiming(
        1,
        listOf(AyahTiming(1, 0, 5_000), AyahTiming(2, 5_000, 9_000)),
    )

    @Test
    fun `one ayah runs from its start to its end`() {
        assertEquals(ClipRange(9_000, 20_000), AudioClips.range(opened, 2, 2, withOpening = true))
    }

    @Test
    fun `a run runs from its first ayah's start to its last ayah's end`() {
        assertEquals(ClipRange(9_000, 31_500), AudioClips.range(opened, 2, 3, withOpening = false))
    }

    @Test
    fun `from ayah 1 the opening is taken in only when asked`() {
        assertEquals(ClipRange(0, 20_000), AudioClips.range(opened, 1, 2, withOpening = true))
        assertEquals(ClipRange(6_000, 20_000), AudioClips.range(opened, 1, 2, withOpening = false))
    }

    @Test
    fun `the opening is never added to a run that starts later`() {
        assertEquals(ClipRange(20_000, 31_500), AudioClips.range(opened, 3, 3, withOpening = true))
    }

    @Test
    fun `an opening is offered only when the reciter has one`() {
        assertTrue(AudioClips.hasOpening(opened))
        assertFalse(AudioClips.hasOpening(plain))
    }

    @Test
    fun `an ayah missing from the table gives no clip`() {
        assertNull(AudioClips.range(plain, 1, 7, withOpening = true))
    }

    @Test
    fun `an ayah with no length gives no clip`() {
        val flat = SurahTiming(3, listOf(AyahTiming(1, 4_000, 4_000)))
        assertNull(AudioClips.range(flat, 1, 1, withOpening = false))
    }

    @Test
    fun `the fade rises at the start, holds, and falls at the end`() {
        val d = 10_000_000L
        assertEquals(0f, AudioClips.fadeGain(0, d, 100_000, 300_000), 0f)
        assertEquals(0.5f, AudioClips.fadeGain(50_000, d, 100_000, 300_000), 1e-6f)
        assertEquals(1f, AudioClips.fadeGain(5_000_000, d, 100_000, 300_000), 0f)
        assertEquals(0.5f, AudioClips.fadeGain(d - 150_000, d, 100_000, 300_000), 1e-6f)
        assertEquals(0f, AudioClips.fadeGain(d, d, 100_000, 300_000), 0f)
    }

    @Test
    fun `a clip shorter than both fades never reaches full loudness`() {
        val d = 200_000L
        assertTrue(AudioClips.fadeGain(100_000, d, 100_000, 300_000) < 1f)
    }
}
