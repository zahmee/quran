package com.mushaf.reader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "reading")

/** Persists the last-read page (continue reading), display settings (dark theme,
 *  fill-screen), and the set of bookmarked verse keys, so the app reopens as it was left. */
class ReadingStore(private val context: Context) {

    companion object {
        /** Thickness (dp) of the progress bars / page-side marker before the user picks another. */
        const val DEFAULT_BAR_THICKNESS = 4
        /** Height (dp) of the page-side marker before the user picks another one. */
        const val DEFAULT_SIDE_INDICATOR_LENGTH = 40
        /** Opacity (percent) of the two progress bars — fully opaque unless the user dials it down. */
        const val DEFAULT_BAR_OPACITY = 100
        /** The page-side marker has always been drawn a little see-through; keep that as its default. */
        const val DEFAULT_SIDE_INDICATOR_OPACITY = 70
        /** Keep the restore-header button at its current visual strength until the user changes it. */
        const val DEFAULT_SHOW_HEADER_BUTTON_OPACITY = 100
    }

    private val keyLastPage = intPreferencesKey("last_page")
    private val keyBookmarks = stringSetPreferencesKey("bookmarks")
    private val keyBookmarks2 = stringSetPreferencesKey("bookmarks2")
    private val keyDarkTheme = booleanPreferencesKey("dark_theme")
    private val keyFillScreen = booleanPreferencesKey("fill_screen")
    private val keyVisitedPages = stringSetPreferencesKey("visited_pages")
    private val keyReadPages = stringSetPreferencesKey("read_pages")
    private val keyHiddenButtons = stringSetPreferencesKey("hidden_header_buttons")
    private val keyBigButtons = booleanPreferencesKey("big_buttons")
    private val keyShowClock = booleanPreferencesKey("show_clock")
    private val keyShowSessionTimer = booleanPreferencesKey("show_session_timer")
    private val keyShowSurahNumber = booleanPreferencesKey("show_surah_number")
    private val keyShowSurahAyahCount = booleanPreferencesKey("show_surah_ayah_count")
    private val keyShowSurahProgress = booleanPreferencesKey("show_surah_progress")
    private val keyShowJuzProgressPercent = booleanPreferencesKey("show_juz_progress_percent")
    private val keyShowJuzProgressPages = booleanPreferencesKey("show_juz_progress_pages")
    private val keyClockColor = stringPreferencesKey("clock_color")
    private val keySessionTimerColor = stringPreferencesKey("session_timer_color")
    // Full-screen restore-header chip + bottom-of-page juz bar.
    private val keyShowButtonPage = booleanPreferencesKey("show_button_page")
    private val keyButtonPageColor = stringPreferencesKey("button_page_color")
    private val keyShowHeaderButtonOpacity = intPreferencesKey("show_header_button_opacity")
    // Where the user dragged the chip, as a 0..1 fraction of its vertical travel; -1 = never moved.
    private val keyButtonPosFraction = floatPreferencesKey("button_pos_fraction")
    private val keyShowBottomJuzBar = booleanPreferencesKey("show_bottom_juz_bar")
    private val keyBottomJuzBarColor = stringPreferencesKey("bottom_juz_bar_color")
    // Bar thickness in dp and opacity in percent — shared scales for both progress bars.
    private val keyBottomJuzBarThickness = intPreferencesKey("bottom_juz_bar_thickness")
    private val keyBottomJuzBarOpacity = intPreferencesKey("bottom_juz_bar_opacity")
    // Top-of-page surah-progress bar (mirror of the bottom juz bar).
    private val keyShowTopSurahBar = booleanPreferencesKey("show_top_surah_bar")
    private val keyTopSurahBarColor = stringPreferencesKey("top_surah_bar_color")
    private val keyTopSurahBarThickness = intPreferencesKey("top_surah_bar_thickness")
    private val keyTopSurahBarOpacity = intPreferencesKey("top_surah_bar_opacity")
    // Full-screen edge marker for the current page's side (right vs left of the spread).
    private val keyShowPageSideIndicator = booleanPreferencesKey("show_page_side_indicator")
    private val keyPageSideIndicatorColor = stringPreferencesKey("page_side_indicator_color")
    // Its bar width (thickness) and height (length) in dp, plus its opacity in percent.
    private val keyPageSideIndicatorThickness = intPreferencesKey("page_side_indicator_thickness")
    private val keyPageSideIndicatorLength = intPreferencesKey("page_side_indicator_length")
    private val keyPageSideIndicatorOpacity = intPreferencesKey("page_side_indicator_opacity")
    // When the current (in-progress) khatma cycle began, epoch millis; 0 = not initialised yet.
    private val keyKhatmaStartedAt = longPreferencesKey("khatma_started_at")
    // Page turning direction: false = horizontal (default), true = vertical (up/down).
    private val keyVerticalPaging = booleanPreferencesKey("vertical_paging")
    // Local OAuth account selection. It is deliberately excluded from cloud backups.
    private val keyDriveAccount = stringPreferencesKey("drive_account")

