package com.mushaf.reader.share

import org.junit.Assert.assertEquals
import org.junit.Test

class AyahSpanTest {

    // A page closing surah 2 (indices 0-3) and opening surah 3 (indices 4-6).
    private val surahs = listOf(2, 2, 2, 2, 3, 3, 3)

    @Test
    fun `dragging the end forward extends the span`() {
        val d = AyahSpan(1, 1).drag(movingStart = false, target = 3, surahs = surahs)
        assertEquals(AyahSpan(1, 3), d.span)
        assertEquals(false, d.movingStart)
    }

    @Test
    fun `dragging the start back extends the span`() {
        val d = AyahSpan(2, 3).drag(movingStart = true, target = 0, surahs = surahs)
        assertEquals(AyahSpan(0, 3), d.span)
        assertEquals(true, d.movingStart)
    }

    @Test
    fun `dragging the start past the end hands the finger to the end`() {
        val d = AyahSpan(1, 2).drag(movingStart = true, target = 3, surahs = surahs)
        assertEquals(AyahSpan(2, 3), d.span)
        assertEquals(false, d.movingStart)
    }

    @Test
    fun `dragging the end before the start hands the finger to the start`() {
        val d = AyahSpan(2, 3).drag(movingStart = false, target = 0, surahs = surahs)
        assertEquals(AyahSpan(0, 2), d.span)
        assertEquals(true, d.movingStart)
    }

    @Test
    fun `dragging onto the anchor collapses to one ayah and keeps the moving end`() {
        assertEquals(AyahSpan.Drag(AyahSpan(2, 2), true), AyahSpan(1, 2).drag(true, 2, surahs))
        assertEquals(AyahSpan.Drag(AyahSpan(1, 1), false), AyahSpan(1, 2).drag(false, 1, surahs))
    }

    @Test
    fun `a drag stops at the surah boundary`() {
        assertEquals(AyahSpan(1, 3), AyahSpan(1, 2).drag(false, 6, surahs).span)
        assertEquals(AyahSpan(4, 5), AyahSpan(5, 5).drag(true, 0, surahs).span)
    }

    @Test
    fun `a tap before the span extends its start, after it its end`() {
        assertEquals(AyahSpan(0, 2), AyahSpan(1, 2).tap(0, surahs))
        assertEquals(AyahSpan(1, 3), AyahSpan(1, 2).tap(3, surahs))
    }

    @Test
    fun `a tap inside the span pulls its end back`() {
        assertEquals(AyahSpan(0, 1), AyahSpan(0, 3).tap(1, surahs))
        assertEquals(AyahSpan(0, 0), AyahSpan(0, 3).tap(0, surahs))
    }

    @Test
    fun `a tap in another surah starts a new span there`() {
        assertEquals(AyahSpan(5, 5), AyahSpan(1, 3).tap(5, surahs))
    }
}
