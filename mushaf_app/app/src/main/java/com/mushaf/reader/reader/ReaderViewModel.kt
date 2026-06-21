package com.mushaf.reader.reader

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.AyahRepository
import com.mushaf.reader.data.PageRepository
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.stats.FullStats
import com.mushaf.reader.data.stats.ReadingStats
import com.mushaf.reader.data.stats.SessionEntity
import com.mushaf.reader.data.stats.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** One surah in the navigation index: number, Arabic name, and the page it begins on. */
data class SurahEntry(val number: Int, val nameAr: String, val firstPage: Int, val ayahCount: Int)

/** One juz in the navigation index: number and the page it begins on. */
data class JuzEntry(val number: Int, val firstPage: Int, val ayahCount: Int)

/** Juz position for a page: juz number, the page's index within that juz, and the juz's page count. */
data class JuzPageInfo(val juz: Int, val pageInJuz: Int, val pagesInJuz: Int)

/** A single search hit. */
data class SearchResult(
    val verseKey: String,
    val surahNameAr: String,
    val ayahNumber: Int,
    val page: Int,
    val text: String,
)

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val pageRepo = PageRepository(app)
    private val ayahRepo = AyahRepository(app)
    private val store = ReadingStore(app)
    private val statsRepo = StatsRepository(app)

    val pageCount: Int = pageRepo.pageCount()

    private var ayahData by mutableStateOf(AyahRepository.Data(emptyMap(), emptyMap(), 1106, 1789))
    val imageWidth: Int get() = ayahData.imageWidth
    val imageHeight: Int get() = ayahData.imageHeight

    // Persisted display/position settings, read once synchronously so the first frame already
    // reflects the user's last theme/fit choice (no flash of the default light/fitted view).
    private val initialSettings = runBlocking(Dispatchers.IO) { store.settings() }

    val initialPage: Int = initialSettings.lastPage.coerceIn(1, pageCount.coerceAtLeast(1))

    var darkTheme by mutableStateOf(initialSettings.darkTheme)
        private set

    /** When on, the page fills the screen (width-fill + scroll on wide screens, height-stretch
     *  on tall screens); when off, the whole page is fitted and centered. */
    var fillScreen by mutableStateOf(initialSettings.fillScreen)
        private set

    /** Ids of the header buttons the user has hidden; any id NOT in this set is shown. */
    var hiddenButtons by mutableStateOf(initialSettings.hiddenButtons)
        private set

    /** When on, the header buttons are rendered a little larger. */
    var bigButtons by mutableStateOf(initialSettings.bigButtons)
        private set

    /** When on, a live wall-clock (red) shows in the header. */
    var showClock by mutableStateOf(initialSettings.showClock)
        private set

    /** When on, the current reading-session duration (green) shows in the header. */
    var showSessionTimer by mutableStateOf(initialSettings.showSessionTimer)
        private set

    var showSurahNumber by mutableStateOf(initialSettings.showSurahNumber)
        private set

    var showSurahAyahCount by mutableStateOf(initialSettings.showSurahAyahCount)
        private set

    var showSurahProgress by mutableStateOf(initialSettings.showSurahProgress)
        private set

    var showJuzProgressPercent by mutableStateOf(initialSettings.showJuzProgressPercent)
        private set

    var showJuzProgressPages by mutableStateOf(initialSettings.showJuzProgressPages)
        private set

    var clockColor by mutableStateOf(initialSettings.clockColor)
        private set

    var sessionTimerColor by mutableStateOf(initialSettings.sessionTimerColor)
        private set

    /** Start time (ms) of the current foreground reading session; 0 when none is running.
     *  Observable so the header timer updates when a new session begins. */
    var sessionStartedAt by mutableStateOf(0L)
        private set

    var selectedAyah by mutableStateOf<AyahMarker?>(null)
        private set

    var bookmarks by mutableStateOf<Set<String>>(emptySet())
        private set

    var stats by mutableStateOf<ReadingStats?>(null)
        private set

    var sessions by mutableStateOf<List<SessionEntity>>(emptyList())
        private set

    var fullStats by mutableStateOf<FullStats?>(null)
        private set

    /** Pages ever opened (khatma map). */
    var visitedPagesAll by mutableStateOf(initialSettings.visitedPages)
        private set

    /** Pages dwelt on long enough to count as actually read (read ⊆ visited). */
    var readPagesAll by mutableStateOf(initialSettings.readPages)
        private set

    // Session tracking (one row per foreground period, committed on stop).
    private var sessionStart = 0L
    private var sessionStartPage = initialPage
    private val visitedPages = linkedSetOf(initialPage)
    private var lastPage = initialPage

    // Per-page dwell tracking: a page becomes "read" once it stays visible this long (20s),
    // so genuine reading counts but quick flips stay merely "visited".
    private val readDwellMs = 20_000L
    private var pageEnteredAt = 0L
    private var visiblePage = initialPage

    init {
        viewModelScope.launch { ayahData = withContext(Dispatchers.IO) { ayahRepo.loadAll() } }
        viewModelScope.launch { bookmarks = withContext(Dispatchers.IO) { store.bookmarks() } }
    }

    fun toggleTheme() {
        darkTheme = !darkTheme
        viewModelScope.launch { store.setDarkTheme(darkTheme) }
    }

    fun toggleFillScreen() {
        fillScreen = !fillScreen
        viewModelScope.launch { store.setFillScreen(fillScreen) }
    }

    /** Whether the header button with [id] is currently shown. */
    fun isButtonVisible(id: String): Boolean = !hiddenButtons.contains(id)

    /** Show or hide the header button with [id] and persist the choice. */
    fun setButtonVisible(id: String, visible: Boolean) {
        val next = if (visible) hiddenButtons - id else hiddenButtons + id
        if (next == hiddenButtons) return
        hiddenButtons = next
        viewModelScope.launch { store.setHiddenButtons(next) }
    }

    /** Enable or disable the larger header buttons and persist the choice. */
    fun updateBigButtons(value: Boolean) {
        if (value == bigButtons) return
        bigButtons = value
        viewModelScope.launch { store.setBigButtons(value) }
    }

    fun updateShowClock(value: Boolean) {
        if (value == showClock) return
        showClock = value
        viewModelScope.launch { store.setShowClock(value) }
    }

    fun updateShowSessionTimer(value: Boolean) {
        if (value == showSessionTimer) return
        showSessionTimer = value
        viewModelScope.launch { store.setShowSessionTimer(value) }
    }

    fun updateShowSurahNumber(value: Boolean) {
        if (value == showSurahNumber) return
        showSurahNumber = value
        viewModelScope.launch { store.setShowSurahNumber(value) }
    }

    fun updateShowSurahAyahCount(value: Boolean) {
        if (value == showSurahAyahCount) return
        showSurahAyahCount = value
        viewModelScope.launch { store.setShowSurahAyahCount(value) }
    }

    fun updateShowSurahProgress(value: Boolean) {
        if (value == showSurahProgress) return
        showSurahProgress = value
        viewModelScope.launch { store.setShowSurahProgress(value) }
    }

    fun updateShowJuzProgressPercent(value: Boolean) {
        if (value == showJuzProgressPercent) return
        showJuzProgressPercent = value
        viewModelScope.launch { store.setShowJuzProgressPercent(value) }
    }

    fun updateShowJuzProgressPages(value: Boolean) {
        if (value == showJuzProgressPages) return
        showJuzProgressPages = value
        viewModelScope.launch { store.setShowJuzProgressPages(value) }
    }

    fun updateClockColor(value: String) {
        if (value == clockColor) return
        clockColor = value
        viewModelScope.launch { store.setClockColor(value) }
    }

    fun updateSessionTimerColor(value: String) {
        if (value == sessionTimerColor) return
        sessionTimerColor = value
        viewModelScope.launch { store.setSessionTimerColor(value) }
    }

    fun assetModel(pageNumber: Int): String = pageRepo.assetUri(pageNumber, darkTheme)

    fun markersForPage(pageNumber: Int): List<AyahMarker> = ayahData.byPage[pageNumber].orEmpty()

    fun selectAyah(ayah: AyahMarker?) { selectedAyah = ayah }

    fun clearSelection() { selectedAyah = null }

    fun isBookmarked(verseKey: String): Boolean = bookmarks.contains(verseKey)

    /** Single bookmark: a new one replaces the old; re-tapping the current one clears it. */
    fun toggleBookmark(ayah: AyahMarker) {
        val next = if (bookmarks.contains(ayah.verseKey)) emptySet() else setOf(ayah.verseKey)
        bookmarks = next
        viewModelScope.launch { store.setBookmarks(next) }
    }

    private fun bookmarkPage(): Int? = bookmarks.firstOrNull()?.let { ayahData.keyToPage[it] }

    fun bookmarkJumpPage(): Int? = bookmarkPage()

    fun saveLastPage(page: Int) {
        lastPage = page
        visitedPages.add(page)
        viewModelScope.launch { store.setLastPage(page) }
    }

    /** Called when [page] becomes the visible page: records the dwell time on the page just left
     *  (→ "read" if long enough), marks the new page visited, and persists the reading position. */
    fun onPageVisible(page: Int) {
        val now = System.currentTimeMillis()
        finalizeDwell(now)
        visiblePage = page
        pageEnteredAt = now
        markVisited(page)
        saveLastPage(page)
    }

    /** Promote the currently-visible page to "read" if it has been on screen long enough. */
    private fun finalizeDwell(now: Long) {
        if (pageEnteredAt <= 0L) return
        if (now - pageEnteredAt >= readDwellMs) markRead(visiblePage)
    }

    private fun markVisited(page: Int) {
        if (visitedPagesAll.contains(page)) return
        val next = visitedPagesAll + page
        visitedPagesAll = next
        viewModelScope.launch { store.setVisitedPages(next) }
    }

    private fun markRead(page: Int) {
        if (readPagesAll.contains(page)) return
        val next = readPagesAll + page
        readPagesAll = next
        viewModelScope.launch { store.setReadPages(next) }
    }

    fun beginSession() {
        sessionStart = System.currentTimeMillis()
        sessionStartedAt = sessionStart
        sessionStartPage = lastPage
        visitedPages.clear()
        visitedPages.add(lastPage)
        // Resume dwell timing for the page on screen.
        visiblePage = lastPage
        pageEnteredAt = sessionStart
    }

    fun commitSession() {
        // Settle the current page's dwell before the app leaves the foreground.
        finalizeDwell(System.currentTimeMillis())
        pageEnteredAt = 0L
        if (sessionStart <= 0L) return
        val start = sessionStart
        val startPage = sessionStartPage
        val end = System.currentTimeMillis()
        val endPage = lastPage
        val pages = visitedPages.size
        sessionStart = 0L
        viewModelScope.launch { statsRepo.commitSession(start, end, startPage, endPage, pages) }
    }

    /** Delete the given reading sessions, then refresh the visible stats. */
    fun deleteSessions(toDelete: List<SessionEntity>) {
        if (toDelete.isEmpty()) return
        viewModelScope.launch {
            statsRepo.deleteSessions(toDelete.map { it.id })
            sessions = statsRepo.allSessions()
            fullStats = statsRepo.fullStats(
                currentPage = lastPage,
                totalPages = pageCount,
                bookmarkPage = bookmarkPage(),
            )
        }
    }

    /** Wipe ALL statistics: every session plus the khatma-map progress (visited/read pages). */
    fun clearAllStats() {
        viewModelScope.launch {
            statsRepo.clearAllSessions()
            visitedPagesAll = emptySet()
            readPagesAll = emptySet()
            store.setVisitedPages(emptySet())
            store.setReadPages(emptySet())
            sessions = statsRepo.allSessions()
            fullStats = statsRepo.fullStats(
                currentPage = lastPage,
                totalPages = pageCount,
                bookmarkPage = bookmarkPage(),
            )
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            stats = statsRepo.summary(
                currentPage = lastPage,
                totalPages = pageCount,
                bookmarkPage = bookmarkPage(),
            )
        }
    }

    fun refreshSessions() {
        viewModelScope.launch { sessions = statsRepo.allSessions() }
    }

    fun openStats() {
        // Credit dwell on the page being read before the stats/khatma map are shown.
        finalizeDwell(System.currentTimeMillis())
        viewModelScope.launch {
            sessions = statsRepo.allSessions()
            fullStats = statsRepo.fullStats(
                currentPage = lastPage,
                totalPages = pageCount,
                bookmarkPage = bookmarkPage(),
            )
        }
    }

    /** True once the ayah data has finished loading (so the index/search are populated). */
    val ayahLoaded: Boolean get() = ayahData.byPage.isNotEmpty()

    /** Surahs in order, each mapped to the lowest page it appears on (its starting page). */
    fun surahIndex(): List<SurahEntry> {
        val byNumber = HashMap<Int, SurahEntry>()
        for (markers in ayahData.byPage.values) {
            for (m in markers) {
                val cur = byNumber[m.surahNumber]
                if (cur == null || m.page < cur.firstPage) {
                    byNumber[m.surahNumber] =
                        SurahEntry(m.surahNumber, m.surahNameAr, m.page, surahAyahCount(m.surahNumber))
                }
            }
        }
        return byNumber.values.sortedBy { it.number }
    }

    /** The 30 juz in order, each mapped to the lowest page it appears on. */
    fun juzIndex(): List<JuzEntry> {
        val firstPage = HashMap<Int, Int>()
        val ayahs = HashMap<Int, MutableSet<String>>()
        for (markers in ayahData.byPage.values) {
            for (m in markers) {
                if (m.juz <= 0) continue
                val cur = firstPage[m.juz]
                if (cur == null || m.page < cur) firstPage[m.juz] = m.page
                ayahs.getOrPut(m.juz) { HashSet() }.add(m.verseKey)
            }
        }
        return firstPage.entries.sortedBy { it.key }
            .map { JuzEntry(it.key, it.value, ayahs[it.key]?.size ?: 0) }
    }

    /** Total number of ayahs in each surah (index 0 = surah 1), standard Hafs/Madinah numbering. */
    private val surahAyahCounts = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109,
        123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
        34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45,
        60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44,
        28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
        15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
        5, 4, 5, 6
    )

    /** Number of ayahs in [surahNumber] (1..114); 0 if out of range. */
    fun surahAyahCount(surahNumber: Int): Int =
        surahAyahCounts.getOrElse(surahNumber - 1) { 0 }

    // Page span (first..last page where it appears) per surah, built once from the loaded data.
    private var surahPageRangesCache: Map<Int, IntRange> = emptyMap()

    private fun surahPageRanges(): Map<Int, IntRange> {
        if (surahPageRangesCache.isNotEmpty()) return surahPageRangesCache
        val minPage = HashMap<Int, Int>()
        val maxPage = HashMap<Int, Int>()
        for (markers in ayahData.byPage.values) {
            for (m in markers) {
                minPage[m.surahNumber] = minOf(minPage[m.surahNumber] ?: m.page, m.page)
                maxPage[m.surahNumber] = maxOf(maxPage[m.surahNumber] ?: m.page, m.page)
            }
        }
        val map = minPage.keys.associateWith { minPage.getValue(it)..maxPage.getValue(it) }
        if (map.isNotEmpty()) surahPageRangesCache = map
        return map
    }

    /** Progress (0..100) through [surahNumber] by page position of [page]. */
    fun surahProgressPercent(page: Int, surahNumber: Int): Int {
        val range = surahPageRanges()[surahNumber] ?: return 0
        val span = (range.last - range.first + 1).coerceAtLeast(1)
        val pos = (page - range.first + 1).coerceIn(1, span)
        return pos * 100 / span
    }

    /** Juz number, current page within the juz, and total pages in the juz — derived purely from
     *  the page number using the Madinah mushaf layout: juz 1 = 21 pages (1..21), juz 2..29 =
     *  20 pages each, juz 30 = 23 pages (582..604). */
    fun juzInfoForPage(page: Int): JuzPageInfo {
        val p = page.coerceIn(1, pageCount)
        return when {
            p <= 21 -> JuzPageInfo(1, p, 21)
            p >= 582 -> JuzPageInfo(30, p - 581, 23)
            else -> {
                val juz = 2 + (p - 22) / 20
                val start = 22 + (juz - 2) * 20
                JuzPageInfo(juz, p - start + 1, 20)
            }
        }
    }

    // Cached (marker, normalized text) pairs, built once from the loaded ayah data.
    private var searchEntries: List<Pair<AyahMarker, String>> = emptyList()

    private fun ensureSearchIndex(): List<Pair<AyahMarker, String>> {
        if (searchEntries.isNotEmpty()) return searchEntries
        val all = ayahData.byPage.values.flatten()
        if (all.isEmpty()) return emptyList()
        searchEntries = all.map { it to normalizeArabic(it.textUthmani) }
        return searchEntries
    }

    /** Search ayah text and surah names (diacritic-insensitive). Also matches a "2:255" verse key. */
    fun search(query: String, limit: Int = 60): List<SearchResult> {
        val raw = query.trim()
        if (raw.isEmpty()) return emptyList()

        // Direct "surah:ayah" jump, e.g. 2:255.
        Regex("""^\s*(\d{1,3})\s*[:：]\s*(\d{1,3})\s*$""").find(raw)?.let { mt ->
            val key = "${mt.groupValues[1]}:${mt.groupValues[2]}"
            ensureSearchIndex().firstOrNull { it.first.verseKey == key }?.let { (m, _) ->
                return listOf(SearchResult(m.verseKey, m.surahNameAr, m.ayahNumber, m.page, m.textUthmani))
            }
        }

        val q = normalizeArabic(raw)
        if (q.isEmpty()) return emptyList()
        val results = ArrayList<SearchResult>()
        for ((m, norm) in ensureSearchIndex()) {
            if (norm.contains(q) || normalizeArabic(m.surahNameAr).contains(q)) {
                results.add(SearchResult(m.verseKey, m.surahNameAr, m.ayahNumber, m.page, m.textUthmani))
                if (results.size >= limit) break
            }
        }
        return results
    }
}

/** Strip Arabic diacritics/tatweel and fold letter variants so search ignores tashkeel. */
private fun normalizeArabic(s: String): String {
    val sb = StringBuilder(s.length)
    for (ch in s) {
        when (ch) {
            // harakat, tanwin, shadda, sukun, superscript alef, tatweel
            'ً', 'ٌ', 'ٍ', 'َ', 'ُ', 'ِ', 'ّ',
            'ْ', 'ٓ', 'ٔ', 'ٕ', 'ٰ', 'ـ' -> {}
            'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
            'ى' -> sb.append('ي')
            'ئ' -> sb.append('ي')
            'ؤ' -> sb.append('و')
            'ة' -> sb.append('ه')
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}