    /** The display/position settings + per-page progress read together in one pass at startup.
     *  [visitedPages] = pages opened at all; [readPages] = pages dwelt on long enough to count
     *  as read (read ⊆ visited). Drives the khatma map. */
    data class Settings(
        val lastPage: Int,
        val darkTheme: Boolean,
        val fillScreen: Boolean,
        val visitedPages: Set<Int>,
        val readPages: Set<Int>,
        val hiddenButtons: Set<String>,
        val bigButtons: Boolean,
        val showClock: Boolean,
        val showSessionTimer: Boolean,
        val showSurahNumber: Boolean,
        val showSurahAyahCount: Boolean,
        val showSurahProgress: Boolean,
        val showJuzProgressPercent: Boolean,
        val showJuzProgressPages: Boolean,
        val clockColor: String,
        val sessionTimerColor: String,
        val showButtonPage: Boolean,
        val buttonPageColor: String,
        val showHeaderButtonOpacity: Int,
        val buttonPosFraction: Float,
        val showBottomJuzBar: Boolean,
        val bottomJuzBarColor: String,
        val bottomJuzBarThickness: Int,
        val bottomJuzBarOpacity: Int,
        val showTopSurahBar: Boolean,
        val topSurahBarColor: String,
        val topSurahBarThickness: Int,
        val topSurahBarOpacity: Int,
        val showPageSideIndicator: Boolean,
        val pageSideIndicatorColor: String,
        val pageSideIndicatorThickness: Int,
        val pageSideIndicatorLength: Int,
        val pageSideIndicatorOpacity: Int,
        val khatmaStartedAt: Long,
        val verticalPaging: Boolean,
    )

    /** Complete user-owned state exported to Google Drive. OAuth/account metadata is excluded. */
    data class BackupState(
        val settings: Settings,
        val bookmarks: Set<String>,
        val bookmarks2: Set<String>,
    )

