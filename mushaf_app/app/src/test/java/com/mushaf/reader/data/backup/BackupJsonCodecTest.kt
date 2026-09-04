package com.mushaf.reader.data.backup

import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.stats.KhatmaEntity
import com.mushaf.reader.data.stats.SessionEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The backup file is the only copy of a reader's khatma, statistics and bookmarks that outlives
 * the app on their phone, so every rejection here throws away something irreplaceable. These tests
 * pin both halves of that bargain: a sound backup survives a round trip intact, and a damaged one
 * is refused — but only for damage that actually matters.
 */
class BackupJsonCodecTest {

    private val pageCount = 604

    // ── Round trip ──────────────────────────────────────────────────────────────

    @Test
    fun `round trip preserves every field`() {
        val snapshot = snapshot()
        val decoded = BackupJsonCodec.decode(BackupJsonCodec.encode(snapshot), pageCount)

        assertEquals(snapshot.backupId, decoded.backupId)
        assertEquals(snapshot.createdAt, decoded.createdAt)
        assertEquals(snapshot.appVersion, decoded.appVersion)
        assertEquals(snapshot.deviceName, decoded.deviceName)
        assertEquals(snapshot.pageCount, decoded.pageCount)
        assertEquals(snapshot.reading.settings, decoded.reading.settings)
        assertEquals(snapshot.reading.bookmarks, decoded.reading.bookmarks)
        assertEquals(snapshot.reading.bookmarks2, decoded.reading.bookmarks2)
        assertEquals(snapshot.sessions, decoded.sessions)
        assertEquals(snapshot.khatmas, decoded.khatmas)
    }

    @Test
    fun `round trip survives an empty install`() {
        val empty = snapshot(
            settings = settings(lastPage = 1, visited = emptySet(), read = emptySet()),
            bookmarks = emptySet(),
            bookmarks2 = emptySet(),
            sessions = emptyList(),
            khatmas = emptyList(),
        )
        val decoded = BackupJsonCodec.decode(BackupJsonCodec.encode(empty), pageCount)

        assertTrue(decoded.reading.settings.visitedPages.isEmpty())
        assertTrue(decoded.reading.settings.readPages.isEmpty())
        assertTrue(decoded.sessions.isEmpty())
        assertTrue(decoded.khatmas.isEmpty())
    }

    @Test
    fun `sessions and khatmas keep their order`() {
        val sessions = List(5) { index ->
            SessionEntity(
                id = index + 1L,
                startedAt = 1_000L * (index + 1),
                endedAt = 2_000L * (index + 1),
                startPage = index + 1,
                endPage = index + 2,
                pagesRead = index,
            )
        }
        val decoded = BackupJsonCodec.decode(
            BackupJsonCodec.encode(snapshot(sessions = sessions)), pageCount
        )
        assertEquals(sessions.map { it.id }, decoded.sessions.map { it.id })
    }

    @Test
    fun `a backup at the full mushaf is accepted`() {
        val all = (1..pageCount).toSet()
        val decoded = BackupJsonCodec.decode(
            BackupJsonCodec.encode(
                snapshot(settings = settings(lastPage = pageCount, visited = all, read = all))
            ),
            pageCount,
        )
        assertEquals(pageCount, decoded.reading.settings.readPages.size)
    }

    // ── The khatma-progress repair ──────────────────────────────────────────────

    @Test
    fun `a read page missing from visited is repaired, not rejected`() {
        // Exactly the file a reader ends up with after clearing their statistics and reading on:
        // the dwell timer credited page 300 as read while the visited set had just been emptied.
        val data = mutate { settings ->
            settings.put("visitedPages", JSONArray(listOf(1, 2)))
            settings.put("readPages", JSONArray(listOf(1, 300)))
        }

        val decoded = BackupJsonCodec.decode(data, pageCount)

        assertEquals(setOf(1, 300), decoded.reading.settings.readPages)
        assertEquals(setOf(1, 2, 300), decoded.reading.settings.visitedPages)
    }

