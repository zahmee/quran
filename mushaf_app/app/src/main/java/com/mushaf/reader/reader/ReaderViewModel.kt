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
data class SurahEntry(val number: Int, val nameAr: String, val firstPage: Int)

/** One juz in the navigation index: number and the page it begins on. */
data class JuzEntry(val number: Int, val firstPage: Int)

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

    val initialPage: Int =
        runBlocking(Dispatchers.IO) { store.lastPage() }.coerceIn(1, pageCount.coerceAtLeast(1))

    var darkTheme by mutableStateOf(false)
        private set

    /** When on, the page fills the screen (width-fill + scroll on wide screens, height-stretch
     *  on tall screens); when off, the whole page is fitted and centered. */
    var fillScreen by mutableStateOf(false)
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

    // Session tracking (one row per foreground period, committed on stop).
    private var sessionStart = 0L
    private var sessionStartPage = initialPage
    private val visitedPages = linkedSetOf(initialPage)
    private var lastPage = initialPage

    init {
        viewModelScope.launch { ayahData = withContext(Dispatchers.IO) { ayahRepo.loadAll() } }
        viewModelScope.launch { bookmarks = withContext(Dispatchers.IO) { store.bookmarks() } }
    }

    fun toggleTheme() { darkTheme = !darkTheme }

    fun toggleFillScreen() { fillScreen = !fillScreen }

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

    fun beginSession() {
        sessionStart = System.currentTimeMillis()
        sessionStartPage = lastPage
        visitedPages.clear()
        visitedPages.add(lastPage)
    }

    fun commitSession() {
        if (sessionStart <= 0L) return
        val start = sessionStart
        val startPage = sessionStartPage
        val end = System.currentTimeMillis()
        val endPage = lastPage
        val pages = visitedPages.size
        sessionStart = 0L
        viewModelScope.launch { statsRepo.commitSession(start, end, startPage, endPage, pages) }
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
                    byNumber[m.surahNumber] = SurahEntry(m.surahNumber, m.surahNameAr, m.page)
                }
            }
        }
        return byNumber.values.sortedBy { it.number }
    }

    /** The 30 juz in order, each mapped to the lowest page it appears on. */
    fun juzIndex(): List<JuzEntry> {
        val byNumber = HashMap<Int, Int>()
        for (markers in ayahData.byPage.values) {
            for (m in markers) {
                if (m.juz <= 0) continue
                val cur = byNumber[m.juz]
                if (cur == null || m.page < cur) byNumber[m.juz] = m.page
            }
        }
        return byNumber.entries.sortedBy { it.key }.map { JuzEntry(it.key, it.value) }
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
