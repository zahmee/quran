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

/** Persists the last-read page (continue reading), display settings (reading theme,
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
        /** The screen stayed awake unconditionally before this became a setting, so keep that
         *  as the default: an upgrade must not start locking the screen mid-read. */
        const val DEFAULT_KEEP_SCREEN_ON = true
        /** How far the corner controls and the page pull back from a curved screen edge. A rounded
         *  display corner is reported by neither the status-bar nor the display-cutout inset, so
         *  nothing in the layout knows about it; this is the reader's own answer to that. It
         *  defaults to "auto", which pulls back only as much as the device reports it needs, so a
         *  curved screen does not clip a corner control on first run; "none" is one tap away for
         *  a flat screen that wants every pixel. */
        const val EDGE_MARGIN_NONE = "none"
        const val EDGE_MARGIN_AUTO = "auto"
        const val EDGE_MARGIN_SMALL = "small"
        const val EDGE_MARGIN_MEDIUM = "medium"
        const val EDGE_MARGIN_LARGE = "large"
        const val DEFAULT_EDGE_MARGIN = EDGE_MARGIN_AUTO

        /** Palette id used before the user picks one, and the one the old dark_theme flag maps to.
         *  Kept as plain strings so this layer stays independent of the ui.theme palette table. */
        const val DEFAULT_THEME_ID = "light"
        const val DARK_THEME_ID = "dark"
        /** Header actions that sit on the bar itself until the user moves them into the More menu.
         *  "fill" and "hide" were bar buttons before the placement setting existed; "bookmark" is
         *  here because returning to the saved marker is the one action a reader reaches for from
         *  the page itself, and a menu hop for it reads as friction. */
        val DEFAULT_BAR_BUTTONS = setOf("bookmark", "fill", "hide")

        /** Header actions hidden until the reader turns them on. The second (purple) marker is for
         *  readers who keep two places at once; offering both out of the box only crowds the bar
         *  for everyone else, so it starts hidden and the first marker stands alone. */
        val DEFAULT_HIDDEN_BUTTONS = setOf("bookmark2")

        /** The page fills the screen out of the box: the mushaf images carry almost no margin of
         *  their own, so a fitted page leaves bands of paper around text that could be read bigger.
         *  A reader who prefers the whole page framed turns it off once. */
        const val DEFAULT_FILL_SCREEN = true

        /** Header buttons are drawn at the larger size by default — they are touched mid-read, at
         *  arm's length, often one-handed. */
        const val DEFAULT_BIG_BUTTONS = true

        /** The two progress bars are on by default: they are the only thing on the page that says
         *  how far this surah and this juz have gone, and they cost a hairline each. */
        const val DEFAULT_SHOW_TOP_SURAH_BAR = true
        const val DEFAULT_SHOW_BOTTOM_JUZ_BAR = true

        /** The juz bar is red against the surah bar's green so the two never read as one line. */
        const val DEFAULT_BOTTOM_JUZ_BAR_COLOR = "red"

        /** The session timer rides in the header by default; reading time is the statistic the
         *  khatma screens are built on, and seeing it accrue is what makes it worth keeping. */
        const val DEFAULT_SHOW_SESSION_TIMER = true

        // ── Changing a default without moving anyone's reader ───────────────────────────────
        //
        // DataStore holds only the keys that were written. A reader who never opened a setting
        // has no stored value at all and simply gets whatever the default is today — so changing
        // a default silently rearranges the reader of every such user on the next update. That is
        // the opposite of what a default is for.
        //
        // [migrateDefaults] closes that gap once per install: an install that already existed has
        // the pre-v1 values written out explicitly, so nothing shifts under it, while a fresh one
        // keeps the new defaults. After that the stored version says it has been handled, and the
        // same mechanism carries the next change — bump [DEFAULTS_VERSION] and add a branch.

        /** Name of the stamp key; shared with [isExistingInstall], which must ignore it. */
        const val DEFAULTS_VERSION_KEY = "defaults_version"

        /** Bumped whenever a default above changes in a way an existing reader would see.
         *  v1 (0.7.0): fill-screen, big buttons, both progress bars, the juz bar's red, the
         *  session timer, the bookmark on the bar, the hidden second marker, the auto edge
         *  margin. */
        const val DEFAULTS_VERSION = 1

        /**
         * True when these preferences belong to an install that existed before the stamp.
         *
         * The discriminator is "has this install ever written anything", read at the first
         * settings load, before the reader itself writes its first page. The stamp is excluded
         * because it is written by the migration, not by the reader — counting it would make
         * every later migration believe it was looking at a fresh install.
         */
        fun isExistingInstall(storedKeys: Set<String>): Boolean =
            storedKeys.any { it != DEFAULTS_VERSION_KEY }

        /** Per-button colors ride in a preference string set as "id=color" — DataStore has no map
         *  type, and the backup file reuses the same encoding. */
        fun decodeButtonColors(entries: Set<String>?): Map<String, String> =
            entries.orEmpty().mapNotNull { entry ->
                val at = entry.indexOf('=')
                if (at <= 0 || at == entry.lastIndex) null
                else entry.substring(0, at) to entry.substring(at + 1)
            }.toMap()

        fun encodeButtonColors(colors: Map<String, String>): Set<String> =
            colors.entries.mapTo(HashSet()) { "${it.key}=${it.value}" }
    }

    private val keyLastPage = intPreferencesKey("last_page")
    private val keyBookmarks = stringSetPreferencesKey("bookmarks")
    private val keyBookmarks2 = stringSetPreferencesKey("bookmarks2")
    private val keyThemeId = stringPreferencesKey("theme_id")
    // Superseded by keyThemeId; still read once so installs from before the palettes keep their look.
    private val keyDarkTheme = booleanPreferencesKey("dark_theme")
    private val keyFillScreen = booleanPreferencesKey("fill_screen")
    private val keyVisitedPages = stringSetPreferencesKey("visited_pages")
    private val keyReadPages = stringSetPreferencesKey("read_pages")
    private val keyHiddenButtons = stringSetPreferencesKey("hidden_header_buttons")
    // Which header actions sit on the bar rather than in the More menu, and the color each takes there.
    private val keyBarButtons = stringSetPreferencesKey("bar_buttons")
    private val keyButtonColors = stringSetPreferencesKey("button_colors")
    private val keyBigButtons = booleanPreferencesKey("big_buttons")
    private val keyShowClock = booleanPreferencesKey("show_clock")
    private val keyShowSessionTimer = booleanPreferencesKey("show_session_timer")
    private val keyShowSurahNumber = booleanPreferencesKey("show_surah_number")
    private val keyShowSurahAyahCount = booleanPreferencesKey("show_surah_ayah_count")
    private val keyShowSurahProgress = booleanPreferencesKey("show_surah_progress")
    private val keyShowJuzProgressPercent = booleanPreferencesKey("show_juz_progress_percent")
    private val keyShowJuzProgressPages = booleanPreferencesKey("show_juz_progress_pages")
    private val keyShowReadingPosition = booleanPreferencesKey("show_reading_position")
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
    // Whether the screen is held awake while reading. On by default — that was the old behaviour.
    private val keyKeepScreenOn = booleanPreferencesKey("keep_screen_on")
    // Local OAuth account selection. It is deliberately excluded from cloud backups.
    private val keyLastBackupAt = longPreferencesKey("last_backup_at")
    private val keyLastBackupName = stringPreferencesKey("last_backup_name")
    private val keyLastBackupSize = longPreferencesKey("last_backup_size")

    private val keyEdgeMargin = stringPreferencesKey("edge_margin")

    /** Which generation of defaults this install has already been reconciled with. */
    private val keyDefaultsVersion = intPreferencesKey(DEFAULTS_VERSION_KEY)

    // Update-nudge bookkeeping. Device-local on purpose: it stays out of [Settings] so restoring a
    // backup on another phone can't silence (or re-trigger) that phone's update prompt.
    private val keyUpdateCheckedAt = longPreferencesKey("update_checked_at")
    private val keyUpdateDismissedVersion = intPreferencesKey("update_dismissed_version")
    private val keyUpdateDismissedAt = longPreferencesKey("update_dismissed_at")

    /** The display/position settings + per-page progress read together in one pass at startup.
     *  [visitedPages] = pages opened at all; [readPages] = pages dwelt on long enough to count
     *  as read (read ⊆ visited). Drives the khatma map. */
    data class Settings(
        val lastPage: Int,
        val themeId: String,
        val fillScreen: Boolean,
        val visitedPages: Set<Int>,
        val readPages: Set<Int>,
        val hiddenButtons: Set<String>,
        val barButtons: Set<String>,
        val buttonColors: Map<String, String>,
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
        val keepScreenOn: Boolean,
        val edgeMargin: String,
        val showReadingPosition: Boolean = false,
    )

    /** When the app last asked Play about an update, and which version the reader waved away.
     *  [dismissedVersion] is 0 when nothing was ever dismissed. */
    data class UpdateNudge(
        val checkedAt: Long,
        val dismissedVersion: Int,
        val dismissedAt: Long,
    )

    /** Complete user-owned state written to the backup file. Device-local metadata is excluded. */
    data class BackupState(
        val settings: Settings,
        val bookmarks: Set<String>,
        val bookmarks2: Set<String>,
    )

    /**
     * Reconciles this install with [DEFAULTS_VERSION]. Called once, before the first settings read.
     *
     * An install that already existed keeps what it was actually showing: every default that moved
     * in v1 is written out at its pre-v1 value — but only where the reader had not already chosen
     * one, so a deliberate choice is never overwritten. A fresh install writes nothing but the
     * stamp, and so picks up the new defaults.
     *
     * Writing the old value is what makes this permanent. Once a preference exists it is no longer
     * at the mercy of the default, so no later change can move it either.
     */
    suspend fun migrateDefaults() {
        context.dataStore.edit { prefs ->
            if (prefs.contains(keyDefaultsVersion)) return@edit
            if (isExistingInstall(prefs.asMap().keys.mapTo(mutableSetOf()) { it.name })) {
                // The defaults exactly as they stood through 0.6.9.
                if (!prefs.contains(keyFillScreen)) prefs[keyFillScreen] = false
                if (!prefs.contains(keyBigButtons)) prefs[keyBigButtons] = false
                if (!prefs.contains(keyShowSessionTimer)) prefs[keyShowSessionTimer] = false
                if (!prefs.contains(keyShowTopSurahBar)) prefs[keyShowTopSurahBar] = false
                if (!prefs.contains(keyShowBottomJuzBar)) prefs[keyShowBottomJuzBar] = false
                if (!prefs.contains(keyBottomJuzBarColor)) prefs[keyBottomJuzBarColor] = "blue"
                if (!prefs.contains(keyEdgeMargin)) prefs[keyEdgeMargin] = EDGE_MARGIN_NONE
                if (!prefs.contains(keyBarButtons)) prefs[keyBarButtons] = setOf("fill", "hide")
                if (!prefs.contains(keyHiddenButtons)) prefs[keyHiddenButtons] = emptySet()
            }
            prefs[keyDefaultsVersion] = DEFAULTS_VERSION
        }
    }

    suspend fun settings(): Settings {
        val prefs = context.dataStore.data.first()
        val readPages = prefs[keyReadPages].toIntSet()
        return Settings(
            lastPage = prefs[keyLastPage] ?: 1,
            themeId = prefs[keyThemeId]
                ?: if (prefs[keyDarkTheme] == true) DARK_THEME_ID else DEFAULT_THEME_ID,
            fillScreen = prefs[keyFillScreen] ?: DEFAULT_FILL_SCREEN,
            // A page counted as read was necessarily opened. Builds before the rule was enforced
            // at the point of writing could leave a read page with no visited entry, so the union
            // is taken on the way out: the khatma map, and any backup made from it, stay
            // self-consistent whatever is already on disk.
            visitedPages = prefs[keyVisitedPages].toIntSet() + readPages,
            readPages = readPages,
            hiddenButtons = prefs[keyHiddenButtons] ?: DEFAULT_HIDDEN_BUTTONS,
            barButtons = prefs[keyBarButtons] ?: DEFAULT_BAR_BUTTONS,
            buttonColors = decodeButtonColors(prefs[keyButtonColors]),
            bigButtons = prefs[keyBigButtons] ?: DEFAULT_BIG_BUTTONS,
            showClock = prefs[keyShowClock] ?: false,
            showSessionTimer = prefs[keyShowSessionTimer] ?: DEFAULT_SHOW_SESSION_TIMER,
            showSurahNumber = prefs[keyShowSurahNumber] ?: false,
            showSurahAyahCount = prefs[keyShowSurahAyahCount] ?: false,
            showSurahProgress = prefs[keyShowSurahProgress] ?: false,
            showJuzProgressPercent = prefs[keyShowJuzProgressPercent] ?: false,
            showJuzProgressPages = prefs[keyShowJuzProgressPages] ?: false,
            showReadingPosition = prefs[keyShowReadingPosition] ?: false,
            clockColor = prefs[keyClockColor] ?: "muted",
            sessionTimerColor = prefs[keySessionTimerColor] ?: "muted",
            showButtonPage = prefs[keyShowButtonPage] ?: true,
            buttonPageColor = prefs[keyButtonPageColor] ?: "red",
            showHeaderButtonOpacity = (
                prefs[keyShowHeaderButtonOpacity] ?: DEFAULT_SHOW_HEADER_BUTTON_OPACITY
            ).coerceIn(25, 100),
            buttonPosFraction = prefs[keyButtonPosFraction] ?: -1f,
            showBottomJuzBar = prefs[keyShowBottomJuzBar] ?: DEFAULT_SHOW_BOTTOM_JUZ_BAR,
            bottomJuzBarColor = prefs[keyBottomJuzBarColor] ?: DEFAULT_BOTTOM_JUZ_BAR_COLOR,
            bottomJuzBarThickness = prefs[keyBottomJuzBarThickness] ?: DEFAULT_BAR_THICKNESS,
            bottomJuzBarOpacity = prefs[keyBottomJuzBarOpacity] ?: DEFAULT_BAR_OPACITY,
            showTopSurahBar = prefs[keyShowTopSurahBar] ?: DEFAULT_SHOW_TOP_SURAH_BAR,
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
            keepScreenOn = prefs[keyKeepScreenOn] ?: DEFAULT_KEEP_SCREEN_ON,
            edgeMargin = prefs[keyEdgeMargin] ?: DEFAULT_EDGE_MARGIN,
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
            prefs[keyThemeId] = value.themeId
            prefs[keyFillScreen] = value.fillScreen
            prefs[keyVisitedPages] = value.visitedPages.mapTo(HashSet()) { it.toString() }
            prefs[keyReadPages] = value.readPages.mapTo(HashSet()) { it.toString() }
            prefs[keyHiddenButtons] = value.hiddenButtons
            prefs[keyBarButtons] = value.barButtons
            prefs[keyButtonColors] = encodeButtonColors(value.buttonColors)
            prefs[keyBigButtons] = value.bigButtons
            prefs[keyShowClock] = value.showClock
            prefs[keyShowSessionTimer] = value.showSessionTimer
            prefs[keyShowSurahNumber] = value.showSurahNumber
            prefs[keyShowSurahAyahCount] = value.showSurahAyahCount
            prefs[keyShowSurahProgress] = value.showSurahProgress
            prefs[keyShowJuzProgressPercent] = value.showJuzProgressPercent
            prefs[keyShowJuzProgressPages] = value.showJuzProgressPages
            prefs[keyShowReadingPosition] = value.showReadingPosition
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
            prefs[keyKeepScreenOn] = value.keepScreenOn
            prefs[keyEdgeMargin] = value.edgeMargin
        }
    }

    /** Remembered so the backup screen can show when the user last exported, and to where. */
    data class LastBackup(val savedAt: Long, val fileName: String, val sizeBytes: Long)

    suspend fun lastBackup(): LastBackup? {
        val prefs = context.dataStore.data.first()
        val savedAt = prefs[keyLastBackupAt] ?: return null
        val fileName = prefs[keyLastBackupName]?.takeIf { it.isNotBlank() } ?: return null
        if (savedAt <= 0L) return null
        return LastBackup(savedAt, fileName, prefs[keyLastBackupSize] ?: 0L)
    }

    suspend fun setLastBackup(value: LastBackup?) {
        context.dataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(keyLastBackupAt)
                prefs.remove(keyLastBackupName)
                prefs.remove(keyLastBackupSize)
            } else {
                prefs[keyLastBackupAt] = value.savedAt
                prefs[keyLastBackupName] = value.fileName
                prefs[keyLastBackupSize] = value.sizeBytes
            }
        }
    }

    private fun Set<String>?.toIntSet(): Set<Int> =
        this?.mapNotNullTo(HashSet()) { it.toIntOrNull() } ?: emptySet()

    suspend fun updateNudge(): UpdateNudge {
        val prefs = context.dataStore.data.first()
        return UpdateNudge(
            checkedAt = prefs[keyUpdateCheckedAt] ?: 0L,
            dismissedVersion = prefs[keyUpdateDismissedVersion] ?: 0,
            dismissedAt = prefs[keyUpdateDismissedAt] ?: 0L,
        )
    }

    suspend fun setUpdateCheckedAt(value: Long) {
        context.dataStore.edit { it[keyUpdateCheckedAt] = value }
    }

    suspend fun setUpdateDismissed(versionCode: Int, at: Long) {
        context.dataStore.edit {
            it[keyUpdateDismissedVersion] = versionCode
            it[keyUpdateDismissedAt] = at
        }
    }

    suspend fun setEdgeMargin(value: String) {
        context.dataStore.edit { it[keyEdgeMargin] = value }
    }

    suspend fun setVisitedPages(pages: Set<Int>) {
        context.dataStore.edit { it[keyVisitedPages] = pages.mapTo(HashSet()) { p -> p.toString() } }
    }

    suspend fun setReadPages(pages: Set<Int>) {
        context.dataStore.edit { it[keyReadPages] = pages.mapTo(HashSet()) { p -> p.toString() } }
    }

    suspend fun setLastPage(page: Int) {
        context.dataStore.edit { it[keyLastPage] = page }
    }

    suspend fun setBarButtons(value: Set<String>) {
        context.dataStore.edit { it[keyBarButtons] = value }
    }

    suspend fun setButtonColors(value: Map<String, String>) {
        context.dataStore.edit { it[keyButtonColors] = encodeButtonColors(value) }
    }

    suspend fun setThemeId(value: String) {
        context.dataStore.edit { it[keyThemeId] = value }
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

    suspend fun setShowReadingPosition(value: Boolean) {
        context.dataStore.edit { it[keyShowReadingPosition] = value }
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

    suspend fun setKeepScreenOn(value: Boolean) {
        context.dataStore.edit { it[keyKeepScreenOn] = value }
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