    suspend fun settings(): Settings {
        val prefs = context.dataStore.data.first()
        return Settings(
            lastPage = prefs[keyLastPage] ?: 1,
            darkTheme = prefs[keyDarkTheme] ?: false,
            fillScreen = prefs[keyFillScreen] ?: false,
            visitedPages = prefs[keyVisitedPages].toIntSet(),
            readPages = prefs[keyReadPages].toIntSet(),
            hiddenButtons = prefs[keyHiddenButtons] ?: emptySet(),
            bigButtons = prefs[keyBigButtons] ?: false,
            showClock = prefs[keyShowClock] ?: false,
            showSessionTimer = prefs[keyShowSessionTimer] ?: false,
            showSurahNumber = prefs[keyShowSurahNumber] ?: false,
            showSurahAyahCount = prefs[keyShowSurahAyahCount] ?: false,
            showSurahProgress = prefs[keyShowSurahProgress] ?: false,
            showJuzProgressPercent = prefs[keyShowJuzProgressPercent] ?: false,
            showJuzProgressPages = prefs[keyShowJuzProgressPages] ?: false,
            clockColor = prefs[keyClockColor] ?: "muted",
            sessionTimerColor = prefs[keySessionTimerColor] ?: "muted",
            showButtonPage = prefs[keyShowButtonPage] ?: true,
            buttonPageColor = prefs[keyButtonPageColor] ?: "red",
            showHeaderButtonOpacity = (
                prefs[keyShowHeaderButtonOpacity] ?: DEFAULT_SHOW_HEADER_BUTTON_OPACITY
            ).coerceIn(25, 100),
            buttonPosFraction = prefs[keyButtonPosFraction] ?: -1f,
            showBottomJuzBar = prefs[keyShowBottomJuzBar] ?: false,
            bottomJuzBarColor = prefs[keyBottomJuzBarColor] ?: "blue",
            bottomJuzBarThickness = prefs[keyBottomJuzBarThickness] ?: DEFAULT_BAR_THICKNESS,
            bottomJuzBarOpacity = prefs[keyBottomJuzBarOpacity] ?: DEFAULT_BAR_OPACITY,
            showTopSurahBar = prefs[keyShowTopSurahBar] ?: false,
            topSurahBarColor = prefs[keyTopSurahBarColor] ?: "green",
            topSurahBarThickness = prefs[keyTopSurahBarThickness] ?: DEFAULT_BAR_THICKNESS,
            topSurahBarOpacity = prefs[keyTopSurahBarOpacity] ?: DEFAULT_BAR_OPACITY,
            showPageSideIndicator = prefs[keyShowPageSideIndicator] ?: true,
            pageSideIndicatorColor = prefs[keyPageSideIndicatorColor] ?: "green",
            pageSideIndicatorThickness = prefs[keyPageSideIndicatorThickness] ?: DEFAULT_BAR_THICKNESS,
            pageSideIndicatorLength = prefs[keyPageSideIndicatorLength] ?: DEFAULT_SIDE_INDICATOR_LENGTH,
            pageSideIndicatorOpacity = prefs[keyPageSideIndicatorOpacity] ?: DEFAULT_SIDE_INDICATOR_OPACITY,
            khatmaStartedAt = prefs[keyKhatmaStartedAt] ?: 0L,
            verticalPaging = prefs[keyVerticalPaging] ?: false,
        )
    }

    suspend fun backupState(): BackupState = BackupState(
        settings = settings(),
        bookmarks = bookmarks(),
        bookmarks2 = bookmarks2(),
    )

