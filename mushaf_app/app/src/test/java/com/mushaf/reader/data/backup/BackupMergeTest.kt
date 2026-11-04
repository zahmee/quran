package com.mushaf.reader.data.backup

import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.stats.KhatmaEntity
import com.mushaf.reader.data.stats.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Restoring is how a reader moves between a phone and a tablet, so it must never cost either
 * device its own reading. These tests pin what is united, what moves over, and which khatma cycle
 * survives when the two devices disagree.
 */
class BackupMergeTest {

    // ── History is united ───────────────────────────────────────────────────────

    @Test
    fun `sessions from both devices are kept`() {
        val merged = BackupMerge.merge(
            local = snapshot(sessions = listOf(session(id = 1, start = 1_000))),
            incoming = snapshot(sessions = listOf(session(id = 1, start = 5_000))),
            takeDisplaySettings = false,
        )
        assertEquals(listOf(5_000L, 1_000L), merged.sessions.map { it.startedAt })
    }

    @Test
    fun `restoring the same backup twice adds nothing`() {
        val sessions = listOf(session(id = 1, start = 1_000), session(id = 2, start = 9_000))
        val merged = BackupMerge.merge(
            local = snapshot(sessions = sessions),
            incoming = snapshot(sessions = sessions),
            takeDisplaySettings = false,
        )
        assertEquals(sessions.sortedByDescending { it.startedAt }, merged.sessions)
    }

    @Test
    fun `local rows keep their ids and arriving rows get fresh ones`() {
        // Both devices number their first session 1; inserting the backup's id as-is would collide.
        val merged = BackupMerge.merge(
            local = snapshot(sessions = listOf(session(id = 1, start = 1_000))),
            incoming = snapshot(sessions = listOf(session(id = 1, start = 5_000))),
            takeDisplaySettings = false,
        )
        assertEquals(mapOf(1_000L to 1L, 5_000L to 0L), merged.sessions.associate { it.startedAt to it.id })
    }

    @Test
    fun `completed khatmas from both devices are kept once`() {
        val shared = khatma(id = 1, completedAt = 50_000, startedAt = 10_000)
        val merged = BackupMerge.merge(
            local = snapshot(khatmas = listOf(shared)),
            incoming = snapshot(khatmas = listOf(shared, khatma(id = 2, completedAt = 90_000, startedAt = 50_000))),
            takeDisplaySettings = false,
        )
        assertEquals(listOf(90_000L, 50_000L), merged.khatmas.map { it.completedAt })
    }

    // ── Position and markers move over ──────────────────────────────────────────

    @Test
    fun `reading position and markers come from the backup`() {
        val merged = BackupMerge.merge(
            local = snapshot(lastPage = 10, bookmarks = setOf("1:1"), bookmarks2 = setOf("2:1")),
            incoming = snapshot(lastPage = 300, bookmarks = setOf("18:10"), bookmarks2 = setOf("36:1")),
            takeDisplaySettings = false,
        )
        assertEquals(300, merged.reading.settings.lastPage)
        assertEquals(setOf("18:10"), merged.reading.bookmarks)
        assertEquals(setOf("36:1"), merged.reading.bookmarks2)
    }

    @Test
    fun `a marker the backup never set leaves this device's in place`() {
        val merged = BackupMerge.merge(
            local = snapshot(bookmarks = setOf("1:1"), bookmarks2 = setOf("2:1")),
            incoming = snapshot(bookmarks = emptySet(), bookmarks2 = setOf("36:1")),
            takeDisplaySettings = false,
        )
        assertEquals(setOf("1:1"), merged.reading.bookmarks)
        assertEquals(setOf("36:1"), merged.reading.bookmarks2)
    }

    // ── Display settings only on request ────────────────────────────────────────

    @Test
    fun `display settings stay local unless asked for`() {
        val local = snapshot(themeId = "light")
        val incoming = snapshot(themeId = "dark")

        assertEquals("light", BackupMerge.merge(local, incoming, takeDisplaySettings = false).reading.settings.themeId)
        assertEquals("dark", BackupMerge.merge(local, incoming, takeDisplaySettings = true).reading.settings.themeId)
    }

    // ── The current khatma's page map ───────────────────────────────────────────

    @Test
    fun `the same khatma read on two devices unites its pages`() {
        val merged = BackupMerge.merge(
            local = snapshot(khatmaStartedAt = 100, visited = setOf(1, 2, 3), read = setOf(1, 2)),
            incoming = snapshot(khatmaStartedAt = 200, visited = setOf(3, 4), read = setOf(3, 4)),
            takeDisplaySettings = false,
        ).reading.settings

        assertEquals(setOf(1, 2, 3, 4), merged.readPages)
        assertEquals(setOf(1, 2, 3, 4), merged.visitedPages)
        assertEquals(100L, merged.khatmaStartedAt)
    }

    @Test
    fun `a fresh device takes the backup's khatma whole`() {
        // A tablet set up after the phone has a later cycle start, but nothing in it.
        val merged = BackupMerge.merge(
            local = snapshot(khatmaStartedAt = 9_000, visited = emptySet(), read = emptySet()),
            incoming = snapshot(khatmaStartedAt = 100, visited = setOf(1, 2), read = setOf(1)),
            takeDisplaySettings = false,
        ).reading.settings

        assertEquals(100L, merged.khatmaStartedAt)
        assertEquals(setOf(1, 2), merged.visitedPages)
        assertEquals(setOf(1), merged.readPages)
    }