    @Test
    fun `every read page is visited after decoding`() {
        val data = mutate { settings ->
            settings.put("visitedPages", JSONArray(listOf<Int>()))
            settings.put("readPages", JSONArray((1..50).toList()))
        }

        val decoded = BackupJsonCodec.decode(data, pageCount)

        assertTrue(
            decoded.reading.settings.visitedPages.containsAll(decoded.reading.settings.readPages)
        )
        assertEquals(50, decoded.reading.settings.visitedPages.size)
    }

    @Test
    fun `repairing does not invent pages that were never read`() {
        val data = mutate { settings ->
            settings.put("visitedPages", JSONArray(listOf(1, 2, 3)))
            settings.put("readPages", JSONArray(listOf(2)))
        }

        val decoded = BackupJsonCodec.decode(data, pageCount)

        assertEquals(setOf(1, 2, 3), decoded.reading.settings.visitedPages)
        assertEquals(setOf(2), decoded.reading.settings.readPages)
    }

    @Test
    fun `duplicate page entries collapse into a set`() {
        val data = mutate { settings ->
            settings.put("visitedPages", JSONArray(listOf(7, 7, 7, 8)))
            settings.put("readPages", JSONArray(listOf(7, 7)))
        }

        val decoded = BackupJsonCodec.decode(data, pageCount)

        assertEquals(setOf(7, 8), decoded.reading.settings.visitedPages)
        assertEquals(setOf(7), decoded.reading.settings.readPages)
    }

    // ── Rejections that protect the reader ──────────────────────────────────────

    @Test
    fun `a backup from a different mushaf is rejected`() {
        val error = assertThrows(BackupException::class.java) {
            BackupJsonCodec.decode(BackupJsonCodec.encode(snapshot()), 500)
        }
        assertTrue(error.message!!.contains("عدد صفحات"))
    }

    @Test
    fun `an unknown schema version is rejected`() {
        val data = mutateRoot { it.put("schemaVersion", BACKUP_SCHEMA_VERSION + 1) }
        assertThrows(BackupException::class.java) { BackupJsonCodec.decode(data, pageCount) }
    }

    @Test
    fun `a page number outside the mushaf is rejected`() {
        val data = mutate { it.put("visitedPages", JSONArray(listOf(1, pageCount + 1))) }
        val error = assertThrows(BackupException::class.java) {
            BackupJsonCodec.decode(data, pageCount)
        }
        assertTrue(error.message!!.contains("رقم صفحة"))
    }

    @Test
    fun `page zero is rejected`() {
        val data = mutate { it.put("readPages", JSONArray(listOf(0))) }
        assertThrows(BackupException::class.java) { BackupJsonCodec.decode(data, pageCount) }
    }

    @Test
    fun `a reading position outside the mushaf is rejected`() {
        val data = mutate { it.put("lastPage", pageCount + 1) }
        val error = assertThrows(BackupException::class.java) {
            BackupJsonCodec.decode(data, pageCount)
        }
        assertTrue(error.message!!.contains("موضع القراءة"))
    }

    @Test
    fun `a malformed bookmark is rejected`() {
        val data = mutateRoot {
            it.getJSONObject("reading").put("bookmarks", JSONArray(listOf("not-a-verse")))
        }
        val error = assertThrows(BackupException::class.java) {
            BackupJsonCodec.decode(data, pageCount)
        }
        assertTrue(error.message!!.contains("علامة مرجعية"))
    }

    @Test
    fun `a session pointing outside the mushaf is rejected`() {
        val data = mutateRoot {
            it.getJSONArray("sessions").getJSONObject(0).put("endPage", pageCount + 5)
        }
        val error = assertThrows(BackupException::class.java) {
            BackupJsonCodec.decode(data, pageCount)
        }
        assertTrue(error.message!!.contains("جلسة قراءة"))
    }