    /** Replace the backed-up preferences in one atomic DataStore edit. */
    suspend fun restoreBackupState(state: BackupState) {
        val value = state.settings
        context.dataStore.edit { prefs ->
            prefs[keyLastPage] = value.lastPage
            prefs[keyBookmarks] = state.bookmarks
            prefs[keyBookmarks2] = state.bookmarks2
            prefs[keyDarkTheme] = value.darkTheme
            prefs[keyFillScreen] = value.fillScreen
            prefs[keyVisitedPages] = value.visitedPages.mapTo(HashSet()) { it.toString() }
            prefs[keyReadPages] = value.readPages.mapTo(HashSet()) { it.toString() }
            prefs[keyHiddenButtons] = value.hiddenButtons
            prefs[keyBigButtons] = value.bigButtons
            prefs[keyShowClock] = value.showClock
            prefs[keyShowSessionTimer] = value.showSessionTimer
            prefs[keyShowSurahNumber] = value.showSurahNumber
            prefs[keyShowSurahAyahCount] = value.showSurahAyahCount
            prefs[keyShowSurahProgress] = value.showSurahProgress
            prefs[keyShowJuzProgressPercent] = value.showJuzProgressPercent
            prefs[keyShowJuzProgressPages] = value.showJuzProgressPages
            prefs[keyClockColor] = value.clockColor
            prefs[keySessionTimerColor] = value.sessionTimerColor
            prefs[keyShowButtonPage] = value.showButtonPage
            prefs[keyButtonPageColor] = value.buttonPageColor
            prefs[keyShowHeaderButtonOpacity] = value.showHeaderButtonOpacity
            prefs[keyButtonPosFraction] = value.buttonPosFraction
            prefs[keyShowBottomJuzBar] = value.showBottomJuzBar
            prefs[keyBottomJuzBarColor] = value.bottomJuzBarColor
            prefs[keyBottomJuzBarThickness] = value.bottomJuzBarThickness
            prefs[keyBottomJuzBarOpacity] = value.bottomJuzBarOpacity
            prefs[keyShowTopSurahBar] = value.showTopSurahBar
            prefs[keyTopSurahBarColor] = value.topSurahBarColor
            prefs[keyTopSurahBarThickness] = value.topSurahBarThickness
            prefs[keyTopSurahBarOpacity] = value.topSurahBarOpacity
            prefs[keyShowPageSideIndicator] = value.showPageSideIndicator
            prefs[keyPageSideIndicatorColor] = value.pageSideIndicatorColor
            prefs[keyPageSideIndicatorThickness] = value.pageSideIndicatorThickness
            prefs[keyPageSideIndicatorLength] = value.pageSideIndicatorLength
            prefs[keyPageSideIndicatorOpacity] = value.pageSideIndicatorOpacity
            prefs[keyKhatmaStartedAt] = value.khatmaStartedAt
            prefs[keyVerticalPaging] = value.verticalPaging
        }
    }

    suspend fun driveAccount(): String? =
        context.dataStore.data.first()[keyDriveAccount]?.takeIf { it.isNotBlank() }

