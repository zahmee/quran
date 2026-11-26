package com.mushaf.reader.reader

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.ui.theme.MushafPalette
import com.mushaf.reader.ui.theme.paletteFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * How the reader wants the page shown: theme, page fill, paging direction, header buttons and
 * their colours, the clock and timer, the progress bars and the note marks. Each value is state
 * the screen reads, and each change is written to [store] at once.
 *
 * Starts from [initial], read before the first frame so nothing flashes from a default.
 */
@Stable
class DisplaySettings internal constructor(
    private val store: ReadingStore,
    private val scope: CoroutineScope,
    initial: ReadingStore.Settings,
) {
    /** Id of the chosen reading theme; [palette] resolves it to the colors it stands for. */
    var themeId by mutableStateOf(initial.themeId)
        private set

    val palette: MushafPalette get() = paletteFor(themeId)

    /** When on, the page fills the screen (width-fill + scroll on wide screens, height-stretch
     *  on tall screens); when off, the whole page is fitted and centered. Kept once for a
     *  narrow screen and once for a wide one, so unfolding a phone brings back the choice
     *  made unfolded, and folding it the choice made folded. */
    var fillScreenNarrow by mutableStateOf(initial.fillScreen)
        private set
    var fillScreenWide by mutableStateOf(initial.fillScreenWide)
        private set

    fun fillScreen(wide: Boolean): Boolean = if (wide) fillScreenWide else fillScreenNarrow

    /** When on, pages turn vertically (swipe up/down) instead of horizontally. */
    var verticalPaging by mutableStateOf(initial.verticalPaging)
        private set

    /** When on, the screen is held awake while the reader is in the foreground. */
    var keepScreenOn by mutableStateOf(initial.keepScreenOn)
        private set

    /** How far the corner controls and the page pull back from a curved screen edge; one of the
     *  ReadingStore.EDGE_MARGIN_* ids. "none" is today's layout, and stays the default. */
    var edgeMargin by mutableStateOf(initial.edgeMargin)
        private set

    /** Whether an ayah that carries a note shows its mark on the page. */
    var showNoteMarks by mutableStateOf(initial.showNoteMarks)
        private set

    /** How strongly that mark shows, in percent. */
    var noteMarkOpacity by mutableStateOf(initial.noteMarkOpacity)
        private set

    /** Ids of the header buttons the user has hidden; any id NOT in this set is shown. */
    var hiddenButtons by mutableStateOf(initial.hiddenButtons)
        private set

    /** Ids drawn on the top bar itself; a visible action not in here lives in the More menu. */
    var barButtons by mutableStateOf(initial.barButtons)
        private set

    /** Color id per header action, for the icon it draws on the bar. Missing = "muted". */
    var buttonColors by mutableStateOf(initial.buttonColors)
        private set

    /** When on, the header buttons are rendered a little larger. */
    var bigButtons by mutableStateOf(initial.bigButtons)
        private set

    /** When on, a live wall-clock (red) shows in the header. */
    var showClock by mutableStateOf(initial.showClock)
        private set

    /** When on, the current reading-session duration (green) shows in the header. */
    var showSessionTimer by mutableStateOf(initial.showSessionTimer)
        private set

    var showSurahNumber by mutableStateOf(initial.showSurahNumber)
        private set

    var showSurahAyahCount by mutableStateOf(initial.showSurahAyahCount)
        private set

    var showSurahProgress by mutableStateOf(initial.showSurahProgress)
        private set

    var showJuzProgressPercent by mutableStateOf(initial.showJuzProgressPercent)
        private set

    var showJuzProgressPages by mutableStateOf(initial.showJuzProgressPages)
        private set

    var showReadingPosition by mutableStateOf(initial.showReadingPosition)
        private set

    var clockColor by mutableStateOf(initial.clockColor)
        private set

    var sessionTimerColor by mutableStateOf(initial.sessionTimerColor)
        private set

    /** Full-screen restore-header chip: show the page number, and its color. */
    var showButtonPage by mutableStateOf(initial.showButtonPage)
        private set

    var buttonPageColor by mutableStateOf(initial.buttonPageColor)
        private set

    /** Opacity of the draggable restore-header button, 25..100 percent. */
    var showHeaderButtonOpacity by mutableStateOf(initial.showHeaderButtonOpacity)
        private set

    /** Where the user parked the full-screen chip, as a 0..1 fraction of its vertical travel
     *  (0 = just under the top inset, 1 = bottom edge); -1 = never moved, so it rests at the top.
     *  A fraction — not pixels — so the spot holds across rotation and different screen sizes. */
    var buttonPosFraction by mutableStateOf(initial.buttonPosFraction)
        private set

    /** Thin juz-progress bar pinned to the bottom of the page, its color, and its thickness (dp). */
    var showBottomJuzBar by mutableStateOf(initial.showBottomJuzBar)
        private set

    var bottomJuzBarColor by mutableStateOf(initial.bottomJuzBarColor)
        private set

    var bottomJuzBarThickness by mutableStateOf(initial.bottomJuzBarThickness)
        private set

    /** How solid the bar is drawn, 0..100 percent. */
    var bottomJuzBarOpacity by mutableStateOf(initial.bottomJuzBarOpacity)
        private set

    /** Thin surah-progress bar sitting just above the page, its color, and its thickness (dp). */
    var showTopSurahBar by mutableStateOf(initial.showTopSurahBar)
        private set

    var topSurahBarColor by mutableStateOf(initial.topSurahBarColor)
        private set

    var topSurahBarThickness by mutableStateOf(initial.topSurahBarThickness)
        private set

    var topSurahBarOpacity by mutableStateOf(initial.topSurahBarOpacity)
        private set

    /** Full-screen edge marker for the current page's side (right/left of the spread): whether it
     *  shows, its color, and its bar width (thickness) and height (length) in dp. */
    var showPageSideIndicator by mutableStateOf(initial.showPageSideIndicator)
        private set

    var pageSideIndicatorColor by mutableStateOf(initial.pageSideIndicatorColor)
        private set

    var pageSideIndicatorThickness by mutableStateOf(initial.pageSideIndicatorThickness)
        private set

    var pageSideIndicatorLength by mutableStateOf(initial.pageSideIndicatorLength)
        private set

    var pageSideIndicatorOpacity by mutableStateOf(initial.pageSideIndicatorOpacity)
        private set

    fun updateThemeId(value: String) {
        if (value == themeId) return
        themeId = value
        scope.launch { store.setThemeId(value) }
    }

    /** Flips the choice for the kind of screen being read on, and leaves the other alone. */
    fun toggleFillScreen(wide: Boolean) {
        val value = !fillScreen(wide)
        if (wide) fillScreenWide = value else fillScreenNarrow = value
        scope.launch { store.setFillScreen(wide, value) }
    }

    fun updateVerticalPaging(value: Boolean) {
        if (value == verticalPaging) return
        verticalPaging = value
        scope.launch { store.setVerticalPaging(value) }
    }

    fun updateKeepScreenOn(value: Boolean) {
        if (value == keepScreenOn) return
        keepScreenOn = value
        scope.launch { store.setKeepScreenOn(value) }
    }

    fun updateEdgeMargin(value: String) {
        if (value == edgeMargin) return
        edgeMargin = value
        scope.launch { store.setEdgeMargin(value) }
    }

    fun updateShowNoteMarks(value: Boolean) {
        if (value == showNoteMarks) return
        showNoteMarks = value
        scope.launch { store.setShowNoteMarks(value) }
    }

    fun updateNoteMarkOpacity(value: Int) {
        val clamped = value.coerceIn(ReadingStore.NOTE_MARK_OPACITY_RANGE)
        if (clamped == noteMarkOpacity) return
        noteMarkOpacity = clamped
        scope.launch { store.setNoteMarkOpacity(clamped) }
    }

    /** Whether the header button with [id] is currently shown. */
    fun isButtonVisible(id: String): Boolean = !hiddenButtons.contains(id)

    /** Show or hide the header button with [id] and persist the choice. */
    fun setButtonVisible(id: String, visible: Boolean) {
        val next = if (visible) hiddenButtons - id else hiddenButtons + id
        if (next == hiddenButtons) return
        hiddenButtons = next
        scope.launch { store.setHiddenButtons(next) }
    }

    fun isButtonInBar(id: String): Boolean = barButtons.contains(id)

    /** Move the header action with [id] onto the bar, or back into the More menu. */
    fun setButtonInBar(id: String, inBar: Boolean) {
        val next = if (inBar) barButtons + id else barButtons - id
        if (next == barButtons) return
        barButtons = next
        scope.launch { store.setBarButtons(next) }
    }

    /** Color id the action with [id] draws in on the bar; "muted" means keep its own default. */
    fun buttonColor(id: String): String = buttonColors[id] ?: "muted"

    fun updateButtonColor(id: String, color: String) {
        if (buttonColor(id) == color) return
        buttonColors = buttonColors + (id to color)
        scope.launch { store.setButtonColors(buttonColors) }
    }

    /** Enable or disable the larger header buttons and persist the choice. */
    fun updateBigButtons(value: Boolean) {
        if (value == bigButtons) return
        bigButtons = value
        scope.launch { store.setBigButtons(value) }
    }

    fun updateShowClock(value: Boolean) {
        if (value == showClock) return
        showClock = value
        scope.launch { store.setShowClock(value) }
    }

    fun updateShowSessionTimer(value: Boolean) {
        if (value == showSessionTimer) return
        showSessionTimer = value
        scope.launch { store.setShowSessionTimer(value) }
    }

    fun updateShowSurahNumber(value: Boolean) {
        if (value == showSurahNumber) return
        showSurahNumber = value
        scope.launch { store.setShowSurahNumber(value) }
    }

    fun updateShowSurahAyahCount(value: Boolean) {
        if (value == showSurahAyahCount) return
        showSurahAyahCount = value
        scope.launch { store.setShowSurahAyahCount(value) }
    }

    fun updateShowSurahProgress(value: Boolean) {
        if (value == showSurahProgress) return
        showSurahProgress = value
        scope.launch { store.setShowSurahProgress(value) }
    }

    fun updateShowJuzProgressPercent(value: Boolean) {
        if (value == showJuzProgressPercent) return
        showJuzProgressPercent = value
        scope.launch { store.setShowJuzProgressPercent(value) }
    }

    fun updateShowJuzProgressPages(value: Boolean) {
        if (value == showJuzProgressPages) return
        showJuzProgressPages = value
        scope.launch { store.setShowJuzProgressPages(value) }
    }

    fun updateShowReadingPosition(value: Boolean) {
        if (value == showReadingPosition) return
        showReadingPosition = value
        scope.launch { store.setShowReadingPosition(value) }
    }

    fun updateClockColor(value: String) {
        if (value == clockColor) return
        clockColor = value
        scope.launch { store.setClockColor(value) }
    }

    fun updateSessionTimerColor(value: String) {
        if (value == sessionTimerColor) return
        sessionTimerColor = value
        scope.launch { store.setSessionTimerColor(value) }
    }

    fun updateShowButtonPage(value: Boolean) {
        if (value == showButtonPage) return
        showButtonPage = value
        scope.launch { store.setShowButtonPage(value) }
    }

    fun updateButtonPageColor(value: String) {
        if (value == buttonPageColor) return
        buttonPageColor = value
        scope.launch { store.setButtonPageColor(value) }
    }

    fun updateShowHeaderButtonOpacity(value: Int) {
        val normalized = value.coerceIn(25, 100)
        if (normalized == showHeaderButtonOpacity) return
        showHeaderButtonOpacity = normalized
        scope.launch { store.setShowHeaderButtonOpacity(normalized) }
    }

    /** Live drag of the full-screen chip: memory only, so it tracks the finger without a disk
     *  write per pixel. [saveButtonPosFraction] commits the spot when the finger lifts. */
    fun dragButtonPosFraction(value: Float) {
        buttonPosFraction = value.coerceIn(0f, 1f)
    }

    fun saveButtonPosFraction() {
        val value = buttonPosFraction
        scope.launch { store.setButtonPosFraction(value) }
    }

    fun updateShowBottomJuzBar(value: Boolean) {
        if (value == showBottomJuzBar) return
        showBottomJuzBar = value
        scope.launch { store.setShowBottomJuzBar(value) }
    }

    fun updateBottomJuzBarColor(value: String) {
        if (value == bottomJuzBarColor) return
        bottomJuzBarColor = value
        scope.launch { store.setBottomJuzBarColor(value) }
    }

    fun updateBottomJuzBarThickness(value: Int) {
        if (value == bottomJuzBarThickness) return
        bottomJuzBarThickness = value
        scope.launch { store.setBottomJuzBarThickness(value) }
    }

    fun updateBottomJuzBarOpacity(value: Int) {
        if (value == bottomJuzBarOpacity) return
        bottomJuzBarOpacity = value
        scope.launch { store.setBottomJuzBarOpacity(value) }
    }

    fun updateShowTopSurahBar(value: Boolean) {
        if (value == showTopSurahBar) return
        showTopSurahBar = value
        scope.launch { store.setShowTopSurahBar(value) }
    }

    fun updateTopSurahBarColor(value: String) {
        if (value == topSurahBarColor) return
        topSurahBarColor = value
        scope.launch { store.setTopSurahBarColor(value) }
    }

    fun updateTopSurahBarThickness(value: Int) {
        if (value == topSurahBarThickness) return
        topSurahBarThickness = value
        scope.launch { store.setTopSurahBarThickness(value) }
    }

    fun updateTopSurahBarOpacity(value: Int) {
        if (value == topSurahBarOpacity) return
        topSurahBarOpacity = value
        scope.launch { store.setTopSurahBarOpacity(value) }
    }

    fun updateShowPageSideIndicator(value: Boolean) {
        if (value == showPageSideIndicator) return
        showPageSideIndicator = value
        scope.launch { store.setShowPageSideIndicator(value) }
    }

    fun updatePageSideIndicatorColor(value: String) {
        if (value == pageSideIndicatorColor) return
        pageSideIndicatorColor = value
        scope.launch { store.setPageSideIndicatorColor(value) }
    }

    fun updatePageSideIndicatorThickness(value: Int) {
        if (value == pageSideIndicatorThickness) return
        pageSideIndicatorThickness = value
        scope.launch { store.setPageSideIndicatorThickness(value) }
    }

    fun updatePageSideIndicatorLength(value: Int) {
        if (value == pageSideIndicatorLength) return
        pageSideIndicatorLength = value
        scope.launch { store.setPageSideIndicatorLength(value) }
    }

    fun updatePageSideIndicatorOpacity(value: Int) {
        if (value == pageSideIndicatorOpacity) return
        pageSideIndicatorOpacity = value
        scope.launch { store.setPageSideIndicatorOpacity(value) }
    }

    /** Takes every value from restored [value] settings, as a restore or merge leaves them. */
    internal fun apply(value: ReadingStore.Settings) {
        themeId = value.themeId
        fillScreenNarrow = value.fillScreen
        fillScreenWide = value.fillScreenWide
        verticalPaging = value.verticalPaging
        keepScreenOn = value.keepScreenOn
        edgeMargin = value.edgeMargin
        showNoteMarks = value.showNoteMarks
        noteMarkOpacity = value.noteMarkOpacity
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
    }
}
