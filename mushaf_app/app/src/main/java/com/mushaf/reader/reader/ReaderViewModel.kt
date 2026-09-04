package com.mushaf.reader.reader

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.AyahRepository
import com.mushaf.reader.data.AyahSearchIndex
import com.mushaf.reader.data.JuzLayout
import com.mushaf.reader.data.PageRepository
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.backup.BackupException
import com.mushaf.reader.data.backup.BackupFileInfo
import com.mushaf.reader.data.backup.BackupSnapshot
import com.mushaf.reader.data.backup.BackupStage
import com.mushaf.reader.data.backup.BackupUiState
import com.mushaf.reader.data.backup.FileBackupRepository
import com.mushaf.reader.data.content.GharibMeaning
import com.mushaf.reader.data.content.QuranContentRepository
import com.mushaf.reader.data.stats.FullStats
import com.mushaf.reader.data.stats.KhatmaEntity
import com.mushaf.reader.data.stats.ReadingStats
import com.mushaf.reader.data.stats.SessionEntity
import com.mushaf.reader.data.stats.StatsRepository
import com.mushaf.reader.ui.theme.MushafPalette
import com.mushaf.reader.ui.theme.paletteFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One surah in the navigation index: number, Arabic name, and the page it begins on. */
data class SurahEntry(val number: Int, val nameAr: String, val firstPage: Int, val ayahCount: Int)

/** One juz in the navigation index: number and the page it begins on. */
data class JuzEntry(val number: Int, val firstPage: Int, val ayahCount: Int)

/** A single search hit. */
data class SearchResult(
    val verseKey: String,
    val surahNameAr: String,
    val ayahNumber: Int,
    val page: Int,
    val text: String,
    /** True for a hit only the stemmed pass reached — a widened, less certain match that the
     *  search screen separates from the exact ones. See [AyahSearchIndex]. */
    val expanded: Boolean = false,
)