    suspend fun setDriveAccount(value: String?) {
        context.dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(keyDriveAccount)
            else prefs[keyDriveAccount] = value
        }
    }

    private fun Set<String>?.toIntSet(): Set<Int> =
        this?.mapNotNullTo(HashSet()) { it.toIntOrNull() } ?: emptySet()

    suspend fun setVisitedPages(pages: Set<Int>) {
        context.dataStore.edit { it[keyVisitedPages] = pages.mapTo(HashSet()) { p -> p.toString() } }
    }

    suspend fun setReadPages(pages: Set<Int>) {
        context.dataStore.edit { it[keyReadPages] = pages.mapTo(HashSet()) { p -> p.toString() } }
    }

    suspend fun setLastPage(page: Int) {
        context.dataStore.edit { it[keyLastPage] = page }
    }

    suspend fun setDarkTheme(value: Boolean) {
        context.dataStore.edit { it[keyDarkTheme] = value }
    }

    suspend fun setFillScreen(value: Boolean) {
        context.dataStore.edit { it[keyFillScreen] = value }
    }

    suspend fun setHiddenButtons(values: Set<String>) {
        context.dataStore.edit { it[keyHiddenButtons] = values }
    }

    suspend fun setBigButtons(value: Boolean) {
        context.dataStore.edit { it[keyBigButtons] = value }
    }

    suspend fun setShowClock(value: Boolean) {
        context.dataStore.edit { it[keyShowClock] = value }
    }

    suspend fun setShowSessionTimer(value: Boolean) {
        context.dataStore.edit { it[keyShowSessionTimer] = value }
    }

    suspend fun setShowSurahNumber(value: Boolean) {
        context.dataStore.edit { it[keyShowSurahNumber] = value }
    }

    suspend fun setShowSurahAyahCount(value: Boolean) {
        context.dataStore.edit { it[keyShowSurahAyahCount] = value }
    }

    suspend fun setShowSurahProgress(value: Boolean) {
        context.dataStore.edit { it[keyShowSurahProgress] = value }
    }

    suspend fun setShowJuzProgressPercent(value: Boolean) {
        context.dataStore.edit { it[keyShowJuzProgressPercent] = value }
    }

    suspend fun setShowJuzProgressPages(value: Boolean) {
        context.dataStore.edit { it[keyShowJuzProgressPages] = value }
    }

    suspend fun setClockColor(value: String) {
        context.dataStore.edit { it[keyClockColor] = value }
    }

    suspend fun setSessionTimerColor(value: String) {
        context.dataStore.edit { it[keySessionTimerColor] = value }
    }

    suspend fun setShowButtonPage(value: Boolean) {
        context.dataStore.edit { it[keyShowButtonPage] = value }
    }

    suspend fun setButtonPageColor(value: String) {
        context.dataStore.edit { it[keyButtonPageColor] = value }
    }

    suspend fun setShowHeaderButtonOpacity(value: Int) {
        context.dataStore.edit { it[keyShowHeaderButtonOpacity] = value.coerceIn(25, 100) }
    }

    suspend fun setButtonPosFraction(value: Float) {
        context.dataStore.edit { it[keyButtonPosFraction] = value }
    }

    suspend fun setShowBottomJuzBar(value: Boolean) {
        context.dataStore.edit { it[keyShowBottomJuzBar] = value }
    }

    suspend fun setBottomJuzBarColor(value: String) {
        context.dataStore.edit { it[keyBottomJuzBarColor] = value }
    }

    suspend fun setBottomJuzBarThickness(value: Int) {
        context.dataStore.edit { it[keyBottomJuzBarThickness] = value }
    }

    suspend fun setBottomJuzBarOpacity(value: Int) {
        context.dataStore.edit { it[keyBottomJuzBarOpacity] = value }
    }

    suspend fun setShowTopSurahBar(value: Boolean) {
        context.dataStore.edit { it[keyShowTopSurahBar] = value }
    }

    suspend fun setTopSurahBarColor(value: String) {
        context.dataStore.edit { it[keyTopSurahBarColor] = value }
    }

    suspend fun setTopSurahBarThickness(value: Int) {
        context.dataStore.edit { it[keyTopSurahBarThickness] = value }
    }

    suspend fun setTopSurahBarOpacity(value: Int) {
        context.dataStore.edit { it[keyTopSurahBarOpacity] = value }
    }

    suspend fun setShowPageSideIndicator(value: Boolean) {
        context.dataStore.edit { it[keyShowPageSideIndicator] = value }
    }

    suspend fun setPageSideIndicatorColor(value: String) {
        context.dataStore.edit { it[keyPageSideIndicatorColor] = value }
    }

    suspend fun setPageSideIndicatorThickness(value: Int) {
        context.dataStore.edit { it[keyPageSideIndicatorThickness] = value }
    }

    suspend fun setPageSideIndicatorLength(value: Int) {
        context.dataStore.edit { it[keyPageSideIndicatorLength] = value }
    }

    suspend fun setPageSideIndicatorOpacity(value: Int) {
        context.dataStore.edit { it[keyPageSideIndicatorOpacity] = value }
    }

    suspend fun setKhatmaStartedAt(value: Long) {
        context.dataStore.edit { it[keyKhatmaStartedAt] = value }
    }

    suspend fun setVerticalPaging(value: Boolean) {
        context.dataStore.edit { it[keyVerticalPaging] = value }
    }

    suspend fun bookmarks(): Set<String> = context.dataStore.data.first()[keyBookmarks] ?: emptySet()

    suspend fun setBookmarks(values: Set<String>) {
        context.dataStore.edit { it[keyBookmarks] = values }
    }

    suspend fun bookmarks2(): Set<String> = context.dataStore.data.first()[keyBookmarks2] ?: emptySet()

    suspend fun setBookmarks2(values: Set<String>) {
        context.dataStore.edit { it[keyBookmarks2] = values }
    }
}