    @Test
    fun `a khatma claiming more pages than the mushaf has is rejected`() {
        val data = mutateRoot {
            it.getJSONArray("khatmas").getJSONObject(0).put("pagesRead", pageCount + 1)
        }
        val error = assertThrows(BackupException::class.java) {
            BackupJsonCodec.decode(data, pageCount)
        }
        assertTrue(error.message!!.contains("سجل ختمة"))
    }

    @Test
    fun `a blank backup id is rejected`() {
        val data = mutateRoot { it.put("backupId", "   ") }
        assertThrows(BackupException::class.java) { BackupJsonCodec.decode(data, pageCount) }
    }

    @Test
    fun `a missing creation date is rejected`() {
        val data = mutateRoot { it.put("createdAt", 0L) }
        val error = assertThrows(BackupException::class.java) {
            BackupJsonCodec.decode(data, pageCount)
        }
        assertTrue(error.message!!.contains("تاريخ"))
    }

    @Test
    fun `a negative khatma start is rejected`() {
        val data = mutate { it.put("khatmaStartedAt", -1L) }
        assertThrows(BackupException::class.java) { BackupJsonCodec.decode(data, pageCount) }
    }

    @Test
    fun `a file that is not a backup is rejected`() {
        val error = assertThrows(BackupException::class.java) {
            BackupJsonCodec.decode("just some text".toByteArray(), pageCount)
        }
        assertTrue(error.message!!.contains("تعذر قراءة"))
    }

    @Test
    fun `a truncated backup is rejected`() {
        val whole = BackupJsonCodec.encode(snapshot())
        val half = whole.copyOfRange(0, whole.size / 2)
        assertThrows(BackupException::class.java) { BackupJsonCodec.decode(half, pageCount) }
    }

    @Test
    fun `an empty file is rejected`() {
        assertThrows(BackupException::class.java) { BackupJsonCodec.decode(ByteArray(0), pageCount) }
    }

    // ── Older backups keep working ──────────────────────────────────────────────

    @Test
    fun `a backup written before a setting existed falls back to its default`() {
        val data = mutate { settings ->
            settings.remove("keepScreenOn")
            settings.remove("barButtons")
            settings.remove("buttonColors")
            settings.remove("pageSideIndicatorLength")
            settings.remove("verticalPaging")
            settings.remove("edgeMargin")
        }

        val decoded = BackupJsonCodec.decode(data, pageCount).reading.settings

        assertEquals(ReadingStore.DEFAULT_KEEP_SCREEN_ON, decoded.keepScreenOn)
        assertEquals(ReadingStore.DEFAULT_BAR_BUTTONS, decoded.barButtons)
        assertEquals(40, decoded.pageSideIndicatorLength)
        assertFalse(decoded.verticalPaging)
        // A backup from before curved-edge margins existed must not start moving the reader's
        // layout on restore; "none" is the default precisely so nothing shifts.
        assertEquals(ReadingStore.EDGE_MARGIN_NONE, decoded.edgeMargin)
    }

    @Test
    fun `the curved-edge margin survives a round trip`() {
        val decoded = BackupJsonCodec.decode(
            BackupJsonCodec.encode(
                snapshot(settings = settings().copy(edgeMargin = ReadingStore.EDGE_MARGIN_AUTO))
            ),
            pageCount,
        )
        assertEquals(ReadingStore.EDGE_MARGIN_AUTO, decoded.reading.settings.edgeMargin)
    }

    @Test
    fun `a backup from before the palettes maps its dark flag onto the dark palette`() {
        val data = mutate { settings ->
            settings.remove("themeId")
            settings.put("darkTheme", true)
        }
        assertEquals(
            ReadingStore.DARK_THEME_ID,
            BackupJsonCodec.decode(data, pageCount).reading.settings.themeId,
        )
    }