data class AyahExplanationUiState(
    val verseKey: String? = null,
    val loading: Boolean = false,
    val tafsirHtml: String? = null,
    val meanings: List<GharibMeaning> = emptyList(),
    val errorMessage: String? = null,
)

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val pageRepo = PageRepository(app)
    private val ayahRepo = AyahRepository(app)
    private val contentRepo = QuranContentRepository(app)
    private val store = ReadingStore(app)
    private val statsRepo = StatsRepository(app)
    private val backupRepo = FileBackupRepository(app)

    val pageCount: Int = pageRepo.pageCount()

    private var ayahData by mutableStateOf(AyahRepository.Data(emptyMap(), emptyMap(), 1106, 1789))
    val imageWidth: Int get() = ayahData.imageWidth
    val imageHeight: Int get() = ayahData.imageHeight

    // Persisted display/position settings, read once synchronously so the first frame already
    // reflects the user's last theme/fit choice (no flash of the default light/fitted view).
    private val initialSettings = runBlocking(Dispatchers.IO) { store.settings() }
    private val initialLastBackup = runBlocking(Dispatchers.IO) { store.lastBackup() }

    val initialPage: Int = initialSettings.lastPage.coerceIn(1, pageCount.coerceAtLeast(1))

    /** Id of the chosen reading theme; [palette] resolves it to the colors it stands for. */
    var themeId by mutableStateOf(initialSettings.themeId)
        private set

    val palette: MushafPalette get() = paletteFor(themeId)

    /** When on, the page fills the screen (width-fill + scroll on wide screens, height-stretch
     *  on tall screens); when off, the whole page is fitted and centered. */
    var fillScreen by mutableStateOf(initialSettings.fillScreen)
        private set

    /** When on, pages turn vertically (swipe up/down) instead of horizontally. */
    var verticalPaging by mutableStateOf(initialSettings.verticalPaging)
        private set

    /** When on, the screen is held awake while the reader is in the foreground. */
    var keepScreenOn by mutableStateOf(initialSettings.keepScreenOn)
        private set

    /** How far the corner controls and the page pull back from a curved screen edge; one of the
     *  ReadingStore.EDGE_MARGIN_* ids. "none" is today's layout, and stays the default. */
    var edgeMargin by mutableStateOf(initialSettings.edgeMargin)
        private set

    /** Ids of the header buttons the user has hidden; any id NOT in this set is shown. */
    var hiddenButtons by mutableStateOf(initialSettings.hiddenButtons)
        private set

    /** Ids drawn on the top bar itself; a visible action not in here lives in the More menu. */
    var barButtons by mutableStateOf(initialSettings.barButtons)
        private set

    /** Color id per header action, for the icon it draws on the bar. Missing = "muted". */
    var buttonColors by mutableStateOf(initialSettings.buttonColors)
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

    /** Full-screen restore-header chip: show the page number, and its color. */
    var showButtonPage by mutableStateOf(initialSettings.showButtonPage)
        private set

    var buttonPageColor by mutableStateOf(initialSettings.buttonPageColor)
        private set

    /** Opacity of the draggable restore-header button, 25..100 percent. */
    var showHeaderButtonOpacity by mutableStateOf(initialSettings.showHeaderButtonOpacity)
        private set

    /** Where the user parked the full-screen chip, as a 0..1 fraction of its vertical travel
     *  (0 = just under the top inset, 1 = bottom edge); -1 = never moved, so it rests at the top.
     *  A fraction — not pixels — so the spot holds across rotation and different screen sizes. */
    var buttonPosFraction by mutableStateOf(initialSettings.buttonPosFraction)
        private set

    /** Thin juz-progress bar pinned to the bottom of the page, its color, and its thickness (dp). */
    var showBottomJuzBar by mutableStateOf(initialSettings.showBottomJuzBar)
        private set

    var bottomJuzBarColor by mutableStateOf(initialSettings.bottomJuzBarColor)
        private set

    var bottomJuzBarThickness by mutableStateOf(initialSettings.bottomJuzBarThickness)
        private set

    /** How solid the bar is drawn, 0..100 percent. */
    var bottomJuzBarOpacity by mutableStateOf(initialSettings.bottomJuzBarOpacity)
        private set

    /** Thin surah-progress bar sitting just above the page, its color, and its thickness (dp). */
    var showTopSurahBar by mutableStateOf(initialSettings.showTopSurahBar)
        private set

    var topSurahBarColor by mutableStateOf(initialSettings.topSurahBarColor)
        private set

    var topSurahBarThickness by mutableStateOf(initialSettings.topSurahBarThickness)
        private set

    var topSurahBarOpacity by mutableStateOf(initialSettings.topSurahBarOpacity)
        private set

    /** Full-screen edge marker for the current page's side (right/left of the spread): whether it
     *  shows, its color, and its bar width (thickness) and height (length) in dp. */
    var showPageSideIndicator by mutableStateOf(initialSettings.showPageSideIndicator)
        private set

    var pageSideIndicatorColor by mutableStateOf(initialSettings.pageSideIndicatorColor)
        private set

    var pageSideIndicatorThickness by mutableStateOf(initialSettings.pageSideIndicatorThickness)
        private set

    var pageSideIndicatorLength by mutableStateOf(initialSettings.pageSideIndicatorLength)
        private set

    var pageSideIndicatorOpacity by mutableStateOf(initialSettings.pageSideIndicatorOpacity)
        private set

    /** When the current (in-progress) khatma cycle began, epoch millis. */
    var khatmaStartedAt by mutableStateOf(
        initialSettings.khatmaStartedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
    )
        private set

    /** Archive of completed khatmas, newest first. */
    var khatmas by mutableStateOf<List<KhatmaEntity>>(emptyList())
        private set

    /** Start time (ms) of the current foreground reading session; 0 when none is running.
     *  Observable so the header timer updates when a new session begins. */
    var sessionStartedAt by mutableStateOf(0L)
        private set

    var selectedAyah by mutableStateOf<AyahMarker?>(null)
        private set

    var explanationState by mutableStateOf(AyahExplanationUiState())
        private set

    private var explanationJob: Job? = null

    var bookmarks by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Second, independent bookmark (different colour); behaves exactly like the first. */
    var bookmarks2 by mutableStateOf<Set<String>>(emptySet())
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

    var backupUiState by mutableStateOf(
        BackupUiState(
            lastBackup = initialLastBackup?.let {
                BackupFileInfo(fileName = it.fileName, savedAt = it.savedAt, sizeBytes = it.sizeBytes)
            }
        )
    )
        private set

    /** A restored page is consumed by ReaderScreen so the live pager follows the imported state. */
    var restorePageRequest by mutableStateOf<Int?>(null)
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
        viewModelScope.launch { bookmarks2 = withContext(Dispatchers.IO) { store.bookmarks2() } }
        viewModelScope.launch { khatmas = withContext(Dispatchers.IO) { statsRepo.allKhatmas() } }
        // Anchor the current khatma cycle on first run of the feature so its duration is meaningful.
        if (initialSettings.khatmaStartedAt <= 0L) {
            viewModelScope.launch { store.setKhatmaStartedAt(khatmaStartedAt) }
        }
    }

    fun updateThemeId(value: String) {
        if (value == themeId) return
        themeId = value
        viewModelScope.launch { store.setThemeId(value) }
    }

    fun toggleFillScreen() {
        fillScreen = !fillScreen
        viewModelScope.launch { store.setFillScreen(fillScreen) }
    }

    fun updateVerticalPaging(value: Boolean) {
        if (value == verticalPaging) return
        verticalPaging = value
        viewModelScope.launch { store.setVerticalPaging(value) }
    }

    fun updateKeepScreenOn(value: Boolean) {
        if (value == keepScreenOn) return
        keepScreenOn = value
        viewModelScope.launch { store.setKeepScreenOn(value) }
    }

    fun updateEdgeMargin(value: String) {
        if (value == edgeMargin) return
        edgeMargin = value
        viewModelScope.launch { store.setEdgeMargin(value) }
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

    fun isButtonInBar(id: String): Boolean = barButtons.contains(id)

    /** Move the header action with [id] onto the bar, or back into the More menu. */
    fun setButtonInBar(id: String, inBar: Boolean) {
        val next = if (inBar) barButtons + id else barButtons - id
        if (next == barButtons) return
        barButtons = next
        viewModelScope.launch { store.setBarButtons(next) }
    }

    /** Color id the action with [id] draws in on the bar; "muted" means keep its own default. */
    fun buttonColor(id: String): String = buttonColors[id] ?: "muted"

    fun updateButtonColor(id: String, color: String) {
        if (buttonColor(id) == color) return
        buttonColors = buttonColors + (id to color)
        viewModelScope.launch { store.setButtonColors(buttonColors) }
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

    fun updateShowButtonPage(value: Boolean) {
        if (value == showButtonPage) return
        showButtonPage = value
        viewModelScope.launch { store.setShowButtonPage(value) }
    }

    fun updateButtonPageColor(value: String) {
        if (value == buttonPageColor) return
        buttonPageColor = value
        viewModelScope.launch { store.setButtonPageColor(value) }
    }

    fun updateShowHeaderButtonOpacity(value: Int) {
        val normalized = value.coerceIn(25, 100)
        if (normalized == showHeaderButtonOpacity) return
        showHeaderButtonOpacity = normalized
        viewModelScope.launch { store.setShowHeaderButtonOpacity(normalized) }
    }

    /** Live drag of the full-screen chip: memory only, so it tracks the finger without a disk
     *  write per pixel. [saveButtonPosFraction] commits the spot when the finger lifts. */
    fun dragButtonPosFraction(value: Float) {
        buttonPosFraction = value.coerceIn(0f, 1f)
    }

    fun saveButtonPosFraction() {
        val value = buttonPosFraction
        viewModelScope.launch { store.setButtonPosFraction(value) }
    }

    fun updateShowBottomJuzBar(value: Boolean) {
        if (value == showBottomJuzBar) return
        showBottomJuzBar = value
        viewModelScope.launch { store.setShowBottomJuzBar(value) }
    }

    fun updateBottomJuzBarColor(value: String) {
        if (value == bottomJuzBarColor) return
        bottomJuzBarColor = value
        viewModelScope.launch { store.setBottomJuzBarColor(value) }
    }

    fun updateBottomJuzBarThickness(value: Int) {
        if (value == bottomJuzBarThickness) return
        bottomJuzBarThickness = value
        viewModelScope.launch { store.setBottomJuzBarThickness(value) }
    }

    fun updateBottomJuzBarOpacity(value: Int) {
        if (value == bottomJuzBarOpacity) return
        bottomJuzBarOpacity = value
        viewModelScope.launch { store.setBottomJuzBarOpacity(value) }
    }

    fun updateShowTopSurahBar(value: Boolean) {
        if (value == showTopSurahBar) return
        showTopSurahBar = value
        viewModelScope.launch { store.setShowTopSurahBar(value) }
    }

    fun updateTopSurahBarColor(value: String) {
        if (value == topSurahBarColor) return
        topSurahBarColor = value
        viewModelScope.launch { store.setTopSurahBarColor(value) }
    }

    fun updateTopSurahBarThickness(value: Int) {
        if (value == topSurahBarThickness) return
        topSurahBarThickness = value
        viewModelScope.launch { store.setTopSurahBarThickness(value) }
    }

    fun updateTopSurahBarOpacity(value: Int) {
        if (value == topSurahBarOpacity) return
        topSurahBarOpacity = value
        viewModelScope.launch { store.setTopSurahBarOpacity(value) }
    }

    fun updateShowPageSideIndicator(value: Boolean) {
        if (value == showPageSideIndicator) return
        showPageSideIndicator = value
        viewModelScope.launch { store.setShowPageSideIndicator(value) }
    }

    fun updatePageSideIndicatorColor(value: String) {
        if (value == pageSideIndicatorColor) return
        pageSideIndicatorColor = value
        viewModelScope.launch { store.setPageSideIndicatorColor(value) }
    }

    fun updatePageSideIndicatorThickness(value: Int) {
        if (value == pageSideIndicatorThickness) return
        pageSideIndicatorThickness = value
        viewModelScope.launch { store.setPageSideIndicatorThickness(value) }
    }

    fun updatePageSideIndicatorLength(value: Int) {
        if (value == pageSideIndicatorLength) return
        pageSideIndicatorLength = value
        viewModelScope.launch { store.setPageSideIndicatorLength(value) }
    }

    fun updatePageSideIndicatorOpacity(value: Int) {
        if (value == pageSideIndicatorOpacity) return
        pageSideIndicatorOpacity = value
        viewModelScope.launch { store.setPageSideIndicatorOpacity(value) }
    }

    fun suggestedBackupFileName(): String = backupRepo.suggestedFileName()

    fun exportBackup(target: Uri) {
        viewModelScope.launch {
            startBackupOperation(BackupStage.Exporting)
            try {
                checkpointCurrentSessionForBackup()
                val saved = backupRepo.exportTo(target)
                store.setLastBackup(
                    ReadingStore.LastBackup(saved.savedAt, saved.fileName, saved.sizeBytes)
                )
                backupUiState = backupUiState.copy(
                    lastBackup = saved,
                    busy = false,
                    stage = null,
                    error = null,
                    message = "حُفظت النسخة في «${saved.fileName}».",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                finishBackupWithError(error)
            }
        }
    }

    fun importBackup(source: Uri) {
        viewModelScope.launch {
            startBackupOperation(BackupStage.Importing)
            try {
                val restored = backupRepo.importFrom(source)
                applyRestoredSnapshot(restored.snapshot)
                backupUiState = backupUiState.copy(
                    busy = false,
                    stage = null,
                    error = null,
                    message = "اكتملت الاستعادة من نسخة ${formatBackupDate(restored.snapshot.createdAt)} " +
                        "(${restored.snapshot.deviceName}).",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                finishBackupWithError(error)
            }
        }
    }

    /** The user closed the system picker without choosing a file. */
    fun backupSelectionCancelled() {
        backupUiState = backupUiState.copy(busy = false, stage = null)
    }

    fun clearBackupFeedback() {
        backupUiState = backupUiState.copy(message = null, error = null)
    }

    fun consumeRestorePageRequest() {
        restorePageRequest = null
    }

    private fun startBackupOperation(stage: BackupStage) {
        backupUiState = backupUiState.copy(
            busy = true,
            stage = stage,
            message = null,
            error = null,
        )
    }

    private fun finishBackupWithError(error: Exception) {
        val message = if (error is BackupException) error.message else null
        backupUiState = backupUiState.copy(
            busy = false,
            stage = null,
            message = null,
            error = message ?: "تعذر إكمال العملية. أعد المحاولة باختيار الملف مرة أخرى.",
        )
    }

    private fun formatBackupDate(time: Long): String =
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar")).format(Date(time))

    /** Persist the active reading slice before exporting, then continue with a fresh slice. */
    private suspend fun checkpointCurrentSessionForBackup() {
        val now = System.currentTimeMillis()
        if (pageEnteredAt > 0L && now - pageEnteredAt >= readDwellMs &&
            !readPagesAll.contains(visiblePage)
        ) {
            readPagesAll = readPagesAll + visiblePage
            visitedPagesAll = visitedPagesAll + visiblePage
            store.setReadPages(readPagesAll)
            store.setVisitedPages(visitedPagesAll)
        }
        if (sessionStart > 0L && now - sessionStart >= 1_000L) {
            statsRepo.commitSession(
                startedAt = sessionStart,
                endedAt = now,
                startPage = sessionStartPage,
                endPage = lastPage,
                pagesRead = visitedPages.size,
            )
        }
        sessionStart = now
        sessionStartedAt = now
        sessionStartPage = lastPage
        visitedPages.clear()
        visitedPages.add(lastPage)
        visiblePage = lastPage
        pageEnteredAt = now
    }

    private suspend fun applyRestoredSnapshot(snapshot: BackupSnapshot) {
        val value = snapshot.reading.settings
        themeId = value.themeId
        fillScreen = value.fillScreen
        verticalPaging = value.verticalPaging
        keepScreenOn = value.keepScreenOn
        edgeMargin = value.edgeMargin
        hiddenButtons = value.hiddenButtons
        barButtons = value.barButtons
        buttonColors = value.buttonColors
        bigButtons = value.bigButtons
        showClock = value.showClock
        showSessionTimer = value.showSessionTimer
        showSurahNumber = value.showSurahNumber
        showSurahAyahCount = value.showSurahAyahCount
        showSurahProgress = value.showSurahProgress
        showJuzProgressPercent = value.showJuzProgressPercent
        showJuzProgressPages = value.showJuzProgressPages
        clockColor = value.clockColor
        sessionTimerColor = value.sessionTimerColor
        showButtonPage = value.showButtonPage
        buttonPageColor = value.buttonPageColor
        showHeaderButtonOpacity = value.showHeaderButtonOpacity
        buttonPosFraction = value.buttonPosFraction
        showBottomJuzBar = value.showBottomJuzBar
        bottomJuzBarColor = value.bottomJuzBarColor
        bottomJuzBarThickness = value.bottomJuzBarThickness
        bottomJuzBarOpacity = value.bottomJuzBarOpacity
        showTopSurahBar = value.showTopSurahBar
        topSurahBarColor = value.topSurahBarColor
        topSurahBarThickness = value.topSurahBarThickness
        topSurahBarOpacity = value.topSurahBarOpacity
        showPageSideIndicator = value.showPageSideIndicator
        pageSideIndicatorColor = value.pageSideIndicatorColor
        pageSideIndicatorThickness = value.pageSideIndicatorThickness
        pageSideIndicatorLength = value.pageSideIndicatorLength
        pageSideIndicatorOpacity = value.pageSideIndicatorOpacity
        khatmaStartedAt = value.khatmaStartedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        bookmarks = snapshot.reading.bookmarks
        bookmarks2 = snapshot.reading.bookmarks2
        visitedPagesAll = value.visitedPages
        readPagesAll = value.readPages
        lastPage = value.lastPage.coerceIn(1, pageCount)
        sessions = statsRepo.allSessions()
        khatmas = statsRepo.allKhatmas()
        stats = statsRepo.summary(lastPage, pageCount, bookmarkPage())
        fullStats = statsRepo.fullStats(lastPage, pageCount, bookmarkPage())

        val now = System.currentTimeMillis()
        sessionStart = now
        sessionStartedAt = now
        sessionStartPage = lastPage
        visitedPages.clear()
        visitedPages.add(lastPage)
        visiblePage = lastPage
        pageEnteredAt = now
        restorePageRequest = lastPage
    }

    fun assetModel(pageNumber: Int): String = pageRepo.assetUri(pageNumber)

    fun markersForPage(pageNumber: Int): List<AyahMarker> = ayahData.byPage[pageNumber].orEmpty()

    fun selectAyah(ayah: AyahMarker?) { selectedAyah = ayah }

    fun clearSelection() { selectedAyah = null }

    fun loadExplanation(ayah: AyahMarker) {
        explanationJob?.cancel()
        explanationState = AyahExplanationUiState(
            verseKey = ayah.verseKey,
            loading = true,
        )
        explanationJob = viewModelScope.launch {
            try {
                val content = contentRepo.explanationFor(ayah.verseKey)
                explanationState = AyahExplanationUiState(
                    verseKey = ayah.verseKey,
                    tafsirHtml = content.tafsirHtml,
                    meanings = content.meanings,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                explanationState = AyahExplanationUiState(
                    verseKey = ayah.verseKey,
                    errorMessage = "تعذر فتح المحتوى. حاول مرة أخرى.",
                )
            }
        }
    }

    fun clearExplanation() {
        explanationJob?.cancel()
        explanationJob = null
        explanationState = AyahExplanationUiState()
    }

    fun isBookmarked(verseKey: String): Boolean = bookmarks.contains(verseKey)

    /** Single bookmark: a new one replaces the old; re-tapping the current one clears it. */
    fun toggleBookmark(ayah: AyahMarker) {
        val next = if (bookmarks.contains(ayah.verseKey)) emptySet() else setOf(ayah.verseKey)
        bookmarks = next
        viewModelScope.launch { store.setBookmarks(next) }
    }

    private fun bookmarkPage(): Int? = bookmarks.firstOrNull()?.let { ayahData.keyToPage[it] }

    fun bookmarkJumpPage(): Int? = bookmarkPage()

    fun isBookmarked2(verseKey: String): Boolean = bookmarks2.contains(verseKey)

    /** Second bookmark: same single-slot behaviour as the first, stored separately. */
    fun toggleBookmark2(ayah: AyahMarker) {
        val next = if (bookmarks2.contains(ayah.verseKey)) emptySet() else setOf(ayah.verseKey)
        bookmarks2 = next
        viewModelScope.launch { store.setBookmarks2(next) }
    }

    fun bookmark2JumpPage(): Int? = bookmarks2.firstOrNull()?.let { ayahData.keyToPage[it] }

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
        val visitedGrew = nextVisited.size != visitedPagesAll.size
        visitedPagesAll = nextVisited
        viewModelScope.launch {
            store.setReadPages(nextRead)
            if (visitedGrew) store.setVisitedPages(nextVisited)
        }
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
        viewModelScope.launch {
            store.setReadPages(nextRead)
            store.setVisitedPages(nextVisited)
        }
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
            resetKhatmaProgressInternal(now)
        }
    }

    /** Start a new khatma: clear page/juz progress and reset the cycle start. Sessions and the
     *  saved-khatma archive are NOT touched. */
    fun resetKhatmaProgress() {
        viewModelScope.launch { resetKhatmaProgressInternal(System.currentTimeMillis()) }
    }

    private suspend fun resetKhatmaProgressInternal(now: Long) {
        visitedPagesAll = emptySet()
        readPagesAll = emptySet()
        store.setVisitedPages(emptySet())
        store.setReadPages(emptySet())
        khatmaStartedAt = now
        store.setKhatmaStartedAt(now)
        // A new cycle starts from the beginning: move the reader to page 1 and re-anchor the dwell
        // there, so the page left behind can't be credited to the fresh khatma.
        saveLastPage(1)
        visiblePage = 1
        pageEnteredAt = now
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
     * See [AyahSearchIndex] for why the index carries both orthographies of the mushaf.
     */
    suspend fun search(query: String, limit: Int = AyahSearchIndex.DEFAULT_LIMIT): List<SearchResult> =
        withContext(Dispatchers.Default) {
            val index = ensureSearchIndex() ?: return@withContext emptyList()
            index.search(query, limit).map { it.marker.toSearchResult(it.expanded) }
        }
}

private fun AyahMarker.toSearchResult(expanded: Boolean) =
    SearchResult(verseKey, surahNameAr, ayahNumber, page, textUthmani, expanded)
