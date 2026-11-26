package com.mushaf.reader.reader

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.AyahRepository
import com.mushaf.reader.data.AyahSearchIndex
import com.mushaf.reader.data.AyahNeighbours
import com.mushaf.reader.data.JuzLayout
import com.mushaf.reader.data.PageRepository
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.notes.NoteEntity
import com.mushaf.reader.data.notes.NotesRepository
import com.mushaf.reader.data.SearchTier
import com.mushaf.reader.data.SurahAyahCounts
import com.mushaf.reader.data.backup.BackupSnapshot
import com.mushaf.reader.data.sync.PlacePoint
import com.mushaf.reader.data.RootSearch
import com.mushaf.reader.data.content.CenterExtrasRepository
import com.mushaf.reader.data.content.RootIndexRow
import com.mushaf.reader.data.packs.PackDownloads
import com.mushaf.reader.data.stats.FullStats
import com.mushaf.reader.data.stats.KhatmaEntity
import com.mushaf.reader.data.stats.ReadingStats
import com.mushaf.reader.data.stats.ReadingSessionTracker
import com.mushaf.reader.data.stats.SessionEntity
import com.mushaf.reader.data.stats.StatsRepository
import com.mushaf.reader.data.recitation.AyahRef
import com.mushaf.reader.data.recitation.RecitationRepository
import com.mushaf.reader.recitation.MobileData
import com.mushaf.reader.recitation.MobileDataPolicy
import com.mushaf.reader.recitation.RecitationController
import com.mushaf.reader.recitation.RecitationDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** The floating recitation handle starts a little below the middle of the screen edge, in thumb
 *  reach on a phone held in the right hand. */
