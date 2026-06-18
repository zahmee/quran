package com.mushaf.reader.data.stats

import android.content.Context
import java.util.Calendar

/** Aggregated reading statistics for display. */
data class ReadingStats(
    val sessionCount: Int,
    val totalDurationMs: Long,
    val totalPagesRead: Int,
    val lastSessionDurationMs: Long,
    val lastSessionPages: Int,
    val currentPage: Int,
    val totalPages: Int,
    val khatmaPercent: Int,
    val bookmarkPage: Int?,
    val bookmarkPercent: Int?,
)

/** One day's reading aggregate (used for charts and comparisons). */
data class DayStat(
    val dayStartMillis: Long,
    val pages: Int,
    val durationMs: Long,
    val sessions: Int,
)

/** Rich statistics for the full-screen stats view. */
data class FullStats(
    val todayPages: Int,
    val todayDurationMs: Long,
    val todaySessions: Int,
    val yesterdayPages: Int,
    val weekPages: Int,
    val weekDurationMs: Long,
    val weekActiveDays: Int,
    val weekAvgPages: Int,
    val monthPages: Int,
    val monthDurationMs: Long,
    val yearPages: Int,
    val streakDays: Int,
    val bestDayPages: Int,
    val longestSessionMs: Long,
    val activeDays: Int,
    val totalSessions: Int,
    val totalPages: Int,
    val totalDurationMs: Long,
    val last7Days: List<DayStat>,
    val khatmaPercent: Int,
    val currentPage: Int,
    val totalPagesInQuran: Int,
    val bookmarkPage: Int?,
    val bookmarkPercent: Int?,
)

/** Records reading sessions and produces aggregated stats. */
class StatsRepository(context: Context) {

    private val dao = AppDatabase.get(context).sessionDao()

    suspend fun commitSession(
        startedAt: Long,
        endedAt: Long,
        startPage: Int,
        endPage: Int,
        pagesRead: Int,
    ) {
        dao.insert(
            SessionEntity(
                startedAt = startedAt,
                endedAt = endedAt,
                startPage = startPage,
                endPage = endPage,
                pagesRead = pagesRead,
            )
        )
    }

    suspend fun summary(
        currentPage: Int,
        totalPages: Int,
        bookmarkPage: Int?,
    ): ReadingStats {
        val last = dao.lastSession()
        val progressPage = bookmarkPage ?: currentPage
        return ReadingStats(
            sessionCount = dao.sessionCount(),
            totalDurationMs = dao.totalDurationMs(),
            totalPagesRead = dao.totalPagesRead(),
            lastSessionDurationMs = last?.let { it.endedAt - it.startedAt } ?: 0L,
            lastSessionPages = last?.pagesRead ?: 0,
            currentPage = currentPage,
            totalPages = totalPages,
            khatmaPercent = percent(progressPage, totalPages),
            bookmarkPage = bookmarkPage,
            bookmarkPercent = bookmarkPage?.let { percent(it, totalPages) },
        )
    }

    suspend fun allSessions(): List<SessionEntity> = dao.allSessions()

    /** Rich, motivating stats computed in-memory from all sessions. */
    suspend fun fullStats(currentPage: Int, totalPages: Int, bookmarkPage: Int?): FullStats {
        val sessions = dao.allSessions()
        val cal = Calendar.getInstance()
        val dayMs = 86_400_000L

        fun dayStart(t: Long): Long {
            cal.timeInMillis = t
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        data class Acc(var pages: Int = 0, var durationMs: Long = 0, var sessions: Int = 0)
        val byDay = HashMap<Long, Acc>()
        for (s in sessions) {
            val acc = byDay.getOrPut(dayStart(s.startedAt)) { Acc() }
            acc.pages += s.pagesRead
            acc.durationMs += (s.endedAt - s.startedAt).coerceAtLeast(0)
            acc.sessions += 1
        }

        val now = System.currentTimeMillis()
        val today = dayStart(now)
        val todayAcc = byDay[today]
        val yesterdayAcc = byDay[today - dayMs]

        val last7 = ArrayList<DayStat>(7)
        var weekPages = 0
        var weekDuration = 0L
        var weekActiveDays = 0
        for (i in 6 downTo 0) {
            val k = today - i * dayMs
            val a = byDay[k]
            last7.add(DayStat(k, a?.pages ?: 0, a?.durationMs ?: 0L, a?.sessions ?: 0))
            weekPages += a?.pages ?: 0
            weekDuration += a?.durationMs ?: 0L
            if ((a?.sessions ?: 0) > 0) weekActiveDays++
        }

        cal.timeInMillis = now
        val curMonth = cal.get(Calendar.MONTH)
        val curYear = cal.get(Calendar.YEAR)
        var monthPages = 0
        var monthDuration = 0L
        var yearPages = 0
        for ((k, a) in byDay) {
            cal.timeInMillis = k
            if (cal.get(Calendar.YEAR) == curYear) {
                yearPages += a.pages
                if (cal.get(Calendar.MONTH) == curMonth) {
                    monthPages += a.pages
                    monthDuration += a.durationMs
                }
            }
        }

        // Streak: consecutive days with activity, counting back from today (or yesterday).
        var streak = 0
        var k = today
        if ((byDay[k]?.sessions ?: 0) == 0) k -= dayMs
        while ((byDay[k]?.sessions ?: 0) > 0) {
            streak++
            k -= dayMs
        }

        return FullStats(
            todayPages = todayAcc?.pages ?: 0,
            todayDurationMs = todayAcc?.durationMs ?: 0L,
            todaySessions = todayAcc?.sessions ?: 0,
            yesterdayPages = yesterdayAcc?.pages ?: 0,
            weekPages = weekPages,
            weekDurationMs = weekDuration,
            weekActiveDays = weekActiveDays,
            weekAvgPages = weekPages / 7,
            monthPages = monthPages,
            monthDurationMs = monthDuration,
            yearPages = yearPages,
            streakDays = streak,
            bestDayPages = byDay.values.maxOfOrNull { it.pages } ?: 0,
            longestSessionMs = sessions.maxOfOrNull { (it.endedAt - it.startedAt).coerceAtLeast(0) } ?: 0L,
            activeDays = byDay.size,
            totalSessions = sessions.size,
            totalPages = sessions.sumOf { it.pagesRead },
            totalDurationMs = sessions.sumOf { (it.endedAt - it.startedAt).coerceAtLeast(0) },
            last7Days = last7,
            khatmaPercent = percent(bookmarkPage ?: currentPage, totalPages),
            currentPage = currentPage,
            totalPagesInQuran = totalPages,
            bookmarkPage = bookmarkPage,
            bookmarkPercent = bookmarkPage?.let { percent(it, totalPages) },
        )
    }

    private fun percent(page: Int, total: Int): Int =
        if (total <= 0) 0 else (page.coerceIn(0, total) * 100 / total)
}
