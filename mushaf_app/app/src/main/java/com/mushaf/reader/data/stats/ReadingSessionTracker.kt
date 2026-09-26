package com.mushaf.reader.data.stats

/**
 * One foreground session can contain several reading intervals. Menus, other screens and lost
 * focus pause both clocks without creating another session or counting the same page twice.
 * Durations use a monotonic clock; the wall clock is only used for the session's dates.
 */
class ReadingSessionTracker(
    initialPage: Int,
    private val elapsedRealtime: () -> Long,
    private val wallTimeMillis: () -> Long,
    private val onPageVisited: (Int) -> Unit,
    private val onPageRead: (Int) -> Unit,
    private val readDwellMs: Long = 20_000L,
) {
    var startedAt: Long? = null
        private set
    var isReading: Boolean = false
        private set

    private var page = initialPage
    private var startPage = initialPage
    private var lastReadPage = initialPage
    private var lastReadAt = 0L
    private var runningSince = 0L
    private var accumulatedMs = 0L
    private var pageDwellMs = 0L
    private var pageCredited = false
    private val visitedPages = linkedSetOf<Int>()

    fun durationMs(): Long = accumulatedMs +
        if (isReading) (elapsedRealtime() - runningSince).coerceAtLeast(0L) else 0L

    fun setReading(reading: Boolean) {
        if (reading == isReading) return
        if (!reading) {
            checkpoint()
            isReading = false
            return
        }
        if (startedAt == null) {
            startedAt = wallTimeMillis()
            startPage = page
        }
        runningSince = elapsedRealtime()
        isReading = true
        visitPage()
    }

    /** A programmatic jump behind another screen changes position, but earns no reading credit. */
    fun changePage(nextPage: Int) {
        if (nextPage == page) return
        checkpoint()
        page = nextPage
        pageDwellMs = 0L
        pageCredited = false
        if (isReading) visitPage()
    }

    /** Settle only time actually spent reading, leaving a paused clock paused. */
    fun checkpoint() = accumulate(creditPage = true)

    private fun accumulate(creditPage: Boolean) {
        if (!isReading) return
        val now = elapsedRealtime()
        val duration = (now - runningSince).coerceAtLeast(0L)
        accumulatedMs += duration
        pageDwellMs += duration
        runningSince = now
        lastReadAt = wallTimeMillis()
        if (creditPage && !pageCredited && pageDwellMs >= readDwellMs) {
            pageCredited = true
            onPageRead(page)
        }
    }

    private fun visitPage() {
        lastReadPage = page
        visitedPages.add(page)
        onPageVisited(page)
    }

    /** A new khatma must not inherit the previous page's dwell, even when already on page 1. */
    fun resetPage(nextPage: Int) {
        accumulate(creditPage = false)
        page = nextPage
        pageDwellMs = 0L
        pageCredited = false
        if (isReading) visitPage()
    }

    /** Finish once on background/export. A visit to settings alone produces no reading session. */
    fun finish(): SessionEntity? {
        setReading(false)
        val start = startedAt
        val result = if (start != null && accumulatedMs >= 1_000L) {
            SessionEntity(
                startedAt = start,
                // Keep the interval valid if the user moves the device's wall clock backwards.
                endedAt = maxOf(lastReadAt, start + accumulatedMs),
                startPage = startPage,
                endPage = lastReadPage,
                pagesRead = visitedPages.size,
                durationMs = accumulatedMs,
            )
        } else null
        reset(page)
        return result
    }

    /** Discard in-memory progress after a restore or an explicit statistics reset. */
    fun reset(nextPage: Int) {
        val resume = isReading
        isReading = false
        startedAt = null
        accumulatedMs = 0L
        pageDwellMs = 0L
        pageCredited = false
        visitedPages.clear()
        page = nextPage
        if (resume) setReading(true)
    }
}