private const val DEFAULT_HANDLE_POS = 0.62f

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val pageRepo = PageRepository(app)
    private val ayahRepo = AyahRepository(app)
    private val centerExtras = CenterExtrasRepository(app)
    private val store = ReadingStore(app)
    private val statsRepo = StatsRepository(app)
    private val notesRepo = NotesRepository(app)

    private val recitationRepo = RecitationRepository(app)

    /** Recitation downloads and the audio kept from listening. */
    val recitationDownloads = RecitationDownloads(app, viewModelScope, recitationRepo)

    /** The downloadable tafsir packs: the library's catalog, downloads and installed packs. */
    val packDownloads = PackDownloads(app, viewModelScope)

    /** The explanation sheet: its sources, pins, text size, and what it shows for an ayah. */
    val explanation = ExplanationController(app, viewModelScope, store, centerExtras)

    /** Recitation: the player bar, the reciter list and the ayah being recited. */
    val recitation = RecitationController(
        app = app,
        scope = viewModelScope,
        repo = recitationRepo,
        store = store,
        downloads = recitationDownloads,
        surahName = ::surahNameAr,
    )

    /** Whether recitation may use mobile data; read and written straight through. */
    var mobileDataPolicy by mutableStateOf(MobileData.policy(app))
        private set

    fun updateMobileDataPolicy(value: MobileDataPolicy) {
        MobileData.setPolicy(getApplication(), value)
        mobileDataPolicy = value
        recitationDownloads.applyMobileDataPolicy()
    }

    /** Re-read after the reader answered the first-time prompt in the bar. */
    fun refreshMobileDataPolicy() {
        mobileDataPolicy = MobileData.policy(getApplication())
    }

    /** Whether the "what's new" card is up: once per [WhatsNew.RELEASE], and only after an update. */
    var showWhatsNew by mutableStateOf(false)
        private set

    private fun loadWhatsNew() = viewModelScope.launch {
        if (store.whatsNewSeen() >= WhatsNew.RELEASE) return@launch
        val app = getApplication<Application>()
        val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        // A fresh install has nothing "new" to compare against: the guide in the More menu is
        // where it learns the app, so its card is marked seen without showing.
        val updated = info != null && info.lastUpdateTime > info.firstInstallTime
        if (updated) showWhatsNew = true else store.setWhatsNewSeen(WhatsNew.RELEASE)
    }

    fun dismissWhatsNew() {
        showWhatsNew = false
        viewModelScope.launch { store.setWhatsNewSeen(WhatsNew.RELEASE) }
    }

    /** Turn the page with the recitation. Off lets the reader browse while it plays on. */
    var recitationFollowPage by mutableStateOf(true)
        private set

    fun updateRecitationFollowPage(value: Boolean) {
        recitationFollowPage = value
        viewModelScope.launch { store.setRecitationFollowPage(value) }
    }

    /** The floating recitation handle's place along the screen edge (0 top .. 1 bottom). */
    var recitationHandlePos by mutableFloatStateOf(DEFAULT_HANDLE_POS)
        private set

    fun dragRecitationHandle(value: Float) {
        recitationHandlePos = value.coerceIn(0f, 1f)
    }

    fun saveRecitationHandle() {
        val value = recitationHandlePos
        viewModelScope.launch { store.setRecitationHandlePos(value) }
    }

    /** How solid the folded recitation handle is drawn, 25..100 percent. */
    var recitationHandleOpacity by mutableIntStateOf(ReadingStore.DEFAULT_RECITATION_HANDLE_OPACITY)
        private set

    fun updateRecitationHandleOpacity(value: Int) {
        val normalized = value.coerceIn(25, 100)
        if (normalized == recitationHandleOpacity) return
        recitationHandleOpacity = normalized
        viewModelScope.launch { store.setRecitationHandleOpacity(normalized) }
    }










    init {
        viewModelScope.launch {
            recitationFollowPage = store.recitationFollowPage()
            store.recitationHandlePos()?.let { recitationHandlePos = it }
            recitationHandleOpacity = store.recitationHandleOpacity()
        }
        // Only lists the pack files already on the device; nothing here touches the network.
        packDownloads.start()
        explanation.start()
        loadWhatsNew()
    }

    val pageCount: Int = pageRepo.pageCount()

    private var ayahData by mutableStateOf(AyahRepository.Data(emptyMap(), emptyMap(), 1106, 1789))
    val imageWidth: Int get() = ayahData.imageWidth
    val imageHeight: Int get() = ayahData.imageHeight

    // Persisted display/position settings, read once synchronously so the first frame already
    // reflects the user's last theme/fit choice (no flash of the default light/fitted view).
    private val initialSettings = runBlocking(Dispatchers.IO) {
        // Has to run before the first read, and before anything else writes: the reconciliation
        // tells a fresh install from an existing one by whether this install has ever written a
        // preference, which is only true of a fresh one at this exact point.
        store.migrateDefaults()
        store.settings()
    }
    private val initialLastBackup = runBlocking(Dispatchers.IO) { store.lastBackup() }

    val initialPage: Int = initialSettings.lastPage.coerceIn(1, pageCount.coerceAtLeast(1))

    /** Theme, page fill, header buttons, progress bars: how the reader wants the page shown. */
    val display = DisplaySettings(store, viewModelScope, initialSettings)

    /** When the current (in-progress) khatma cycle began, epoch millis. */
    var khatmaStartedAt by mutableStateOf(
        initialSettings.khatmaStartedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
    )
        private set

    /** Archive of completed khatmas, newest first. */
    var khatmas by mutableStateOf<List<KhatmaEntity>>(emptyList())
        private set

    /** Start time (ms) of the current reading session; 0 when none has started.
     *  Observable so the header timer updates when a new session begins. */
    var sessionStartedAt by mutableStateOf(0L)
        private set

    var selectedAyah by mutableStateOf<AyahMarker?>(null)
        private set



    /**
     * The verse each coloured bookmark holds, by slot (gold, violet, red, teal); null where unset.
     * Each is a single place: setting it on another ayah moves it there.
     */
    var bookmarks by mutableStateOf<List<String?>>(List(ReadingStore.BOOKMARK_SLOTS) { null })
        private set

    /** Every note the reader wrote, in mushaf order; kept current from the database. */
    var notes by mutableStateOf<List<NoteEntity>>(emptyList())
        private set

    private val notesByPlace by derivedStateOf { notes.associateBy { it.surah to it.ayah } }

    /** The verse keys that carry a note, for the mark on the page. */
    val notedVerseKeys: Set<String> by derivedStateOf { notes.mapNotNullTo(HashSet()) { it.verseKey } }

    /** A note that could not be saved or deleted, for the reader to be told; cleared once shown. */
    var noteError by mutableStateOf<String?>(null)
        private set

    /** The name the reader gave each bookmark, by slot; blank where it goes by its colour. */
    var bookmarkNames by mutableStateOf(List(ReadingStore.BOOKMARK_SLOTS) { "" })
        private set

    /** The bookmark slots the reader chose not to see. A hidden one keeps its place. */
    var hiddenBookmarks by mutableStateOf(initialSettings.hiddenBookmarks)
        private set

    /** The slots the menus list and the page colours, in slot order; gold is always among them. */
    val visibleBookmarkSlots: List<Int> by derivedStateOf {
        (0 until ReadingStore.BOOKMARK_SLOTS).filter { it !in hiddenBookmarks }
    }

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



    /** Backup file, cloud account, reading sync and cloud faults. */
    val backupSync = BackupSyncController(
        app = app,
        scope = viewModelScope,
        store = store,
        host = object : BackupSyncHost {
            override val currentPage: Int get() = lastPage
            override val goldBookmark: String? get() = bookmarks[ReadingStore.PRIMARY_BOOKMARK]
            override suspend fun checkpointForBackup() = checkpointCurrentSessionForBackup()
            override suspend fun applyRestored(snapshot: BackupSnapshot) = applyRestoredSnapshot(snapshot)
            override suspend fun reloadKhatmas() {
                khatmas = statsRepo.allKhatmas()
            }
        },
        initialLastBackup = initialLastBackup,
    )

    /** A restored page is consumed by ReaderScreen so the live pager follows the imported state. */
    var restorePageRequest by mutableStateOf<Int?>(null)
        private set

    private var lastPage = initialPage
    private val readingSession = ReadingSessionTracker(
        initialPage = initialPage,
        elapsedRealtime = SystemClock::elapsedRealtime,
        wallTimeMillis = System::currentTimeMillis,
        onPageVisited = ::markVisited,
        onPageRead = ::markRead,
    )
    private var pendingProgressWrite: Job? = null
    private var pendingSessionWrite: Job? = null

    init {
        viewModelScope.launch { ayahData = withContext(Dispatchers.IO) { ayahRepo.loadAll() } }
        viewModelScope.launch {
            bookmarks = withContext(Dispatchers.IO) { store.bookmarkSlots() }.map { it.firstOrNull() }
        }
        viewModelScope.launch { bookmarkNames = withContext(Dispatchers.IO) { store.bookmarkNames() } }
        viewModelScope.launch { notesRepo.notes.collect { notes = it } }
        viewModelScope.launch { khatmas = withContext(Dispatchers.IO) { statsRepo.allKhatmas() } }
        backupSync.start()
        // Anchor the current khatma cycle on first run of the feature so its duration is meaningful.
        if (initialSettings.khatmaStartedAt <= 0L) {
            viewModelScope.launch { store.setKhatmaStartedAt(khatmaStartedAt) }
        }
    }

    fun consumeRestorePageRequest() {
        restorePageRequest = null
    }

    /** Goes to [point] on this device — a page turn, never a bookmark. */
    fun goToSyncedPlace(point: PlacePoint) {
        restorePageRequest = point.page
        point.verseKey?.let { flashSearchResult(it, point.page) }
        backupSync.dismissElsewhere()
    }

    /** "البقرة ٢٥٥" for a verse key, or null before the ayah index has loaded. */
    fun describeVerse(verseKey: String): String? {
        val page = ayahData.keyToPage[verseKey] ?: return null
        val marker = ayahData.byPage[page]?.firstOrNull { it.verseKey == verseKey } ?: return null
        return "${marker.surahNameAr} ${marker.ayahNumber.toArabicDigits()}"
    }

    /** The surah a page opens with, for "ص ٤٢ · البقرة". */
    fun surahOfPage(page: Int): String? = ayahData.byPage[page]?.firstOrNull()?.surahNameAr

    private suspend fun recordBookmarkForSync(verseKey: String?) {
        val page = verseKey?.let { ayahData.keyToPage[it] }
        backupSync.recordBookmark(verseKey, page)
    }

    /** A file picker can stop the activity first, so await that commit as well as this one. */
    private suspend fun checkpointCurrentSessionForBackup() {
        commitSession()
        pendingSessionWrite?.join()
        pendingProgressWrite?.join()
    }

    private suspend fun applyRestoredSnapshot(snapshot: BackupSnapshot) {
        val value = snapshot.reading.settings
        display.apply(value)
        hiddenBookmarks = value.hiddenBookmarks
        khatmaStartedAt = value.khatmaStartedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        bookmarks = snapshot.reading.bookmarks.map { it.firstOrNull() }
        bookmarkNames = snapshot.reading.bookmarkNames.map(ReadingStore::cleanBookmarkName)
        visitedPagesAll = value.visitedPages
        readPagesAll = value.readPages
        lastPage = value.lastPage.coerceIn(1, pageCount)
        sessions = statsRepo.allSessions()
        khatmas = statsRepo.allKhatmas()
        stats = statsRepo.summary(lastPage, pageCount, bookmarkPage())
        fullStats = statsRepo.fullStats(lastPage, pageCount, bookmarkPage())

        readingSession.reset(lastPage)
        sessionStartedAt = 0L
        restorePageRequest = lastPage
    }

    fun assetModel(pageNumber: Int): String = pageRepo.assetUri(pageNumber)

    fun markersForPage(pageNumber: Int): List<AyahMarker> = ayahData.byPage[pageNumber].orEmpty()

    fun hizbMarkersForPage(pageNumber: Int) = ayahData.hizbMarkersByPage[pageNumber].orEmpty()

    fun selectAyah(ayah: AyahMarker?) { selectedAyah = ayah }

    /** The ayah [direction] steps away from [ayah] in mushaf order (+1 next, -1 previous), if any. */
    fun neighbourAyah(ayah: AyahMarker, direction: Int): AyahMarker? {
        val (surah, number) = AyahNeighbours.step(ayah.surahNumber, ayah.ayahNumber, direction) ?: return null
        val key = "$surah:$number"
        val page = ayahData.keyToPage[key] ?: return null
        return ayahData.byPage[page]?.firstOrNull { it.verseKey == key }
    }

    fun clearSelection() { selectedAyah = null }

    /** The ayah a search result jumped to, flashed on its page until it fades or the page is touched. */
    var searchFlash by mutableStateOf<SearchFlash?>(null)
        private set

    fun flashSearchResult(verseKey: String, page: Int) {
        // A fresh token restarts the flash even when the same result is opened twice in a row.
        searchFlash = SearchFlash(verseKey, page, token = System.nanoTime())
    }

    fun clearSearchFlash() { searchFlash = null }








    /**
     * Puts bookmark [slot] on [ayah], moving it from wherever it was; tapping the slot that
     * already holds this ayah clears it. Only the first (gold) slot is shared with the reader's
     * other devices — the reading-sync record carries one bookmark.
     */
    fun toggleBookmark(slot: Int, ayah: AyahMarker) {
        setBookmark(slot, if (bookmarks[slot] == ayah.verseKey) null else ayah.verseKey)
    }

    /** Takes bookmark [slot] off wherever it is, without going there first. */
    fun clearBookmark(slot: Int) {
        if (bookmarks[slot] != null) setBookmark(slot, null)
    }

    private fun setBookmark(slot: Int, verseKey: String?) {
        bookmarks = bookmarks.toMutableList().also { it[slot] = verseKey }
        viewModelScope.launch {
            store.setBookmarkSlot(slot, setOfNotNull(verseKey))
            if (slot == 0) recordBookmarkForSync(verseKey)
        }
    }

    /** The note on [surah]:[ayah] (or on the whole surah when [ayah] is 0), if there is one. */
    fun noteAt(surah: Int, ayah: Int): NoteEntity? = notesByPlace[surah to ayah]

    /** Saves [text] as the note at [surah]:[ayah]; blank text deletes it. */
    fun saveNote(surah: Int, ayah: Int, text: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { notesRepo.save(surah, ayah, text) }
            } catch (error: Exception) {
                noteError = "تعذر حفظ الملاحظة. حاول مرة أخرى."
            }
        }
    }

    fun deleteNote(surah: Int, ayah: Int) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { notesRepo.delete(surah, ayah) }
            } catch (error: Exception) {
                noteError = "تعذر حذف الملاحظة. حاول مرة أخرى."
            }
        }
    }

    fun clearNoteError() { noteError = null }

    /** The page a note opens on: its ayah's, or the first page of its surah. */
    fun notePage(note: NoteEntity): Int? =
        if (note.isSurahNote) surahStartPage(note.surah)
        else ayahData.keyToPage[note.verseKey]

    /** The Uthmani text of [verseKey], shown above its note while it is written. */
    fun ayahText(verseKey: String): String? {
        val page = ayahData.keyToPage[verseKey] ?: return null
        return ayahData.byPage[page]?.firstOrNull { it.verseKey == verseKey }?.textUthmani
    }

    /** The page that opens [surah]: the page of its first ayah. */
    fun surahStartPage(surah: Int): Int? = ayahData.keyToPage["$surah:1"]

    /** What bookmark [slot] is called: the reader's name for it, or its colour's. */
    fun bookmarkTitle(slot: Int): String = bookmarkTitle(slot, bookmarkNames[slot])

    /** Saves the names from the bookmarks screen; a blank one goes back to the colour's name. */
    fun saveBookmarkNames(names: List<String>) {
        val clean = names.map(ReadingStore::cleanBookmarkName)
        if (clean == bookmarkNames) return
        bookmarkNames = clean
        viewModelScope.launch { store.setBookmarkNames(clean) }
    }

    /** Saves which bookmarks the bookmarks screen left hidden. */
    fun saveHiddenBookmarks(slots: Set<Int>) {
        val clean = ReadingStore.cleanHiddenBookmarks(slots)
        if (clean == hiddenBookmarks) return
        hiddenBookmarks = clean
        viewModelScope.launch { store.setHiddenBookmarks(clean) }
    }

    /** Where bookmark [slot] stands, for the menus that list the four; null when it is unset. */
    fun bookmarkPlace(slot: Int): BookmarkPlace? {
        val key = bookmarks[slot] ?: return null
        val page = ayahData.keyToPage[key] ?: return null
        return BookmarkPlace(verseKey = key, page = page, verse = describeVerse(key) ?: key)
    }

    /** The gold bookmark's page: the one the statistics and the khatma map point at. */
    private fun bookmarkPage(): Int? = bookmarkJumpPage(0)

    fun bookmarkJumpPage(slot: Int = 0): Int? = bookmarks[slot]?.let { ayahData.keyToPage[it] }

    fun saveLastPage(page: Int) {
        lastPage = page
        viewModelScope.launch { store.setLastPage(page) }
    }

    /** Called when [page] becomes the visible page: records the dwell time on the page just left
     *  (→ "read" if long enough), marks the new page visited, and persists the reading position. */
    fun onPageVisible(page: Int) {
        readingSession.changePage(page)
        // Only a real move counts as reading here: reopening the app on its saved page is not news
        // for the other device, and must not hide a newer place found there.
        if (page != lastPage) {
            backupSync.recordPageTurn(System.currentTimeMillis())
        }
        saveLastPage(page)
    }

    /** Only the resumed, focused and unobscured reader runs either reading clock. */
    fun setReaderVisible(visible: Boolean) {
        readingSession.setReading(visible)
        sessionStartedAt = readingSession.startedAt ?: 0L
    }

    fun currentSessionDurationMs(): Long = readingSession.durationMs()

    private fun markVisited(page: Int) {
        if (visitedPagesAll.contains(page)) return
        val next = visitedPagesAll + page
        visitedPagesAll = next
        persistPageProgress()
    }

    /** Marking a page read implies it was opened, so the khatma map's "read is a subset of visited"
     *  rule is enforced here — at the one place that adds to the read set. Without it, any reset
     *  that empties both sets while the dwell timer is still running (clearing the statistics, or
     *  starting a khatma while already on page 1) leaves the next dwell writing a read page that
     *  was never recorded as visited, and the backup taken afterwards carries the contradiction. */
    private fun markRead(page: Int) {
        if (readPagesAll.contains(page)) return
        val nextRead = readPagesAll + page
        readPagesAll = nextRead
        val nextVisited = visitedPagesAll + page
        visitedPagesAll = nextVisited
        persistPageProgress()
    }

    /** Manually flip a page's "read" state on the khatma map (tap a cell to mark/unmark read).
     *  Keeps read ⊆ visited: marking adds to both sets, unmarking removes from both. */
    fun togglePageRead(page: Int) {
        if (page < 1 || page > pageCount) return
        val markRead = !readPagesAll.contains(page)
        val nextRead = if (markRead) readPagesAll + page else readPagesAll - page
        val nextVisited = if (markRead) visitedPagesAll + page else visitedPagesAll - page
        readPagesAll = nextRead
        visitedPagesAll = nextVisited
        persistPageProgress()
    }

    private fun persistPageProgress() {
        val read = readPagesAll
        val visited = visitedPagesAll
        val previous = pendingProgressWrite
        pendingProgressWrite = viewModelScope.launch {
            previous?.join()
            store.setReadPages(read)
            store.setVisitedPages(visited)
        }
    }

    fun commitSession() {
        val session = readingSession.finish()
        sessionStartedAt = 0L
        if (session != null) {
            val previous = pendingSessionWrite
            pendingSessionWrite = viewModelScope.launch {
                previous?.join()
                statsRepo.commitSession(session)
            }
        }
    }

    /** Wipe ALL statistics: every session plus the khatma-map progress (visited/read pages). */
    fun clearAllStats() {
        viewModelScope.launch {
            pendingSessionWrite?.join()
            pendingProgressWrite?.join()
            statsRepo.clearAllSessions()
            visitedPagesAll = emptySet()
            readPagesAll = emptySet()
            store.setVisitedPages(emptySet())
            store.setReadPages(emptySet())
            readingSession.reset(lastPage)
            sessionStartedAt = readingSession.startedAt ?: 0L
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
        // Stop immediately on the tap; composing the screen will keep the reader paused.
        setReaderVisible(false)
        viewModelScope.launch {
            sessions = statsRepo.allSessions()
            khatmas = statsRepo.allKhatmas()
            fullStats = statsRepo.fullStats(
                currentPage = lastPage,
                totalPages = pageCount,
                bookmarkPage = bookmarkPage(),
            )
        }
    }

    /** Archive the current khatma as complete (today's date), then reset progress for a new cycle. */
    fun completeKhatma() {
        val now = System.currentTimeMillis()
        val started = khatmaStartedAt
        val days = ((now - started).coerceAtLeast(0L) / 86_400_000L).toInt()
        viewModelScope.launch {
            statsRepo.saveKhatma(now, started, days, pageCount)
            khatmas = statsRepo.allKhatmas()
            backupSync.queueUpload()
            resetKhatmaProgressInternal(now)
        }
    }

    /** Start a new khatma: clear page/juz progress and reset the cycle start. Sessions and the
     *  saved-khatma archive are NOT touched. */
    fun resetKhatmaProgress() {
        viewModelScope.launch { resetKhatmaProgressInternal(System.currentTimeMillis()) }
    }

    private suspend fun resetKhatmaProgressInternal(now: Long) {
        pendingProgressWrite?.join()
        visitedPagesAll = emptySet()
        readPagesAll = emptySet()
        store.setVisitedPages(emptySet())
        store.setReadPages(emptySet())
        khatmaStartedAt = now
        store.setKhatmaStartedAt(now)
        // A new cycle starts from the beginning: move the reader to page 1 and re-anchor the dwell
        // there, so the page left behind can't be credited to the fresh khatma.
        saveLastPage(1)
        readingSession.resetPage(1)
        restorePageRequest = 1
        fullStats = statsRepo.fullStats(
            currentPage = lastPage,
            totalPages = pageCount,
            bookmarkPage = bookmarkPage(),
        )
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

    /** Number of ayahs in [surahNumber] (1..114); 0 if out of range. */
    fun surahAyahCount(surahNumber: Int): Int = SurahAyahCounts.of(surahNumber)

    private var surahNamesCache: Map<Int, String> = emptyMap()

    /** Arabic name of [surahNumber], from the loaded page data; its number until that loads. */
    fun surahNameAr(surahNumber: Int): String {
        if (surahNamesCache.isEmpty() && ayahLoaded) {
            surahNamesCache = ayahData.byPage.values.asSequence().flatten()
                .associate { it.surahNumber to it.surahNameAr }
        }
        return surahNamesCache[surahNumber] ?: surahNumber.toString()
    }

    /** The surahs with at least one ayah in [juz], by this app's page split of the juz. */
    fun surahsInJuz(juz: Int): List<Int> {
        val first = juzIndex().firstOrNull { it.number == juz }?.firstPage ?: return emptyList()
        val next = juzIndex().firstOrNull { it.number == juz + 1 }?.firstPage ?: (pageCount + 1)
        return (first until next).flatMap { markersForPage(it) }.map { it.surahNumber }.distinct()
    }

    /** The marker of [ref] on its page, for highlighting the ayah being recited. */
    fun markerFor(ref: AyahRef): AyahMarker? {
        val page = ayahData.keyToPage[ref.verseKey] ?: return null
        return ayahData.byPage[page]?.firstOrNull { it.verseKey == ref.verseKey }
    }

    override fun onCleared() {
        recitation.release()
    }

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

    /** Juz number, current page within the juz, and total pages in the juz. See [JuzLayout] for why
     *  the split is even and therefore differs from the paper mushaf on two pages. */
    fun juzInfoForPage(page: Int): JuzLayout.Position = JuzLayout.positionOf(page, pageCount)

    // Built once off the main thread, on the first search after the ayah data has loaded.
    @Volatile
    private var searchIndex: AyahSearchIndex? = null

    private fun ensureSearchIndex(): AyahSearchIndex? {
        searchIndex?.let { return it }
        val all = ayahData.byPage.values.flatten()
        if (all.isEmpty()) return null
        return AyahSearchIndex.build(all).also { searchIndex = it }
    }

    /**
     * Search ayah text and surah names (diacritic-insensitive). Also matches a "2:255" verse key.
     *
     * Runs off the main thread: the first call folds all 6236 ayahs to build the index, every later
     * call scans it. The caller debounces, so this never runs once per keystroke.
     *
     * Every matching ayah is returned, so the screen can say how many there are.
     * See [AyahSearchIndex] for how they are ranked and why the index carries both orthographies.
     */
    suspend fun search(query: String): SearchResults =
        withContext(Dispatchers.Default) {
            val index = ensureSearchIndex() ?: return@withContext SearchResults()
            val results = index.search(query)
            SearchResults(
                surahs = results.surahs.map { SurahSearchResult(it.number, it.nameAr, it.firstPage) },
                ayahs = results.ayahs.map { it.marker.toSearchResult(it.tier, it.highlights) },
                roots = ensureRootIndex()?.match(query).orEmpty(),
            )
        }

    private var rootIndex: RootSearch.Index? = null

    /** Built from the bundled roots on first use; null when they cannot be read. */
    private suspend fun ensureRootIndex(): RootSearch.Index? {
        rootIndex?.let { return it }
        val rows = runCatching { centerExtras.rootIndex() }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull() ?: return null
        return RootSearch.Index(rows.map { it.toRoot() }).also { rootIndex = it }
    }

    /** The ayahs holding words from [root], in mushaf order; [form] narrows them to that form. */
    suspend fun rootResults(root: RootSearch.Root, form: Int?): RootResults =
        withContext(Dispatchers.Default) {
            val index = ensureSearchIndex() ?: return@withContext RootResults(root, form, emptyList())
            val packed = runCatching { centerExtras.rootOccurrences(root.id) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull().orEmpty()
            val words = HashMap<String, MutableSet<Int>>()
            for (o in RootSearch.parseOccurrences(packed)) {
                if (form == null || o.form == form) words.getOrPut(o.verseKey) { HashSet() } += o.word
            }
            RootResults(root, form, index.hitsAt(words).map { it.marker.toSearchResult(it.tier, it.highlights) })
        }
}

private fun RootIndexRow.toRoot() =
    RootSearch.Root(id, root, wordCount, ayahCount, RootSearch.parseForms(forms))

private fun AyahMarker.toSearchResult(tier: SearchTier, highlights: List<IntRange>) =
    SearchResult(verseKey, surahNameAr, ayahNumber, page, textUthmani, tier, highlights)

/** The order the sources menu and the library list the packs in. */