    @Test
    fun `out of range appearance numbers are clamped rather than rejected`() {
        val data = mutate { settings ->
            settings.put("bottomJuzBarOpacity", 500)
            settings.put("topSurahBarThickness", 99)
            settings.put("showHeaderButtonOpacity", 1)
            settings.put("buttonPosFraction", 5.0)
        }

        val decoded = BackupJsonCodec.decode(data, pageCount).reading.settings

        assertEquals(100, decoded.bottomJuzBarOpacity)
        assertEquals(24, decoded.topSurahBarThickness)
        assertEquals(25, decoded.showHeaderButtonOpacity)
        assertEquals(1f, decoded.buttonPosFraction, 0.0001f)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Re-encode a valid backup with [edit] applied to its `reading.settings` object. */
    private fun mutate(edit: (JSONObject) -> Unit): ByteArray =
        mutateRoot { edit(it.getJSONObject("reading").getJSONObject("settings")) }

    /** Re-encode a valid backup with [edit] applied to its root object. */
    private fun mutateRoot(edit: (JSONObject) -> Unit): ByteArray {
        val root = JSONObject(gunzip(BackupJsonCodec.encode(snapshot())).toString(Charsets.UTF_8))
        edit(root)
        return gzip(root.toString().toByteArray(Charsets.UTF_8))
    }

    private fun gzip(raw: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(raw) }
        return out.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }

    private fun snapshot(
        settings: ReadingStore.Settings = settings(),
        bookmarks: Set<String> = setOf("2:255"),
        bookmarks2: Set<String> = setOf("36:1"),
        sessions: List<SessionEntity> = listOf(
            SessionEntity(
                id = 1,
                startedAt = 1_700_000_000_000,
                endedAt = 1_700_000_600_000,
                startPage = 10,
                endPage = 14,
                pagesRead = 5,
            )
        ),
        khatmas: List<KhatmaEntity> = listOf(
            KhatmaEntity(
                id = 1,
                completedAt = 1_700_000_000_000,
                startedAt = 1_690_000_000_000,
                durationDays = 115,
                pagesRead = 604,
            )
        ),
    ) = BackupSnapshot(
        backupId = "6f1d0d3e-0f0a-4a1e-9d3f-6a1b2c3d4e5f",
        createdAt = 1_700_000_000_000,
        appVersion = "0.6.4",
        deviceName = "جهاز اختبار",
        pageCount = pageCount,
        reading = ReadingStore.BackupState(settings, bookmarks, bookmarks2),
        sessions = sessions,
        khatmas = khatmas,
    )

    private fun settings(
        lastPage: Int = 42,
        visited: Set<Int> = setOf(1, 2, 3, 42),
        read: Set<Int> = setOf(1, 2),
    ) = ReadingStore.Settings(
        lastPage = lastPage,
        themeId = ReadingStore.DEFAULT_THEME_ID,
        fillScreen = true,
        visitedPages = visited,
        readPages = read,
        hiddenButtons = setOf("search"),
        barButtons = setOf("stats"),
        buttonColors = mapOf("clock" to "red"),
        bigButtons = true,
        showClock = true,
        showSessionTimer = true,
        showSurahNumber = true,
        showSurahAyahCount = true,
        showSurahProgress = true,
        showJuzProgressPercent = true,
        showJuzProgressPages = true,
        clockColor = "red",
        sessionTimerColor = "green",
        showButtonPage = false,
        buttonPageColor = "blue",
        showHeaderButtonOpacity = 80,
        buttonPosFraction = 0.5f,
        showBottomJuzBar = true,
        bottomJuzBarColor = "blue",
        bottomJuzBarThickness = 6,
        bottomJuzBarOpacity = 85,
        showTopSurahBar = true,
        topSurahBarColor = "green",
        topSurahBarThickness = 2,
        topSurahBarOpacity = 70,
        showPageSideIndicator = false,
        pageSideIndicatorColor = "red",
        pageSideIndicatorThickness = 9,
        pageSideIndicatorLength = 64,
        pageSideIndicatorOpacity = 50,
        khatmaStartedAt = 1_690_000_000_000,
        verticalPaging = true,
        keepScreenOn = false,
        edgeMargin = ReadingStore.EDGE_MARGIN_MEDIUM,
    )
}
