package com.mushaf.reader.reader

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.AyahRepository
import com.mushaf.reader.data.AyahSearchIndex
import com.mushaf.reader.data.ExplanationTextScale
import com.mushaf.reader.data.JuzLayout
import com.mushaf.reader.data.PageRepository
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.SearchTier
import com.mushaf.reader.data.SurahAyahCounts
import com.mushaf.reader.data.backup.BackupException
import com.mushaf.reader.data.backup.BackupFileInfo
import com.mushaf.reader.data.backup.BackupSnapshot
import com.mushaf.reader.data.backup.BackupStage
import com.mushaf.reader.data.backup.BackupUiState
import com.mushaf.reader.data.backup.CloudBackupInfo
import com.mushaf.reader.data.backup.CloudBackupRepository
import com.mushaf.reader.data.backup.CloudBackupWorker
import com.mushaf.reader.data.backup.CloudStage
import com.mushaf.reader.data.backup.CloudUiState
import com.mushaf.reader.data.backup.FileBackupRepository
import com.mushaf.reader.data.content.GharibMeaning
import com.mushaf.reader.data.RootSearch
import com.mushaf.reader.data.content.CenterExtrasRepository
import com.mushaf.reader.data.content.RootIndexRow
import com.mushaf.reader.data.content.QuranContentRepository
import com.mushaf.reader.data.content.SurahCard
import com.mushaf.reader.data.content.TajweedNote
import com.mushaf.reader.data.content.TafsirPassage
import com.mushaf.reader.data.content.TafsirPassageRow
import com.mushaf.reader.data.packs.InstalledPack
import com.mushaf.reader.data.packs.PackContent
import com.mushaf.reader.data.packs.PackContentRepository
import com.mushaf.reader.data.packs.PackDownloads
import com.mushaf.reader.data.packs.PackStore
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

/** The floating recitation handle starts a little below the middle of the screen edge, in thumb
 *  reach on a phone held in the right hand. */
private const val DEFAULT_HANDLE_POS = 0.62f

/** One surah in the navigation index: number, Arabic name, and the page it begins on. */
data class SurahEntry(val number: Int, val nameAr: String, val firstPage: Int, val ayahCount: Int)

/** One juz in the navigation index: number and the page it begins on. */
data class JuzEntry(val number: Int, val firstPage: Int, val ayahCount: Int)

/** A single ayah hit. */
data class SearchResult(
    val verseKey: String,
    val surahNameAr: String,
    val ayahNumber: Int,
    val page: Int,
    val text: String,
    /** How the ayah matched; the search screen groups and labels hits by it. See [AyahSearchIndex]. */
    val tier: SearchTier = SearchTier.WORD,
    /** Character ranges in [text] of the words that matched, for the screen to mark. */
    val highlights: List<IntRange> = emptyList(),
)

/** A search result's ayah being flashed on its page; [token] tells one opening from the next. */
data class SearchFlash(val verseKey: String, val page: Int, val token: Long)

/** A surah whose name matched the query — shown once, not as every ayah in it. */
data class SurahSearchResult(val number: Int, val nameAr: String, val firstPage: Int)

data class SearchResults(
    val surahs: List<SurahSearchResult> = emptyList(),
    val ayahs: List<SearchResult> = emptyList(),
    /** The roots the typed word belongs to, offered above the other results. See [RootSearch]. */
    val roots: List<RootSearch.Root> = emptyList(),
) {
    val isEmpty: Boolean get() = surahs.isEmpty() && ayahs.isEmpty() && roots.isEmpty()
}

/** Every ayah holding a word from [root] — or only from its form [form] — with those words marked. */
data class RootResults(
    val root: RootSearch.Root,
    val form: Int?,
    val ayahs: List<SearchResult>,
)

data class AyahExplanationUiState(
    val verseKey: String? = null,
    val loading: Boolean = false,
    val tafsirHtml: String? = null,
    val meanings: List<GharibMeaning> = emptyList(),
    val saadi: TafsirPassage? = null,
    val mukhtasar: String? = null,
    /** The occasion of the ayah's revelation, for the ayahs the source records one for. */
    val nozool: TafsirPassageRow? = null,
    val errorMessage: String? = null,
)

