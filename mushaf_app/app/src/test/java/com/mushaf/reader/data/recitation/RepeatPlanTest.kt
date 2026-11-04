package com.mushaf.reader.data.recitation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatPlanTest {

    // Al-Fatihah by Alafasy: ayah 1 starts after the isti'adha at 6.42 s.
    private val fatihah = SurahTiming(
        1,
        listOf(
            AyahTiming(1, 6420, 11120), AyahTiming(2, 11120, 17640), AyahTiming(3, 17640, 21280),
            AyahTiming(4, 21280, 26800), AyahTiming(5, 26800, 33280), AyahTiming(6, 33280, 38960),
            AyahTiming(7, 38960, 51200),
        ),
    )

    @Test
    fun `the user's example - Al-Fatihah ayah by ayah, each seven times`() {
        val clips = RepeatPlanner.clips(RepeatPlan(1, 1, 7, ayahTimes = 7, rangeTimes = 1), fatihah, 123)
        assertEquals(49, clips.size)
        assertEquals(List(7) { 1 } + List(7) { 2 }, clips.take(14).map { it.ayah })
        assertEquals((1..7).toList(), clips.take(7).map { it.ayahPass })
        // A repeated ayah 1 is the ayah alone: the isti'adha before it is not repeated.
        assertEquals(6420L, clips.first().startMs)
        assertEquals(51200L, clips.last().endMs)
    }

    @Test
    fun `listening to one ayah is a single clip cut at its timing`() {
        val clips = RepeatPlanner.clips(RepeatPlan.single(AyahRef(1, 3)), fatihah, 123)
        assertEquals(1, clips.size)
        assertEquals(17640L to 21280L, clips[0].startMs to clips[0].endMs)
    }

    @Test
    fun `the passage repeats as a whole after each ayah's own repetitions`() {
        val clips = RepeatPlanner.clips(RepeatPlan(1, 2, 3, ayahTimes = 2, rangeTimes = 3), fatihah, 5)
        assertEquals(listOf(2, 2, 3, 3, 2, 2, 3, 3, 2, 2, 3, 3), clips.map { it.ayah })
        assertEquals(listOf(1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3), clips.map { it.rangePass })
    }

    @Test
    fun `an endless plan is one pass for the player to loop`() {
        val plan = RepeatPlan(1, 1, 2, ayahTimes = 3, rangeTimes = RepeatPlan.ENDLESS)
        assertEquals(6, RepeatPlanner.clips(plan, fatihah, 1).size)
        assertEquals(6L, RepeatPlanner.clipCount(plan))
    }

    @Test
    fun `a clip survives the trip through its media id`() {
        val clip = RepeatPlanner.clips(RepeatPlan(1, 4, 4, 7, 2), fatihah, 123)[3]
        val back = AyahClip.fromMediaId(clip.mediaId())!!
        assertEquals(clip.copy(startMs = 0, endMs = 0), back)
        assertNull(AyahClip.fromMediaId("123:2"))
        assertNull(AyahClip.fromMediaId("clip:1:2:x:1:1:1:1"))
    }

    @Test
    fun `next and previous skip the remaining repetitions of an ayah`() {
        val clips = RepeatPlanner.clips(RepeatPlan(1, 1, 3, ayahTimes = 3, rangeTimes = 2), fatihah, 1)
        // Layout: pass 1 = [1,1,1,2,2,2,3,3,3] (0..8), pass 2 = the same at 9..17.
        assertEquals(3, RepeatPlanner.nextAyahStart(clips, 1))   // mid ayah 1 -> ayah 2
        assertEquals(9, RepeatPlanner.nextAyahStart(clips, 7))   // ayah 3 -> pass 2's ayah 1
        assertNull(RepeatPlanner.nextAyahStart(clips, 17))
        assertEquals(0, RepeatPlanner.previousAyahStart(clips, 4))  // mid ayah 2 -> ayah 1
        assertEquals(0, RepeatPlanner.previousAyahStart(clips, 2))  // first ayah -> its start
        assertEquals(6, RepeatPlanner.previousAyahStart(clips, 10)) // pass 2 ayah 1 -> pass 1 ayah 3
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a playlist beyond the limit is refused`() {
        val baqarah = SurahTiming(2, (1..286).map { AyahTiming(it, it * 1000L, it * 1000L + 900) })
        RepeatPlanner.clips(RepeatPlan(2, 1, 286, ayahTimes = 20, rangeTimes = 1), baqarah, 1)
    }
}
