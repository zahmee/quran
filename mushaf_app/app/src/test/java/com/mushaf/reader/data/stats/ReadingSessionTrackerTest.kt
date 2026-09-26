package com.mushaf.reader.data.stats

import org.junit.Assert.*
import org.junit.Test

class ReadingSessionTrackerTest {
    private var elapsed = 0L
    private var wall = 1_700_000_000_000L
    private val visited = mutableListOf<Int>()
    private val read = mutableListOf<Int>()
    private val tracker = ReadingSessionTracker(10, { elapsed }, { wall }, visited::add, read::add)

    private fun advance(ms: Long) { elapsed += ms; wall += ms }

    @Test
    fun `settings pause duration without splitting the session or counting a page twice`() {
        tracker.setReading(true)
        val start = wall
        advance(10_000)
        tracker.setReading(false)
        advance(120_000)
        assertEquals(10_000L, tracker.durationMs())
        assertTrue(read.isEmpty())
        tracker.setReading(true)
        advance(10_000)
        val session = tracker.finish()!!
        assertEquals(start, session.startedAt)
        assertEquals(140_000L, session.endedAt - session.startedAt)
        assertEquals(20_000L, session.durationMs)
        assertEquals(1, session.pagesRead)
        assertEquals(listOf(10), read)
    }

    @Test
    fun `a page behind search is not read after twenty seconds`() {
        tracker.setReading(true)
        advance(2_000)
        tracker.setReading(false)
        advance(60_000)
        tracker.checkpoint()
        assertTrue(read.isEmpty())
        assertEquals(2_000L, tracker.finish()!!.durationMs)
    }

    @Test
    fun `background while another screen is open ends the session at the last reading instant`() {
        tracker.setReading(true)
        advance(5_000)
        tracker.setReading(false)
        val lastReading = wall
        advance(60_000)
        val session = tracker.finish()!!
        assertEquals(lastReading, session.endedAt)
        assertEquals(5_000L, session.durationMs)
        assertNull(tracker.finish())
    }

    @Test
    fun `returning to backup after a file picker does not start a phantom session`() {
        tracker.setReading(true)
        advance(3_000)
        tracker.setReading(false)
        assertNotNull(tracker.finish())
        advance(120_000)
        tracker.setReading(false)
        assertNull(tracker.finish())
        assertNull(tracker.startedAt)
    }

    @Test
    fun `duplicate resume and pause events are idempotent`() {
        tracker.setReading(true)
        advance(10_000)
        tracker.setReading(true)
        advance(5_000)
        tracker.setReading(false)
        tracker.setReading(false)
        advance(30_000)
        assertEquals(15_000L, tracker.finish()!!.durationMs)
        assertTrue(read.isEmpty())
    }

    @Test
    fun `hidden jumps receive no page or dwell credit until the reader returns`() {
        tracker.setReading(true)
        advance(3_000)
        tracker.setReading(false)
        tracker.changePage(30)
        advance(60_000)
        assertFalse(visited.contains(30))
        tracker.setReading(true)
        advance(20_000)
        val session = tracker.finish()!!
        assertEquals(2, session.pagesRead)
        assertEquals(30, session.endPage)
        assertEquals(listOf(30), read)
    }

    @Test
    fun `returning to an earlier page preserves the correct end page`() {
        tracker.setReading(true)
        advance(1_000)
        tracker.changePage(11)
        advance(1_000)
        tracker.changePage(10)
        advance(1_000)
        val session = tracker.finish()!!
        assertEquals(10, session.endPage)
        assertEquals(2, session.pagesRead)
    }

    @Test
    fun `each page earns dwell independently and repeated page notifications retain dwell`() {
        tracker.setReading(true)
        advance(10_000)
        tracker.changePage(10)
        advance(10_000)
        tracker.changePage(11)
        advance(10_000)
        tracker.finish()
        assertEquals(listOf(10), read)
    }

    @Test
    fun `new khatma on the same page discards previous dwell`() {
        tracker.changePage(1)
        tracker.setReading(true)
        advance(19_000)
        tracker.resetPage(1)
        advance(1_000)
        tracker.checkpoint()
        assertTrue(read.isEmpty())
        advance(19_000)
        tracker.finish()
        assertEquals(listOf(1), read)
    }

    @Test
    fun `restoring while paused never starts reading the imported page behind backup`() {
        tracker.setReading(true)
        advance(19_000)
        tracker.setReading(false)
        tracker.reset(42)
        advance(60_000)
        assertEquals(0L, tracker.durationMs())
        assertTrue(read.isEmpty())
        assertFalse(visited.contains(42))
        tracker.setReading(true)
        advance(1_000)
        val session = tracker.finish()!!
        assertEquals(42, session.startPage)
        assertEquals(1_000L, session.durationMs)
        assertTrue(read.isEmpty())
    }

    @Test
    fun `clearing statistics discards the uncommitted old session`() {
        tracker.setReading(true)
        advance(10_000)
        tracker.setReading(false)
        tracker.reset(10)
        tracker.setReading(true)
        advance(2_000)
        assertEquals(2_000L, tracker.finish()!!.durationMs)
    }

    @Test
    fun `wall clock changes do not alter elapsed reading time`() {
        tracker.setReading(true)
        advance(5_000)
        wall -= 3_600_000
        assertEquals(5_000L, tracker.durationMs())
        val session = tracker.finish()!!
        assertEquals(5_000L, session.durationMs)
        assertTrue(session.endedAt >= session.startedAt + session.durationMs)
    }

    @Test
    fun `no reading and subsecond flashes do not create sessions`() {
        advance(60_000)
        assertNull(tracker.finish())
        tracker.setReading(true)
        advance(500)
        assertNull(tracker.finish())
    }
}
