package com.mushaf.reader.data.stats

import java.util.Calendar
import java.util.TimeZone

/**
 * Buckets instants into local calendar days, and steps between those days.
 *
 * Days are NOT a fixed 86_400_000 ms apart: in a time zone that observes DST one day a year is 23
 * hours long and another is 25. Adding a constant day in milliseconds therefore drifts by an hour
 * across such a boundary and stops landing on midnight, so a lookup keyed by day start silently
 * misses — a reading day disappears from the week chart and the streak breaks. Stepping through
 * [Calendar] keeps every key on real local midnight instead.
 *
 * Not thread-safe: it holds one [Calendar]. Create one per computation.
 *
 * Pure JVM (no Android types) so it can be unit-tested against a fixed time zone.
 */
internal class DayGrid(timeZone: TimeZone = TimeZone.getDefault()) {

    private val cal: Calendar = Calendar.getInstance(timeZone)

    /** Local midnight of the day [millis] falls in. */
    fun startOf(millis: Long): Long {
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** The day start [days] whole days after [dayStartMillis] (negative counts backwards). */
    fun plusDays(dayStartMillis: Long, days: Int): Long {
        cal.timeInMillis = dayStartMillis
        cal.add(Calendar.DAY_OF_YEAR, days)
        return cal.timeInMillis
    }
}