/** What the sheet shows for a downloaded pack: that pack's text for one ayah. */
data class PackContentUiState(
    val verseKey: String? = null,
    val packId: String? = null,
    val loading: Boolean = false,
    val content: PackContent? = null,
)

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val pageRepo = PageRepository(app)
    private val ayahRepo = AyahRepository(app)
    private val contentRepo = QuranContentRepository(app)
    private val centerExtras = CenterExtrasRepository(app)
    private val store = ReadingStore(app)
    private val statsRepo = StatsRepository(app)
    private val backupRepo = FileBackupRepository(app)
    private val cloudRepo = CloudBackupRepository(app)

    private val recitationRepo = RecitationRepository(app)

    /** Recitation downloads and the audio kept from listening. */
    val recitationDownloads = RecitationDownloads(app, viewModelScope, recitationRepo)

    /** The downloadable tafsir packs: the library's catalog, downloads and installed packs. */
    val packDownloads = PackDownloads(app, viewModelScope)
    private val packRepo = PackContentRepository(app)

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

    /** The source the explanation sheet last showed, which it opens on next ([ExplanationSources]). */
    var explanationSource by mutableStateOf(ExplanationSources.SAADI)
        private set

    fun updateExplanationSource(source: String) {
        if (source == explanationSource) return
        explanationSource = source
        viewModelScope.launch { store.setExplanationSource(source) }
    }

    /** The sources pinned as the sheet's tabs, in order, including pins on packs deleted since. */
    var pinnedSources by mutableStateOf(ExplanationSources.DEFAULT_PINNED)
        private set

    /**
     * Pins or unpins [source]; false when every place is taken. Pins on deleted packs are dropped
     * here, so a place they held is free again — until then they come back with their pack.
     */
    fun togglePinnedSource(source: String): Boolean {
        val shown = ExplanationSources.visiblePinned(pinnedSources, installedPackIds)
        val next = ExplanationSources.togglePin(shown, source) ?: return false
        pinnedSources = next
        viewModelScope.launch { store.setPinnedSources(next) }
        return true
    }

    /** The installed packs in the order the sources menu lists them. */
    val installedPacks: List<InstalledPack>
        get() = PackStore.installed.values.sortedBy { PACK_ORDER.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }

    private val installedPackIds: Set<String> get() = PackStore.installed.keys

    /** The source the sheet opens on: the last one read, unless its pack was deleted since. */
    fun openingSource(): String = ExplanationSources.opening(explanationSource, installedPackIds)

    /** Text size of the tafsir / word-meanings sheet, percent of the reading size. */
    var explanationTextScale by mutableIntStateOf(ExplanationTextScale.DEFAULT)
        private set

    /** One size up ([direction] > 0) or down; remembered for the next time the sheet opens. */
    fun stepExplanationTextScale(direction: Int) {
        val next = ExplanationTextScale.step(explanationTextScale, direction)
        if (next == explanationTextScale) return
        explanationTextScale = next
        viewModelScope.launch { store.setExplanationTextScale(next) }
    }

    init {
        viewModelScope.launch {
            recitationFollowPage = store.recitationFollowPage()
            store.recitationHandlePos()?.let { recitationHandlePos = it }
            recitationHandleOpacity = store.recitationHandleOpacity()
            explanationSource = store.explanationSource()
            pinnedSources = store.pinnedSources()
            explanationTextScale = store.explanationTextScale()
        }
        // Only lists the pack files already on the device; nothing here touches the network.
        packDownloads.start()
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

    var showReadingPosition by mutableStateOf(initialSettings.showReadingPosition)
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

    /** Start time (ms) of the current reading session; 0 when none has started.
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

    var cloudUiState by mutableStateOf(CloudUiState(available = cloudRepo.isAvailable))
        private set

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
        viewModelScope.launch { bookmarks = withContext(Dispatchers.IO) { store.bookmarks() } }
        viewModelScope.launch { bookmarks2 = withContext(Dispatchers.IO) { store.bookmarks2() } }
        viewModelScope.launch { khatmas = withContext(Dispatchers.IO) { statsRepo.allKhatmas() } }
        if (cloudRepo.isAvailable) viewModelScope.launch { loadCloudAccount() }
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

    fun updateShowReadingPosition(value: Boolean) {
        if (value == showReadingPosition) return
        showReadingPosition = value
        viewModelScope.launch { store.setShowReadingPosition(value) }
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

    fun importBackup(source: Uri, takeDisplaySettings: Boolean) {
        viewModelScope.launch {
            startBackupOperation(BackupStage.Importing)
            try {
                // Finish and await old writes before folding the backup into the database and preferences.
                checkpointCurrentSessionForBackup()
                val restored = backupRepo.importFrom(source, takeDisplaySettings)
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

    // ── Cloud backup ────────────────────────────────────────────────────────────

    private suspend fun loadCloudAccount() {
        val username = cloudRepo.reconcileAccount()
        val prefs = store.cloudPrefs()
        cloudUiState = cloudUiState.copy(
            username = username,
            autoBackup = username != null && prefs.autoBackup,
            lastUploadAt = prefs.lastUploadAt,
        )
    }

    /** Called when the backup screen opens, so the list reflects the other device's uploads. */
    fun refreshCloudBackups() {
        if (cloudUiState.username == null || cloudUiState.busy) return
        runCloud(CloudStage.Loading) {
            cloudUiState = cloudUiState.copy(backups = cloudRepo.list(), listLoaded = true)
            null
        }
    }

    fun cloudSignUp(name: String, password: String) = runCloud(CloudStage.SigningUp) {
        val username = cloudRepo.signUp(name, password)
        CloudBackupWorker.schedule(getApplication(), enabled = true)
        cloudUiState = cloudUiState.copy(username = username, autoBackup = true)
        // The first backup right away: an account with nothing in it protects nothing.
        checkpointCurrentSessionForBackup()
        cloudRepo.upload(auto = false)
        loadCloudAccount()
        cloudUiState = cloudUiState.copy(backups = cloudRepo.list(), listLoaded = true)
        "أُنشئ حسابك «$username» وحُفظت أول نسخة. احفظ كلمة المرور جيداً."
    }

    fun cloudSignIn(name: String, password: String) = runCloud(CloudStage.SigningIn) {
        val username = cloudRepo.signIn(name, password)
        CloudBackupWorker.schedule(getApplication(), enabled = true)
        loadCloudAccount()
        val backups = cloudRepo.list()
        cloudUiState = cloudUiState.copy(backups = backups, listLoaded = true)
        if (backups.isEmpty()) "مرحباً $username. لا توجد نسخ في حسابك بعد."
        else "مرحباً $username. اختر نسخة لاستعادتها على هذا الجهاز."
    }

    fun cloudSignOut() {
        if (cloudUiState.busy) return
        viewModelScope.launch {
            CloudBackupWorker.schedule(getApplication(), enabled = false)
            cloudRepo.signOut()
            cloudUiState = CloudUiState(available = cloudRepo.isAvailable)
        }
    }

    fun cloudBackupNow() = runCloud(CloudStage.Uploading) {
        checkpointCurrentSessionForBackup()
        cloudRepo.upload(auto = false)
        loadCloudAccount()
        cloudUiState = cloudUiState.copy(backups = cloudRepo.list(), listLoaded = true)
        "حُفظت نسخة جديدة في حسابك."
    }

    fun cloudRestore(backup: CloudBackupInfo, takeDisplaySettings: Boolean) = runCloud(CloudStage.Restoring) {
        // Finish and await old writes before folding the backup into the database and preferences.
        checkpointCurrentSessionForBackup()
        applyRestoredSnapshot(cloudRepo.restore(backup, takeDisplaySettings))
        "اكتملت الاستعادة من نسخة ${formatBackupDate(backup.createdAt)} (${backup.deviceName})."
    }

    fun cloudDeleteBackup(backup: CloudBackupInfo) = runCloud(CloudStage.DeletingBackup) {
        cloudRepo.delete(backup)
        cloudUiState = cloudUiState.copy(backups = cloudRepo.list(), listLoaded = true)
        "حُذفت النسخة من حسابك."
    }

    fun setCloudAutoBackup(enabled: Boolean) {
        if (cloudUiState.username == null || enabled == cloudUiState.autoBackup) return
        cloudUiState = cloudUiState.copy(autoBackup = enabled)
        viewModelScope.launch {
            store.setCloudAutoBackup(enabled)
            CloudBackupWorker.schedule(getApplication(), enabled)
        }
    }

    fun cloudDeleteAccount(password: String) = runCloud(CloudStage.Deleting) {
        cloudRepo.deleteAccount(password)
        CloudBackupWorker.schedule(getApplication(), enabled = false)
        cloudUiState = CloudUiState(available = cloudRepo.isAvailable)
        "حُذف حسابك وجميع نسخه من الخادم. بياناتك على هذا الجهاز باقية كما هي."
    }

    fun clearCloudFeedback() {
        cloudUiState = cloudUiState.copy(message = null, error = null)
    }

    /** Runs one cloud operation behind the busy state; [block] returns the success message. */
    private fun runCloud(stage: CloudStage, block: suspend () -> String?) {
        if (cloudUiState.busy || backupUiState.busy) return
        cloudUiState = cloudUiState.copy(busy = true, stage = stage, message = null, error = null)
        viewModelScope.launch {
            try {
                val message = block()
                cloudUiState = cloudUiState.copy(busy = false, stage = null, message = message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                cloudUiState = cloudUiState.copy(
                    busy = false,
                    stage = null,
                    error = (error as? BackupException)?.message ?: "تعذر إكمال العملية. أعد المحاولة بعد قليل.",
                )
            }
        }
    }

    /** A file picker can stop the activity first, so await that commit as well as this one. */
    private suspend fun checkpointCurrentSessionForBackup() {
        commitSession()
        pendingSessionWrite?.join()
        pendingProgressWrite?.join()
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
        showReadingPosition = value.showReadingPosition
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

        readingSession.reset(lastPage)
        sessionStartedAt = 0L
        restorePageRequest = lastPage
    }

    fun assetModel(pageNumber: Int): String = pageRepo.assetUri(pageNumber)

    fun markersForPage(pageNumber: Int): List<AyahMarker> = ayahData.byPage[pageNumber].orEmpty()

    fun hizbMarkersForPage(pageNumber: Int) = ayahData.hizbMarkersByPage[pageNumber].orEmpty()

    fun selectAyah(ayah: AyahMarker?) { selectedAyah = ayah }

    fun clearSelection() { selectedAyah = null }

    /** The ayah a search result jumped to, flashed on its page until it fades or the page is touched. */
    var searchFlash by mutableStateOf<SearchFlash?>(null)
        private set

    fun flashSearchResult(verseKey: String, page: Int) {
        // A fresh token restarts the flash even when the same result is opened twice in a row.
        searchFlash = SearchFlash(verseKey, page, token = System.nanoTime())
    }

    fun clearSearchFlash() { searchFlash = null }

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
                    saadi = content.saadi,
                    mukhtasar = content.mukhtasar,
                    nozool = content.nozool,
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

    /** The fawaed the Mukhtasar draws from [page]; empty when the page has none or cannot be read. */
    suspend fun pageFawaed(page: Int): List<String> =
        runCatching { centerExtras.fawaedForPage(page) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrDefault(emptyList())

    /** The tajweed note on an ayah; null when it has none or the note cannot be read. */
    suspend fun tajweed(surah: Int, ayah: Int): TajweedNote? =
        runCatching { centerExtras.tajweed(surah, ayah) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()

    /** [surah]'s introduction, or null when it cannot be read. */
    suspend fun surahCard(surah: Int): SurahCard? =
        runCatching { centerExtras.surahCard(surah) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()

    fun clearExplanation() {
        explanationJob?.cancel()
        explanationJob = null
        explanationState = AyahExplanationUiState()
        packJob?.cancel()
        packJob = null
        packState = PackContentUiState()
    }

    var packState by mutableStateOf(PackContentUiState())
        private set
    private var packJob: Job? = null

    /** Loads [packId]'s text for [verseKey] into [packState], unless it is already there. */
    fun loadPackContent(verseKey: String, packId: String) {
        val current = packState
        if (current.verseKey == verseKey && current.packId == packId && (current.loading || current.content != null)) return
        val pack = PackStore.installed[packId]
        packJob?.cancel()
        if (pack == null) {
            packState = PackContentUiState(verseKey, packId)
            return
        }
        packState = PackContentUiState(verseKey, packId, loading = true)
        packJob = viewModelScope.launch {
            val content = try {
                packRepo.contentFor(packId, pack.kind, verseKey)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            packState = PackContentUiState(verseKey, packId, content = content)
        }
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
        viewModelScope.launch { store.setLastPage(page) }
    }

    /** Called when [page] becomes the visible page: records the dwell time on the page just left
     *  (→ "read" if long enough), marks the new page visited, and persists the reading position. */
    fun onPageVisible(page: Int) {
        readingSession.changePage(page)
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
val PACK_ORDER = listOf("ibn_kathir", "tabari", "baghawi", "irab")
