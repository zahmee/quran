package com.mushaf.reader.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Guards the day arithmetic behind the week chart, the month totals and the streak.
 *
 * These used to be computed as `dayStart - i * 86_400_000`. That is correct only where every day is
 * exactly 24 hours long. Across a DST change the constant lands an hour off real midnight, the
 * lookup keyed by day start misses, and a day of reading silently disappears from the stats.
 *
 * Europe/Berlin is used because the EU rule is fixed and easy to reason about: DST starts on the
 * last Sunday of March (a 23-hour day) and ends on the last Sunday of October (a 25-hour day).
 */
class DayGridTest {

    private val berlin: TimeZone = TimeZone.getTimeZone("Europe/Berlin")
    private val hour = 3_600_000L
    private val naiveDay = 86_400_000L

    private fun at(year: Int, month: Int, day: Int, hourOfDay: Int = 12): Long =
        Calendar.getInstance(berlin).apply {
            clear()
            set(year, month, day, hourOfDay, 0, 0)
        }.timeInMillis

    @Test
    fun `startOf returns local midnight and is idempotent`() {
        val grid = DayGrid(berlin)
        val midnight = grid.startOf(at(2026, Calendar.JUNE, 10, hourOfDay = 23))
        assertEquals(at(2026, Calendar.JUNE, 10, hourOfDay = 0), midnight)
        assertEquals(midnight, grid.startOf(midnight))
    }

    @Test
    fun `every instant within a day maps to the same day start`() {
        val grid = DayGrid(berlin)
        val expected = at(2026, Calendar.JUNE, 10, hourOfDay = 0)
        for (h in 0..23) {
            assertEquals("hour $h", expected, grid.startOf(at(2026, Calendar.JUNE, 10, hourOfDay = h)))
        }
    }

    @Test
    fun `stepping over the spring forward day stays on midnight`() {
        val grid = DayGrid(berlin)
        // 2026-03-29 is 23 hours long in Berlin.
        val dstDay = grid.startOf(at(2026, Calendar.MARCH, 29))
        val nextDay = grid.startOf(at(2026, Calendar.MARCH, 30))

        assertEquals(nextDay, grid.plusDays(dstDay, 1))
        assertEquals(dstDay, grid.plusDays(nextDay, -1))
        assertEquals(23 * hour, nextDay - dstDay)
        // The old constant-day arithmetic would have landed here — an hour past midnight.
        assertNotEquals(nextDay - naiveDay, dstDay)
    }

    @Test
    fun `stepping over the fall back day stays on midnight`() {
        val grid = DayGrid(berlin)
        // 2026-10-25 is 25 hours long in Berlin.
        val dstDay = grid.startOf(at(2026, Calendar.OCTOBER, 25))
        val nextDay = grid.startOf(at(2026, Calendar.OCTOBER, 26))

        assertEquals(nextDay, grid.plusDays(dstDay, 1))
        assertEquals(dstDay, grid.plusDays(nextDay, -1))
        assertEquals(25 * hour, nextDay - dstDay)
        assertNotEquals(nextDay - naiveDay, dstDay)
    }

    /** The week chart walks back six days; every key it produces has to be a real day start. */
    @Test
    fun `walking a week back across a DST change hits seven distinct day starts`() {
        val grid = DayGrid(berlin)
        val today = grid.startOf(at(2026, Calendar.APRIL, 1))
        val week = (6 downTo 0).map { grid.plusDays(today, -it) }

        assertEquals(7, week.toSet().size)
        assertEquals(week, week.map { grid.startOf(it) })
        assertEquals(today, week.last())
        assertEquals(grid.startOf(at(2026, Calendar.MARCH, 26)), week.first())
    }

    @Test
    fun `stepping across a month and a year boundary`() {
        val grid = DayGrid(berlin)
        assertEquals(
            grid.startOf(at(2026, Calendar.MARCH, 1)),
            grid.plusDays(grid.startOf(at(2026, Calendar.FEBRUARY, 28)), 1),
        )
        assertEquals(
            grid.startOf(at(2027, Calendar.JANUARY, 1)),
            grid.plusDays(grid.startOf(at(2026, Calendar.DECEMBER, 31)), 1),
        )
    }
}
