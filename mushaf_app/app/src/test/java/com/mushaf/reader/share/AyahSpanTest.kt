package com.mushaf.reader.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AyahSpanTest {

    // One page closing surah 2 (indices 0-3) and opening surah 3 (indices 4-6).
    private val page = SpanRules(surahs = listOf(2, 2, 2, 2, 3, 3, 3))

    // One surah over five pages, two ayahs a page; runs of at most three pages.
    private val book = SpanRules(
        surahs = List(10) { 2 },
        pages = listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5),
        maxPages = 3,
    )

    @Test
    fun `dragging the end forward extends the span`() {
        val d = AyahSpan(1, 1).drag(movingStart = false, target = 3, rules = page)
        assertEquals(AyahSpan(1, 3), d.span)
        assertEquals(false, d.movingStart)
    }

    @Test
    fun `dragging the start back extends the span`() {
        val d = AyahSpan(2, 3).drag(movingStart = true, target = 0, rules = page)
        assertEquals(AyahSpan(0, 3), d.span)
        assertEquals(true, d.movingStart)
    }

    @Test
    fun `dragging the start past the end hands the finger to the end`() {
        val d = AyahSpan(1, 2).drag(movingStart = true, target = 3, rules = page)
        assertEquals(AyahSpan(2, 3), d.span)
        assertEquals(false, d.movingStart)
    }

    @Test
    fun `dragging the end before the start hands the finger to the start`() {
        val d = AyahSpan(2, 3).drag(movingStart = false, target = 0, rules = page)
        assertEquals(AyahSpan(0, 2), d.span)
        assertEquals(true, d.movingStart)
    }

    @Test
    fun `dragging onto the anchor collapses to one ayah and keeps the moving end`() {
        assertEquals(AyahSpan.Drag(AyahSpan(2, 2), true), AyahSpan(1, 2).drag(true, 2, page))
        assertEquals(AyahSpan.Drag(AyahSpan(1, 1), false), AyahSpan(1, 2).drag(false, 1, page))
    }

    @Test
    fun `a drag stops at the surah boundary`() {
        assertEquals(AyahSpan(1, 3), AyahSpan(1, 2).drag(false, 6, page).span)
        assertEquals(AyahSpan(4, 5), AyahSpan(5, 5).drag(true, 0, page).span)
    }

    @Test
    fun `a tap before the span extends its start, after it its end`() {
        assertEquals(AyahSpan(0, 2), AyahSpan(1, 2).tap(0, page))
        assertEquals(AyahSpan(1, 3), AyahSpan(1, 2).tap(3, page))
    }

    @Test
    fun `a tap inside the span pulls its end back`() {
        assertEquals(AyahSpan(0, 1), AyahSpan(0, 3).tap(1, page))
        assertEquals(AyahSpan(0, 0), AyahSpan(0, 3).tap(0, page))
    }

    @Test
    fun `a tap in another surah starts a new span there`() {
        assertEquals(AyahSpan(5, 5), AyahSpan(1, 3).tap(5, page))
    }

    @Test
    fun `a tap reaches onto the following pages up to the limit`() {
        // From page 1 to page 3: three pages.
        assertEquals(AyahSpan(1, 5), AyahSpan(1, 1).tap(5, book))
    }

    @Test
    fun `a tap that would run past the page limit is refused, not cut short`() {
        // Page 1 to page 4 is four pages.
        assertNull(AyahSpan(1, 1).tap(6, book))
        assertNull(AyahSpan(8, 9).tap(3, book))
    }

    @Test
    fun `a drag stops at the last ayah within the page limit`() {
        // Anchored on page 2, the end may reach page 4 at most.
        assertEquals(AyahSpan(2, 7), AyahSpan(2, 2).drag(false, 9, book).span)
        // Anchored on page 5, the start may reach back to page 3.
        assertEquals(AyahSpan(4, 9), AyahSpan(9, 9).drag(true, 0, book).span)
    }

    @Test
    fun `counts pages from the run's first ayah to its last`() {
        assertEquals(1, book.pageCount(0, 1))
        assertEquals(3, book.pageCount(1, 5))
    }
}