    @Test
    fun `an empty backup leaves this device's khatma alone`() {
        val merged = BackupMerge.merge(
            local = snapshot(khatmaStartedAt = 100, visited = setOf(7), read = setOf(7)),
            incoming = snapshot(khatmaStartedAt = 9_000, visited = emptySet(), read = emptySet()),
            takeDisplaySettings = false,
        ).reading.settings

        assertEquals(100L, merged.khatmaStartedAt)
        assertEquals(setOf(7), merged.readPages)
    }

    @Test
    fun `a khatma completed on the other device replaces the finished cycle here`() {
        // The phone completed at 50_000 and started anew; the tablet is still in the old cycle.
        val completed = khatma(id = 1, completedAt = 50_000, startedAt = 100)
        val merged = BackupMerge.merge(
            local = snapshot(khatmaStartedAt = 100, visited = (1..600).toSet(), read = (1..600).toSet()),
            incoming = snapshot(
                khatmaStartedAt = 50_000,
                visited = setOf(1, 2),
                read = setOf(1),
                khatmas = listOf(completed),
            ),
            takeDisplaySettings = false,
        ).reading.settings

        assertEquals(50_000L, merged.khatmaStartedAt)
        assertEquals(setOf(1), merged.readPages)
    }

    @Test
    fun `a khatma completed here survives restoring the other device's finished cycle`() {
        val completed = khatma(id = 1, completedAt = 50_000, startedAt = 100)
        val merged = BackupMerge.merge(
            local = snapshot(khatmaStartedAt = 50_000, visited = setOf(1), read = setOf(1), khatmas = listOf(completed)),
            incoming = snapshot(khatmaStartedAt = 100, visited = (1..300).toSet(), read = (1..300).toSet()),
            takeDisplaySettings = false,
        ).reading.settings

        assertEquals(50_000L, merged.khatmaStartedAt)
        assertEquals(setOf(1), merged.readPages)
    }

    @Test
    fun `united pages keep read inside visited`() {
        val merged = BackupMerge.merge(
            local = snapshot(khatmaStartedAt = 100, visited = setOf(1), read = emptySet()),
            incoming = snapshot(khatmaStartedAt = 100, visited = emptySet(), read = setOf(5)),
            takeDisplaySettings = false,
        ).reading.settings

        assertEquals(setOf(1, 5), merged.visitedPages)
        assertEquals(setOf(5), merged.readPages)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun session(id: Long, start: Long) = SessionEntity(
        id = id,
        startedAt = start,
        endedAt = start + 600_000,
        startPage = 1,
        endPage = 3,
        pagesRead = 3,
    )

    private fun khatma(id: Long, completedAt: Long, startedAt: Long) = KhatmaEntity(
        id = id,
        completedAt = completedAt,
        startedAt = startedAt,
        durationDays = 30,
        pagesRead = 604,
    )

    private fun snapshot(
        lastPage: Int = 1,
        themeId: String = ReadingStore.DEFAULT_THEME_ID,
        khatmaStartedAt: Long = 100,
        visited: Set<Int> = emptySet(),
        read: Set<Int> = emptySet(),
        bookmarks: Set<String> = emptySet(),
        bookmarks2: Set<String> = emptySet(),
        sessions: List<SessionEntity> = emptyList(),
        khatmas: List<KhatmaEntity> = emptyList(),
    ) = BackupSnapshot(
        backupId = "backup",
        createdAt = 1_700_000_000_000,
        appVersion = "0.7.0",
        deviceName = "جهاز اختبار",
        pageCount = 604,
        reading = ReadingStore.BackupState(
            settings = settings(lastPage, themeId, khatmaStartedAt, visited, read),
            bookmarks = bookmarks,
            bookmarks2 = bookmarks2,
        ),
        sessions = sessions,
        khatmas = khatmas,
    )

    private fun settings(
        lastPage: Int,
        themeId: String,
        khatmaStartedAt: Long,
        visited: Set<Int>,
        read: Set<Int>,
    ) = ReadingStore.Settings(
        lastPage = lastPage,
        themeId = themeId,
        fillScreen = true,
        visitedPages = visited,
        readPages = read,
        hiddenButtons = emptySet(),
        barButtons = emptySet(),
        buttonColors = emptyMap(),
        bigButtons = true,
        showClock = false,
        showSessionTimer = true,
        showSurahNumber = false,
        showSurahAyahCount = false,
        showSurahProgress = false,
        showJuzProgressPercent = false,
        showJuzProgressPages = false,
        clockColor = "muted",
        sessionTimerColor = "muted",
        showButtonPage = true,
        buttonPageColor = "red",
        showHeaderButtonOpacity = 100,
        buttonPosFraction = -1f,
        showBottomJuzBar = true,
        bottomJuzBarColor = "red",
        bottomJuzBarThickness = 4,
        bottomJuzBarOpacity = 100,
        showTopSurahBar = true,
        topSurahBarColor = "green",
        topSurahBarThickness = 4,
        topSurahBarOpacity = 100,
        showPageSideIndicator = true,
        pageSideIndicatorColor = "green",
        pageSideIndicatorThickness = 4,
        pageSideIndicatorLength = 40,
        pageSideIndicatorOpacity = 70,
        khatmaStartedAt = khatmaStartedAt,
        verticalPaging = false,
        keepScreenOn = true,
        edgeMargin = ReadingStore.EDGE_MARGIN_AUTO,
    )
}
