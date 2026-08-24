package com.mushaf.reader.reader

import android.content.ClipData
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mushaf.reader.ui.components.MushafSegmentedTabs
import com.mushaf.reader.ui.theme.BookmarkGoldColor
import com.mushaf.reader.ui.theme.BookmarkVioletColor
import com.mushaf.reader.ui.theme.ReadingType
import com.mushaf.reader.ui.theme.StatusBlueColor
import com.mushaf.reader.ui.theme.StatusGoldColor
import com.mushaf.reader.ui.theme.StatusGreenColor
import com.mushaf.reader.ui.theme.StatusRedColor
import com.mushaf.reader.data.AyahMarker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ReaderScreen(viewModel: ReaderViewModel) {
    val pageCount = viewModel.pageCount.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = (viewModel.initialPage - 1).coerceIn(0, pageCount - 1),
        pageCount = { pageCount }
    )
    val scope = rememberCoroutineScope()
    var showGoTo by remember { mutableStateOf(false) }
    var showStatsScreen by remember { mutableStateOf(false) }
    var showKhatmaMap by remember { mutableStateOf(false) }
    var showIndex by remember { mutableStateOf(false) }
    var indexTab by remember { mutableStateOf(0) }
    var showSearch by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDriveBackup by remember { mutableStateOf(false) }
    var headerVisible by remember { mutableStateOf(true) }
    val selected = viewModel.selectedAyah

    val jumpToPage: (Int) -> Unit = { page ->
        scope.launch { pagerState.scrollToPage((page - 1).coerceIn(0, pageCount - 1)) }
    }

    BackHandler(enabled = showStatsScreen) { showStatsScreen = false }
    // Registered after stats so Back from the map returns to the stats screen it opened from.
    BackHandler(enabled = showKhatmaMap) { showKhatmaMap = false }
    BackHandler(enabled = showIndex) { showIndex = false }
    BackHandler(enabled = showSearch) { showSearch = false }
    BackHandler(enabled = showSettings) { showSettings = false }
    // The Drive screen opens above Settings, so Back returns to the Settings app tab.
    BackHandler(enabled = showDriveBackup) { showDriveBackup = false }
    // Registered after index/settings so Back from About returns to the screen it was opened from.
    BackHandler(enabled = showAbout) { showAbout = false }
    // When the header is hidden, Back brings it back instead of leaving the app.
    BackHandler(enabled = !showStatsScreen && !showIndex && !showSearch && !showAbout &&
        !showSettings && !showDriveBackup && !headerVisible
    ) {
        headerVisible = true
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.clearSelection()
        viewModel.onPageVisible(pagerState.currentPage + 1)
    }

    val restoredPage = viewModel.restorePageRequest
    LaunchedEffect(restoredPage) {
        if (restoredPage != null) {
            pagerState.scrollToPage((restoredPage - 1).coerceIn(0, pageCount - 1))
            viewModel.consumeRestorePageRequest()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val currentPage = pagerState.currentPage + 1
                val pageInfo = viewModel.markersForPage(currentPage).firstOrNull()
                val juzInfo = viewModel.juzInfoForPage(currentPage)

                if (headerVisible) {
                    ReaderHeader(
                        page = currentPage,
                        surahNumber = pageInfo?.surahNumber,
                        surah = pageInfo?.surahNameAr,
                        ayahCount = pageInfo?.let { viewModel.surahAyahCount(it.surahNumber) },
                        surahPercent = pageInfo?.let { viewModel.surahProgressPercent(currentPage, it.surahNumber) },
                        juz = juzInfo.juz,
                        pageInJuz = juzInfo.pageInJuz,
                        pagesInJuz = juzInfo.pagesInJuz,
                        juzPercent = juzInfo.pageInJuz * 100 / juzInfo.pagesInJuz,
                        dark = viewModel.darkTheme,
                        hasBookmark = viewModel.bookmarks.isNotEmpty(),
                        hasBookmark2 = viewModel.bookmarks2.isNotEmpty(),
                        fillScreen = viewModel.fillScreen,
                        bigButtons = viewModel.bigButtons,
                        showClock = viewModel.showClock,
                        showSessionTimer = viewModel.showSessionTimer,
                        showSurahNumber = viewModel.showSurahNumber,
                        showSurahAyahCount = viewModel.showSurahAyahCount,
                        showSurahProgress = viewModel.showSurahProgress,
                        showJuzProgressPercent = viewModel.showJuzProgressPercent,
                        showJuzProgressPages = viewModel.showJuzProgressPages,
                        clockColor = viewModel.clockColor,
                        sessionTimerColor = viewModel.sessionTimerColor,
                        sessionStartedAt = viewModel.sessionStartedAt,
                        isVisible = { id -> viewModel.isButtonVisible(id) },
                        onOpenSettings = { showSettings = true },
                        onOpenIndex = { tab ->
                            indexTab = tab
                            showIndex = true
                        },
                        onOpenSearch = { showSearch = true },
                        onToggleFillScreen = { viewModel.toggleFillScreen() },
                        onHideHeader = { headerVisible = false },
                        onToggleTheme = { viewModel.toggleTheme() },
                        onBookmarkJump = {
                            viewModel.bookmarkJumpPage()?.let { p ->
                                scope.launch { pagerState.scrollToPage((p - 1).coerceIn(0, pageCount - 1)) }
                            }
                        },
                        onBookmark2Jump = {
                            viewModel.bookmark2JumpPage()?.let { p ->
                                scope.launch { pagerState.scrollToPage((p - 1).coerceIn(0, pageCount - 1)) }
                            }
                        },
                        onStats = {
                            viewModel.openStats()
                            showStatsScreen = true
                        },
                        onPageClick = { showGoTo = true }
                    )
                }

                // Optional thin surah-progress bar directly above the page. It always hugs the very
                // top of its slot — under the header when that shows, at the screen's top edge in
                // full-screen — and deliberately does NOT reserve the camera band, so the page fills
                // the screen right up to the top instead of leaving a gap (the bar simply sits behind
                // a punch-hole camera if one is there).
                if (viewModel.showTopSurahBar) {
                    TopSurahBar(
                        fraction = pageInfo
                            ?.let { viewModel.surahProgressPercent(currentPage, it.surahNumber) / 100f }
                            ?: 0f,
                        colorId = viewModel.topSurahBarColor,
                        thickness = viewModel.topSurahBarThickness.dp,
                        opacity = viewModel.topSurahBarOpacity / 100f
                    )
                }

                ReaderPager(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    viewModel = viewModel,
                    pagerState = pagerState,
                    selected = selected,
                    fillScreen = viewModel.fillScreen,
                    verticalPaging = viewModel.verticalPaging
                )
            }

            // When the header is hidden, a small draggable button restores it (and shows the page).
            if (!headerVisible) {
                ShowHeaderButton(
                    page = pagerState.currentPage + 1,
                    showPage = viewModel.showButtonPage,
                    pageColorId = viewModel.buttonPageColor,
                    opacity = viewModel.showHeaderButtonOpacity / 100f,
                    posFraction = viewModel.buttonPosFraction,
                    onDrag = viewModel::dragButtonPosFraction,
                    onDragEnd = viewModel::saveButtonPosFraction,
                    onClick = { headerVisible = true }
                )
            }

            // Full-screen only: a small edge bar marks whether the page sits on the right or left.
            if (!headerVisible && viewModel.showPageSideIndicator) {
                PageSideIndicator(
                    page = pagerState.currentPage + 1,
                    colorId = viewModel.pageSideIndicatorColor,
                    thickness = viewModel.pageSideIndicatorThickness.dp,
                    length = viewModel.pageSideIndicatorLength.dp,
                    opacity = viewModel.pageSideIndicatorOpacity / 100f
                )
            }

            // Optional thin juz-progress bar pinned to the bottom of the page.
            if (viewModel.showBottomJuzBar) {
                val bbPage = pagerState.currentPage + 1
                val bbJuz = viewModel.juzInfoForPage(bbPage)
                BottomJuzBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    fraction = bbJuz.pageInJuz.toFloat() / bbJuz.pagesInJuz.coerceAtLeast(1),
                    colorId = viewModel.bottomJuzBarColor,
                    thickness = viewModel.bottomJuzBarThickness.dp,
                    opacity = viewModel.bottomJuzBarOpacity / 100f
                )
            }
        }

        if (showGoTo) {
            GoToPageDialog(
                pageCount = pageCount,
                onDismiss = { showGoTo = false },
                onGo = { n ->
                    showGoTo = false
                    scope.launch { pagerState.scrollToPage((n - 1).coerceIn(0, pageCount - 1)) }
                }
            )
        }

        if (showStatsScreen) {
            StatsScreen(
                stats = viewModel.fullStats,
                sessions = viewModel.sessions,
                khatmas = viewModel.khatmas,
                khatmaStartedAt = viewModel.khatmaStartedAt,
                onCompleteKhatma = { viewModel.completeKhatma() },
                onResetKhatma = { viewModel.resetKhatmaProgress() },
                onBack = { showStatsScreen = false },
                onOpenKhatmaMap = { showKhatmaMap = true },
            )
        }

        if (showKhatmaMap) {
            KhatmaMapScreen(
                totalPages = pageCount,
                readPages = viewModel.readPagesAll,
                visitedPages = viewModel.visitedPagesAll,
                bookmarkPage = viewModel.bookmarkJumpPage(),
                currentPage = pagerState.currentPage + 1,
                onToggleRead = { viewModel.togglePageRead(it) },
                onJump = { page ->
                    showKhatmaMap = false
                    showStatsScreen = false
                    jumpToPage(page)
                },
                onBack = { showKhatmaMap = false }
            )
        }

        if (showIndex) {
            val loaded = viewModel.ayahLoaded
            val surahs = remember(loaded) { viewModel.surahIndex() }
            val juzs = remember(loaded) { viewModel.juzIndex() }
            IndexScreen(
                surahs = surahs,
                juzs = juzs,
                initialTab = indexTab,
                currentPage = pagerState.currentPage + 1,
                onJump = { page ->
                    showIndex = false
                    jumpToPage(page)
                },
                onAbout = { showAbout = true },
                onBack = { showIndex = false }
            )
        }

        if (showSettings) {
            SettingsScreen(
                isVisible = { id -> viewModel.isButtonVisible(id) },
                onToggle = { id, v -> viewModel.setButtonVisible(id, v) },
                bigButtons = viewModel.bigButtons,
                onBigButtonsChange = { viewModel.updateBigButtons(it) },
                verticalPaging = viewModel.verticalPaging,
                onVerticalPagingChange = { viewModel.updateVerticalPaging(it) },
                showClock = viewModel.showClock,
                onShowClockChange = { viewModel.updateShowClock(it) },
                showSessionTimer = viewModel.showSessionTimer,
                onShowSessionTimerChange = { viewModel.updateShowSessionTimer(it) },
                showSurahNumber = viewModel.showSurahNumber,
                onShowSurahNumberChange = { viewModel.updateShowSurahNumber(it) },
                showSurahAyahCount = viewModel.showSurahAyahCount,
                onShowSurahAyahCountChange = { viewModel.updateShowSurahAyahCount(it) },
                showSurahProgress = viewModel.showSurahProgress,
                onShowSurahProgressChange = { viewModel.updateShowSurahProgress(it) },
                showJuzProgressPercent = viewModel.showJuzProgressPercent,
                onShowJuzProgressPercentChange = { viewModel.updateShowJuzProgressPercent(it) },
                showJuzProgressPages = viewModel.showJuzProgressPages,
                onShowJuzProgressPagesChange = { viewModel.updateShowJuzProgressPages(it) },
                clockColor = viewModel.clockColor,
                onClockColorChange = { viewModel.updateClockColor(it) },
                sessionTimerColor = viewModel.sessionTimerColor,
                onSessionTimerColorChange = { viewModel.updateSessionTimerColor(it) },
                showButtonPage = viewModel.showButtonPage,
                onShowButtonPageChange = { viewModel.updateShowButtonPage(it) },
                buttonPageColor = viewModel.buttonPageColor,
                onButtonPageColorChange = { viewModel.updateButtonPageColor(it) },
                showHeaderButtonOpacity = viewModel.showHeaderButtonOpacity,
                onShowHeaderButtonOpacityChange = { viewModel.updateShowHeaderButtonOpacity(it) },
                showBottomJuzBar = viewModel.showBottomJuzBar,
                onShowBottomJuzBarChange = { viewModel.updateShowBottomJuzBar(it) },
                bottomJuzBarColor = viewModel.bottomJuzBarColor,
                onBottomJuzBarColorChange = { viewModel.updateBottomJuzBarColor(it) },
                bottomJuzBarThickness = viewModel.bottomJuzBarThickness,
                onBottomJuzBarThicknessChange = { viewModel.updateBottomJuzBarThickness(it) },
                bottomJuzBarOpacity = viewModel.bottomJuzBarOpacity,
                onBottomJuzBarOpacityChange = { viewModel.updateBottomJuzBarOpacity(it) },
                showTopSurahBar = viewModel.showTopSurahBar,
                onShowTopSurahBarChange = { viewModel.updateShowTopSurahBar(it) },
                topSurahBarColor = viewModel.topSurahBarColor,
                onTopSurahBarColorChange = { viewModel.updateTopSurahBarColor(it) },
                topSurahBarThickness = viewModel.topSurahBarThickness,
                onTopSurahBarThicknessChange = { viewModel.updateTopSurahBarThickness(it) },
                topSurahBarOpacity = viewModel.topSurahBarOpacity,
                onTopSurahBarOpacityChange = { viewModel.updateTopSurahBarOpacity(it) },
                showPageSideIndicator = viewModel.showPageSideIndicator,
                onShowPageSideIndicatorChange = { viewModel.updateShowPageSideIndicator(it) },
                pageSideIndicatorColor = viewModel.pageSideIndicatorColor,
                onPageSideIndicatorColorChange = { viewModel.updatePageSideIndicatorColor(it) },
                pageSideIndicatorThickness = viewModel.pageSideIndicatorThickness,
                onPageSideIndicatorThicknessChange = { viewModel.updatePageSideIndicatorThickness(it) },
                pageSideIndicatorLength = viewModel.pageSideIndicatorLength,
                onPageSideIndicatorLengthChange = { viewModel.updatePageSideIndicatorLength(it) },
                pageSideIndicatorOpacity = viewModel.pageSideIndicatorOpacity,
                onPageSideIndicatorOpacityChange = { viewModel.updatePageSideIndicatorOpacity(it) },
                onBackup = { showDriveBackup = true },
                onAbout = { showAbout = true },
                onClearAllStats = { viewModel.clearAllStats() },
                onBack = { showSettings = false }
            )
        }

        if (showDriveBackup) {
            DriveBackupScreen(
                viewModel = viewModel,
                onBack = { showDriveBackup = false },
            )
        }

        if (showAbout) {
            AboutScreen(onBack = { showAbout = false })
        }

        if (showSearch) {
            SearchScreen(
                onSearch = { q -> viewModel.search(q) },
                onJump = { page ->
                    showSearch = false
                    jumpToPage(page)
                },
                onBack = { showSearch = false }
            )
        }
    }
}

private enum class ExplanationTab(val title: String) {
    Tafsir("التفسير الميسر"),
    Meanings("معاني الكلمات"),
}

private data class ExplanationRequest(
    val ayah: AyahMarker,
    val initialTab: ExplanationTab,
)

@Composable
private fun ReaderPager(
    modifier: Modifier,
    viewModel: ReaderViewModel,
    pagerState: PagerState,
    selected: AyahMarker?,
    fillScreen: Boolean,
    verticalPaging: Boolean,
) {
    var anchor by remember { mutableStateOf(Offset.Zero) }
    var longPressAyah by remember { mutableStateOf<AyahMarker?>(null) }
    var explanationRequest by remember { mutableStateOf<ExplanationRequest?>(null) }

    LaunchedEffect(selected) {
        if (selected == null) {
            longPressAyah = null
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val boxW = constraints.maxWidth
        val boxH = constraints.maxHeight

        // Same page content for both orientations; only the pager axis differs.
        val pageContent: @Composable PagerScope.(Int) -> Unit = { index ->
            val pageNumber = index + 1
            ZoomablePage(
                model = viewModel.assetModel(pageNumber),
                markers = viewModel.markersForPage(pageNumber),
                imageWidth = viewModel.imageWidth,
                imageHeight = viewModel.imageHeight,
                selectedAyah = selected,
                bookmarkedKeys = if (viewModel.isButtonVisible("bookmark")) viewModel.bookmarks else emptySet(),
                bookmarkedKeys2 = if (viewModel.isButtonVisible("bookmark2")) viewModel.bookmarks2 else emptySet(),
                onLongPressAyah = { m, off ->
                    viewModel.selectAyah(m)
                    anchor = off
                    longPressAyah = m
                },
                fillScreen = fillScreen
            )
        }

        if (verticalPaging) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                pageContent = pageContent,
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                pageContent = pageContent,
            )
        }

        longPressAyah?.let { ayah ->
            AyahLongPressMenu(
                ayah = ayah,
                anchor = anchor,
                boxWidthPx = boxW,
                boxHeightPx = boxH,
                bookmarked = viewModel.isBookmarked(ayah.verseKey),
                onBookmark = { viewModel.toggleBookmark(ayah) },
                showBookmark = viewModel.isButtonVisible("bookmark"),
                bookmarked2 = viewModel.isBookmarked2(ayah.verseKey),
                onBookmark2 = { viewModel.toggleBookmark2(ayah) },
                showBookmark2 = viewModel.isButtonVisible("bookmark2"),
                onTafsir = {
                    longPressAyah = null
                    viewModel.loadExplanation(ayah)
                    explanationRequest = ExplanationRequest(ayah, ExplanationTab.Tafsir)
                },
                onMeanings = {
                    longPressAyah = null
                    viewModel.loadExplanation(ayah)
                    explanationRequest = ExplanationRequest(ayah, ExplanationTab.Meanings)
                },
                onClose = {
                    longPressAyah = null
                    viewModel.clearSelection()
                },
            )
        }

        explanationRequest?.let { request ->
            AyahExplanationSheet(
                request = request,
                state = viewModel.explanationState,
                onRetry = { viewModel.loadExplanation(request.ayah) },
                onDismiss = {
                    explanationRequest = null
                    viewModel.clearExplanation()
                    viewModel.clearSelection()
                },
            )
        }
    }
}

@Composable
private fun ReaderHeader(
    page: Int,
    surahNumber: Int?,
    surah: String?,
    ayahCount: Int?,
    surahPercent: Int?,
    juz: Int,
    pageInJuz: Int,
    pagesInJuz: Int,
    juzPercent: Int,
    dark: Boolean,
    hasBookmark: Boolean,
    hasBookmark2: Boolean,
    fillScreen: Boolean,
    bigButtons: Boolean,
    showClock: Boolean,
    showSessionTimer: Boolean,
    showSurahNumber: Boolean,
    showSurahAyahCount: Boolean,
    showSurahProgress: Boolean,
    showJuzProgressPercent: Boolean,
    showJuzProgressPages: Boolean,
    clockColor: String,
    sessionTimerColor: String,
    sessionStartedAt: Long,
    isVisible: (String) -> Boolean,
    onOpenSettings: () -> Unit,
    onOpenIndex: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onToggleFillScreen: () -> Unit,
    onHideHeader: () -> Unit,
    onToggleTheme: () -> Unit,
    onBookmarkJump: () -> Unit,
    onBookmark2Jump: () -> Unit,
    onStats: () -> Unit,
    onPageClick: () -> Unit,
) {
    val headerColor = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface
    val quietContentColor = contentColor.copy(alpha = 0.68f)
    val activeContentColor = MaterialTheme.colorScheme.primary
    val clockTextColor = headerStatusColor(clockColor, quietContentColor)
    val sessionTimerTextColor = headerStatusColor(sessionTimerColor, quietContentColor)

    // Height of the top camera/status-bar band (still reported in immersive mode). The strip
    // below fills it with corner buttons; the center is left clear for a punch-hole/notch.
    val topInset = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateTopPadding()

    // "Bigger buttons" setting bumps the header icons up a notch (and the bookmark in the info row).
    val btnSize = if (bigButtons) 40.dp else 32.dp
    val iconSize = if (bigButtons) 24.dp else 20.dp

    // Landscape, or an unfolded foldable's wide inner screen, has room to collapse the two header
    // rows into one. A portrait phone or a folded/cover screen (narrow) keeps the two-row layout.
    val configuration = LocalConfiguration.current
    val singleRow = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600

    // Live ticker for the optional clock / current-session timer (only loops while one is shown).
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(showClock, showSessionTimer) {
        while (showClock || showSessionTimer) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    Surface(
        color = headerColor,
        contentColor = contentColor,
        shadowElevation = 1.dp
    ) {
        if (singleRow) {
            // One-row header for wide screens. Padded fully clear of the camera cutout — the top
            // band in portrait, a side edge in landscape — so no control sits under the punch-hole.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .height(btnSize)
            ) {
                // Start group: settings · optional clock · surah identity.
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(btnSize)) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "إعدادات",
                            tint = quietContentColor,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    if (showClock) {
                        Text(
                            text = formatClock(nowMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = clockTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                    if (surah != null) {
                        SurahChip(
                            surah = surah,
                            surahNumber = surahNumber,
                            ayahCount = ayahCount,
                            surahPercent = surahPercent,
                            showSurahNumber = showSurahNumber,
                            showSurahAyahCount = showSurahAyahCount,
                            showSurahProgress = showSurahProgress,
                            color = quietContentColor,
                            modifier = Modifier.widthIn(max = 160.dp),
                            onClick = { onOpenIndex(0) }
                        )
                    }
                }
                // Center: page badge, kept off the cutout by the box's inset padding above.
                PageChip(
                    page = page,
                    contentColor = contentColor,
                    quietContentColor = quietContentColor,
                    accentColor = activeContentColor,
                    big = bigButtons,
                    modifier = Modifier.align(Alignment.Center),
                    onClick = onPageClick
                )
                // End group: juz position · optional session timer · hide · overflow menu.
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    JuzChip(
                        juz = juz,
                        juzPercent = juzPercent,
                        pageInJuz = pageInJuz,
                        pagesInJuz = pagesInJuz,
                        showJuzProgressPercent = showJuzProgressPercent,
                        showJuzProgressPages = showJuzProgressPages,
                        color = quietContentColor,
                        accentColor = activeContentColor,
                        big = bigButtons,
                        onClick = { onOpenIndex(1) }
                    )
                    if (showSessionTimer && sessionStartedAt > 0L) {
                        Text(
                            text = formatElapsed(nowMs - sessionStartedAt),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = sessionTimerTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                    if (isVisible("fill")) {
                        IconButton(onClick = onToggleFillScreen, modifier = Modifier.size(btnSize)) {
                            Icon(
                                imageVector = if (fillScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (fillScreen) "عرض الصفحة كاملة" else "توسيع الصفحة",
                                tint = if (fillScreen) activeContentColor else quietContentColor,
                                modifier = Modifier.size(iconSize),
                            )
                        }
                    }
                    if (isVisible("hide")) {
                        IconButton(onClick = onHideHeader, modifier = Modifier.size(btnSize)) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "إخفاء الشريط العلوي وملء الشاشة",
                                tint = quietContentColor,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }
                    HeaderMoreMenu(
                        btnSize = btnSize,
                        iconSize = iconSize,
                        tint = quietContentColor,
                        quietContentColor = quietContentColor,
                        hasBookmark = hasBookmark,
                        hasBookmark2 = hasBookmark2,
                        dark = dark,
                        isVisible = isVisible,
                        onOpenSearch = onOpenSearch,
                        onBookmarkJump = onBookmarkJump,
                        onBookmark2Jump = onBookmark2Jump,
                        onStats = onStats,
                        onOpenIndex = onOpenIndex,
                        onToggleTheme = onToggleTheme
                    )
                }
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Keep clear of a side notch in landscape; the top band is handled by the strip.
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        ) {
            // Camera-safe top strip: every quick-access button lives here, split between the two
            // corners so the center stays clear for a punch-hole/notch. Trim a little of the inset
            // to tighten the top gap, but never below the icon height (32dp).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxOf(topInset - 6.dp, btnSize))
            ) {
                // Start corner (top-right in RTL): keep the always-needed controls close and quiet.
                Row(
                    modifier = Modifier.align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App settings — always shown so it can never hide itself away.
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(btnSize)) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "إعدادات",
                            tint = quietContentColor,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    // Optional live wall-clock, shown beside the always-available settings button.
                    if (showClock) {
                        Text(
                            text = formatClock(nowMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = clockTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }
                // End corner (top-left in RTL): reading-first actions plus a secondary menu.
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Optional current-session duration (green), shown before the stats button.
                    if (showSessionTimer && sessionStartedAt > 0L) {
                        Text(
                            text = formatElapsed(nowMs - sessionStartedAt),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = sessionTimerTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                    if (isVisible("fill")) {
                        IconButton(onClick = onToggleFillScreen, modifier = Modifier.size(btnSize)) {
                            Icon(
                                imageVector = if (fillScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (fillScreen) "عرض الصفحة كاملة" else "توسيع الصفحة",
                                tint = if (fillScreen) activeContentColor else quietContentColor,
                                modifier = Modifier.size(iconSize),
                            )
                        }
                    }
                    if (isVisible("hide")) IconButton(onClick = onHideHeader, modifier = Modifier.size(btnSize)) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "إخفاء الشريط العلوي وملء الشاشة",
                            tint = quietContentColor,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    HeaderMoreMenu(
                        btnSize = btnSize,
                        iconSize = iconSize,
                        tint = quietContentColor,
                        quietContentColor = quietContentColor,
                        hasBookmark = hasBookmark,
                        hasBookmark2 = hasBookmark2,
                        dark = dark,
                        isVisible = isVisible,
                        onOpenSearch = onOpenSearch,
                        onBookmarkJump = onBookmarkJump,
                        onBookmark2Jump = onBookmark2Jump,
                        onStats = onStats,
                        onOpenIndex = onOpenIndex,
                        onToggleTheme = onToggleTheme
                    )
                }
            }

            // Info row — surah identity (start) · page number (center) · juz position (end).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                if (surah != null) {
                    SurahChip(
                        surah = surah,
                        surahNumber = surahNumber,
                        ayahCount = ayahCount,
                        surahPercent = surahPercent,
                        showSurahNumber = showSurahNumber,
                        showSurahAyahCount = showSurahAyahCount,
                        showSurahProgress = showSurahProgress,
                        color = quietContentColor,
                        modifier = Modifier.align(Alignment.CenterStart),
                        onClick = { onOpenIndex(0) }
                    )
                }

                PageChip(
                    page = page,
                    contentColor = contentColor,
                    quietContentColor = quietContentColor,
                    accentColor = activeContentColor,
                    big = bigButtons,
                    modifier = Modifier.align(Alignment.Center),
                    onClick = onPageClick
                )

                JuzChip(
                    juz = juz,
                    juzPercent = juzPercent,
                    pageInJuz = pageInJuz,
                    pagesInJuz = pagesInJuz,
                    showJuzProgressPercent = showJuzProgressPercent,
                    showJuzProgressPages = showJuzProgressPages,
                    color = quietContentColor,
                    accentColor = activeContentColor,
                    big = bigButtons,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = { onOpenIndex(1) }
                )
            }
        }
        }
    }
}

/** Overflow ("more") menu shared by both header layouts: the secondary reading actions live
 *  behind a single button so the strip stays uncluttered. Owns its own open/closed state. */
@Composable
private fun HeaderMoreMenu(
    btnSize: Dp,
    iconSize: Dp,
    tint: Color,
    quietContentColor: Color,
    hasBookmark: Boolean,
    hasBookmark2: Boolean,
    dark: Boolean,
    isVisible: (String) -> Boolean,
    onOpenSearch: () -> Unit,
    onBookmarkJump: () -> Unit,
    onBookmark2Jump: () -> Unit,
    onStats: () -> Unit,
    onOpenIndex: (Int) -> Unit,
    onToggleTheme: () -> Unit,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(btnSize)) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "المزيد",
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
        DropdownMenu(
            expanded = showMoreMenu,
            onDismissRequest = { showMoreMenu = false }
        ) {
            if (isVisible("search")) {
                DropdownMenuItem(
                    text = { Text("البحث") },
                    leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onOpenSearch()
                    }
                )
            }
            if (isVisible("bookmark")) {
                DropdownMenuItem(
                    text = { Text("العلامة المرجعية") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = if (hasBookmark) BookmarkGoldColor else quietContentColor
                        )
                    },
                    enabled = hasBookmark,
                    onClick = {
                        showMoreMenu = false
                        onBookmarkJump()
                    }
                )
            }
            if (isVisible("bookmark2")) {
                DropdownMenuItem(
                    text = { Text("العلامة المرجعية الثانية") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = if (hasBookmark2) BookmarkVioletColor else quietContentColor
                        )
                    },
                    enabled = hasBookmark2,
                    onClick = {
                        showMoreMenu = false
                        onBookmark2Jump()
                    }
                )
            }
            if (isVisible("stats")) {
                DropdownMenuItem(
                    text = { Text("إحصائيات القراءة") },
                    leadingIcon = { Icon(imageVector = Icons.Filled.BarChart, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onStats()
                    }
                )
            }
            if (isVisible("index")) {
                DropdownMenuItem(
                    text = { Text("الفهرس") },
                    leadingIcon = { Icon(imageVector = Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onOpenIndex(0)
                    }
                )
            }
            if (isVisible("theme")) {
                DropdownMenuItem(
                    text = { Text(if (dark) "الوضع الفاتح" else "الوضع الليلي") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showMoreMenu = false
                        onToggleTheme()
                    }
                )
            }
        }
    }
}

/** Surah identity label (number · name · ayah count · progress), tappable to open the index.
 *  Shared by both header layouts. */
@Composable
private fun SurahChip(
    surah: String,
    surahNumber: Int?,
    ayahCount: Int?,
    surahPercent: Int?,
    showSurahNumber: Boolean,
    showSurahAyahCount: Boolean,
    showSurahProgress: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val surahLabel = buildString {
        if (showSurahNumber && surahNumber != null) append("${surahNumber.toArabicDigits()} ")
        append(surah)
        if (showSurahAyahCount && ayahCount != null && ayahCount > 0) {
            append(" (${ayahCount.toArabicDigits()})")
        }
        if (showSurahProgress && surahPercent != null) append(" ${surahPercent.toArabicDigits()}٪")
    }
    Text(
        text = surahLabel,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 3.dp)
    )
}

/** Two-page mushaf badge with the current page number, tappable to open the page menu.
 *  Shared by both header layouts. */
@Composable
private fun PageChip(
    page: Int,
    contentColor: Color,
    quietContentColor: Color,
    accentColor: Color,
    big: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        MushafPageBadge(
            page = page,
            // Paper-mushaf order: odd pages sit on the right, even pages on the left.
            onRightPage = page % 2 == 1,
            contentColor = contentColor,
            quietContentColor = quietContentColor,
            accentColor = accentColor,
            big = big
        )
    }
}

/** Juz position label with an optional progress ring, tappable to open the juz index.
 *  Shared by both header layouts. */
@Composable
private fun JuzChip(
    juz: Int,
    juzPercent: Int,
    pageInJuz: Int,
    pagesInJuz: Int,
    showJuzProgressPercent: Boolean,
    showJuzProgressPages: Boolean,
    color: Color,
    accentColor: Color,
    big: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = buildString {
                append("الجزء ${juz.toArabicDigits()}")
                if (showJuzProgressPercent) append(" ${juzPercent.toArabicDigits()}٪")
            },
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1
        )
        if (showJuzProgressPages) {
            JuzRing(
                fraction = pageInJuz.toFloat() / pagesInJuz.coerceAtLeast(1),
                pageInJuz = pageInJuz,
                trackColor = color,
                accentColor = accentColor,
                big = big
            )
        }
    }
}

/** Compact donut gauge for the current position within the juz: a faint full-circle track
 *  with an accent arc swept by [fraction], drawn right→left over the top to mirror the paper
 *  mushaf's page flow, and the current page-in-juz number centered inside. */
@Composable
private fun JuzRing(
    fraction: Float,
    pageInJuz: Int,
    trackColor: Color,
    accentColor: Color,
    big: Boolean,
) {
    val ringSize = if (big) 24.dp else 21.dp
    // -90f = 12 o'clock start; negative sweep travels right→left over the top as the juz advances.
    // A small floor keeps the very first page reading as "started" rather than an empty ring.
    val sweep = -(fraction.coerceIn(0f, 1f) * 360f).coerceAtLeast(10f)
    Box(
        modifier = Modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 2.2.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor.copy(alpha = 0.20f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = pageInJuz.toArabicDigits(),
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MushafPageBadge(
    page: Int,
    onRightPage: Boolean,
    contentColor: Color,
    quietContentColor: Color,
    accentColor: Color,
    big: Boolean,
) {
    // Each leaf needs enough room for the widest mushaf page number (three digits).
    // Keep the number centred inside its own leaf so it never touches the spine or frame.
    val badgeWidth = if (big) 72.dp else 64.dp
    val badgeHeight = if (big) 36.dp else 31.dp
    val leafWidth = badgeWidth / 2
    val pageTextStyle = if (big) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier.size(width = badgeWidth, height = badgeHeight),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 1.35.dp.toPx()
                val radius = 4.5.dp.toPx()
                val centerX = size.width / 2f
                val pageTop = 1.5.dp.toPx()
                val pageHeight = size.height - pageTop * 2f
                val halfWidth = (size.width - stroke) / 2f
                val activeLeft = if (onRightPage) centerX else stroke / 2f

                drawRoundRect(
                    color = accentColor.copy(alpha = 0.13f),
                    topLeft = Offset(activeLeft, pageTop),
                    size = Size(halfWidth - stroke / 2f, pageHeight),
                    cornerRadius = CornerRadius(radius, radius)
                )
                drawRoundRect(
                    color = quietContentColor.copy(alpha = 0.46f),
                    topLeft = Offset(stroke / 2f, pageTop),
                    size = Size(size.width - stroke, pageHeight),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = stroke)
                )
                drawLine(
                    color = quietContentColor.copy(alpha = 0.62f),
                    start = Offset(centerX, pageTop + 1.dp.toPx()),
                    end = Offset(centerX, size.height - pageTop - 1.dp.toPx()),
                    strokeWidth = stroke
                )
                drawLine(
                    color = quietContentColor.copy(alpha = 0.22f),
                    start = Offset(centerX - 3.dp.toPx(), pageTop + 3.dp.toPx()),
                    end = Offset(centerX - 3.dp.toPx(), size.height - pageTop - 4.dp.toPx()),
                    strokeWidth = stroke / 1.6f
                )
                drawLine(
                    color = quietContentColor.copy(alpha = 0.22f),
                    start = Offset(centerX + 3.dp.toPx(), pageTop + 3.dp.toPx()),
                    end = Offset(centerX + 3.dp.toPx(), size.height - pageTop - 4.dp.toPx()),
                    strokeWidth = stroke / 1.6f
                )
            }
            Box(
                modifier = Modifier
                    .align(if (onRightPage) Alignment.CenterEnd else Alignment.CenterStart)
                    .width(leafWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = page.toArabicDigits(),
                    style = pageTextStyle,
                    color = contentColor,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Floating button shown while the header is hidden (full-screen reading): a small chip hugging the
 *  LEFT screen edge — a grabber notch over the current page number (red). Tap restores the header;
 *  the chip slides UP/DOWN along that edge and keeps the spot the user drags it to — across
 *  rotation, hiding/showing the header, and app restarts (persisted via [posFraction]). */
@Composable
private fun ShowHeaderButton(
    page: Int,
    showPage: Boolean,
    pageColorId: String,
    opacity: Float,
    posFraction: Float,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val numberColor = headerStatusColor(pageColorId, muted) // default "red"
    val buttonAlpha = opacity.coerceIn(0.25f, 1f)

    // Position in LTR so the offset math is plain top-left-origin pixels.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val maxHpx = constraints.maxHeight.toFloat()
            // Drag floor: keep the chip below the camera / status band.
            val topInsetPx = with(density) {
                WindowInsets.statusBars.union(WindowInsets.displayCutout)
                    .asPaddingValues().calculateTopPadding().toPx()
            }
            var btnSize by remember { mutableStateOf(IntSize.Zero) }
            // The chip stays flush to the LEFT edge (x = 0) and slides UP/DOWN only. Its spot comes
            // in as a 0..1 fraction of the travel below the top inset (-1 = untouched -> top), so
            // the saved position lands in the same relative place on any screen size/orientation.
            val maxY = (maxHpx - btnSize.height).coerceAtLeast(topInsetPx)
            val travel = maxY - topInsetPx
            val y = topInsetPx + posFraction.coerceIn(0f, 1f) * travel
            // Read inside the (long-lived) gesture coroutine so it sees the current values without
            // restarting the gesture — keying pointerInput on them would cancel the drag mid-move.
            val currentTravel by rememberUpdatedState(travel)
            val currentFraction by rememberUpdatedState(posFraction.coerceIn(0f, 1f))
            val currentOnDrag by rememberUpdatedState(onDrag)
            val currentOnDragEnd by rememberUpdatedState(onDragEnd)

            // Plain Surface + Modifier.clickable (NOT Surface(onClick=…)) so it does NOT get wrapped
            // in the 48.dp minimumInteractiveComponentSize, which would center the smaller visible
            // chip inside a 48.dp touch box and push it ~9.dp off the edge. This keeps it 100% flush.
            Surface(
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 11.dp, bottomEnd = 11.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f * buttonAlpha),
                contentColor = onSurface.copy(alpha = buttonAlpha),
                border = BorderStroke(1.dp, onSurface.copy(alpha = 0.12f * buttonAlpha)),
                shadowElevation = 4.dp * buttonAlpha,
                tonalElevation = if (buttonAlpha >= 0.85f) 1.dp else 0.dp,
                modifier = Modifier
                    .absoluteOffset { IntOffset(0, y.roundToInt()) }
                    .onSizeChanged { btnSize = it }
                    .pointerInput(Unit) {
                        // Accumulate the drag here rather than re-reading the fraction per event:
                        // several pointer events can land between recompositions, and reading a
                        // stale value would drop part of the movement.
                        var dragFraction = 0f
                        detectDragGestures(
                            onDragStart = { dragFraction = currentFraction },
                            onDragEnd = { currentOnDragEnd() },
                            onDragCancel = { currentOnDragEnd() },
                        ) { change, drag ->
                            change.consume()
                            if (currentTravel > 0f) {
                                dragFraction = (dragFraction + drag.y / currentTravel).coerceIn(0f, 1f)
                                currentOnDrag(dragFraction)
                            }
                        }
                    }
                    .clickable(onClick = onClick)
                    .defaultMinSize(minWidth = 30.dp, minHeight = 30.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .width(IntrinsicSize.Min) // hug the page number so the chip stays compact
                        .padding(start = 5.dp, end = 7.dp, top = 3.dp, bottom = 4.dp)
                ) {
                    // Small grabber notch — the "drag me up/down" cue.
                    Box(
                        modifier = Modifier
                            .size(width = 11.dp, height = 2.5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(onSurface.copy(alpha = 0.5f * buttonAlpha))
                    )
                    if (showPage) {
                        Text(
                            text = page.toArabicDigits(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = numberColor.copy(alpha = buttonAlpha)
                        )
                    }
                }
            }
        }
    }
}

/** A thin juz-progress bar pinned to the bottom of the page (optional, off by default).
 *  Fills from the right (mushaf reading direction) by the page's position within its juz.
 *  [thickness] is the user-chosen bar height, [opacity] how solid it is drawn (0..1). */
@Composable
private fun BottomJuzBar(
    modifier: Modifier,
    fraction: Float,
    colorId: String,
    thickness: Dp,
    opacity: Float,
) {
    val barColor = headerStatusColor(colorId, MaterialTheme.colorScheme.onSurfaceVariant)
    // Wrap in LTR so CenterEnd reliably means the right edge regardless of the screen's RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(thickness)
                .background(barColor.copy(alpha = TRACK_ALPHA * opacity)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(barColor.copy(alpha = opacity))
            )
        }
    }
}

/** A thin surah-progress bar laid out directly above the mushaf page (optional, off by default).
 *  Fills from the right, like [BottomJuzBar], by the page's position within the current surah.
 *  Unlike the juz bar it is part of the column rather than an overlay, so it never covers the page
 *  text. It intentionally does not pad for the top inset: in full-screen it hugs the very top edge
 *  and ignores the camera cutout, so the page fills the screen with no gap above the bar. */
@Composable
private fun TopSurahBar(
    fraction: Float,
    colorId: String,
    thickness: Dp,
    opacity: Float,
) {
    val barColor = headerStatusColor(colorId, MaterialTheme.colorScheme.onSurfaceVariant)
    // Wrap in LTR so CenterEnd reliably means the right edge regardless of the screen's RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thickness)
                .background(barColor.copy(alpha = TRACK_ALPHA * opacity)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(barColor.copy(alpha = opacity))
            )
        }
    }
}

/** The unread part of a progress bar is a faint wash of the same color; the user's opacity setting
 *  scales this too, so dialing a bar down fades the whole thing rather than just its filled part. */
private const val TRACK_ALPHA = 0.18f

/** Full-screen-only edge marker for the current page's side of the spread: a small rounded bar
 *  flush to the RIGHT edge for a right-side (odd) page, and to the LEFT edge for a left-side
 *  (even) page — matching the MushafPageBadge parity. Vertically centered. [thickness] is the bar's
 *  width, [length] its height, [opacity] how solid it is drawn (0..1); only the two corners facing
 *  away from the screen edge are rounded. */
@Composable
private fun PageSideIndicator(
    page: Int,
    colorId: String,
    thickness: Dp,
    length: Dp,
    opacity: Float,
) {
    val color = headerStatusColor(colorId, MaterialTheme.colorScheme.onSurfaceVariant)
    val onRight = page % 2 == 1 // odd pages sit on the right of the paper mushaf
    val radius = thickness / 2 // a pill cap at any thickness
    // Wrap in LTR so CenterStart/CenterEnd mean plain left/right regardless of the reader's RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (onRight) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(width = thickness, height = length)
                    .clip(
                        if (onRight) RoundedCornerShape(topStart = radius, bottomStart = radius)
                        else RoundedCornerShape(topEnd = radius, bottomEnd = radius)
                    )
                    .background(color.copy(alpha = opacity))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AyahExplanationSheet(
    request: ExplanationRequest,
    state: AyahExplanationUiState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember(request.ayah.verseKey, request.initialTab) {
        mutableStateOf(request.initialTab)
    }
    val currentState = state.takeIf { it.verseKey == request.ayah.verseKey }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = request.ayah.surahNameAr,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "الآية ${request.ayah.ayahNumber.toArabicDigits()} • صفحة ${request.ayah.page.toArabicDigits()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            MushafSegmentedTabs(
                labels = ExplanationTab.entries.map { it.title },
                selectedIndex = selectedTab.ordinal,
                onSelected = { selectedTab = ExplanationTab.entries[it] },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                currentState == null || currentState.loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                currentState.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            currentState.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = onRetry) { Text("إعادة المحاولة") }
                    }
                }

                selectedTab == ExplanationTab.Tafsir -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            val tafsir = currentState.tafsirHtml
                            if (tafsir.isNullOrBlank()) {
                                EmptyExplanationMessage("لا يتوفر تفسير لهذه الآية.")
                            } else {
                                Text(
                                    text = formatTafsirText(
                                        raw = tafsir,
                                        accent = MaterialTheme.colorScheme.primary,
                                    ),
                                    style = ReadingType.bodyLarge,
                                )
                            }
                        }
                        item {
                            SourceNote("المصدر: التفسير الميسر، الإصدار 3.0 — مجمع الملك فهد لطباعة المصحف الشريف.")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (currentState.meanings.isEmpty()) {
                            item { EmptyExplanationMessage("لا توجد كلمات غريبة مسجّلة لهذه الآية.") }
                        } else {
                            itemsIndexed(currentState.meanings) { _, meaning ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = meaning.word,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = meaning.meaning,
                                            modifier = Modifier.padding(top = 5.dp),
                                            style = ReadingType.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            SourceNote("المصدر: الميسر في غريب القرآن الكريم، الطبعة الثانية — مجمع الملك فهد لطباعة المصحف الشريف.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyExplanationMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SourceNote(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val TafsirAyahSpan = Regex(
    pattern = """<span\s+class=['\"]aya['\"]>(.*?)</span>""",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private fun formatTafsirText(raw: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    TafsirAyahSpan.findAll(raw).forEach { match ->
        append(raw.substring(cursor, match.range.first))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
            append(match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    append(raw.substring(cursor))
}

/** The single ayah menu. It is opened only by a long press and stays anchored to that press. */
@Composable
private fun AyahLongPressMenu(
    ayah: AyahMarker,
    anchor: Offset,
    boxWidthPx: Int,
    boxHeightPx: Int,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    showBookmark: Boolean,
    bookmarked2: Boolean,
    onBookmark2: () -> Unit,
    showBookmark2: Boolean,
    onTafsir: () -> Unit,
    onMeanings: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val reference = "﴿${ayah.surahNameAr} • آية ${ayah.ayahNumber.toArabicDigits()}﴾"
    val shareText = if (ayah.textUthmani.isNotBlank()) "${ayah.textUthmani}\n$reference" else reference

    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val margin = with(density) { 10.dp.toPx() }
    val gap = with(density) { 10.dp.toPx() }

    val x = (anchor.x - menuSize.width / 2f)
        .coerceIn(margin, (boxWidthPx - menuSize.width - margin).coerceAtLeast(margin))
    val above = anchor.y - menuSize.height - gap
    val y = (if (above >= margin) above else anchor.y + gap)
        .coerceIn(margin, (boxHeightPx - menuSize.height - margin).coerceAtLeast(margin))

    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val hasText = ayah.textUthmani.isNotBlank()

    Surface(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .onSizeChanged { menuSize = it }
            .widthIn(min = 280.dp, max = 320.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
        shadowElevation = 12.dp,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AyahMedallion(ayahNumber = ayah.ayahNumber, accent = accent, ornate = hasText)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = ayah.surahNameAr,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = ink,
                            maxLines = 1
                        )
                        if (ayah.isSajdah) SajdahBadge()
                    }
                    Text(
                        text = "الآية ${ayah.ayahNumber.toArabicDigits()} • صفحة ${ayah.page.toArabicDigits()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "إغلاق",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (hasText) {
                Text(
                    text = ayah.textUthmani,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 21.sp),
                    color = ink.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                text = "الجزء ${ayah.juz.toArabicDigits()} • الحزب ${ayah.hizb.toArabicDigits()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            MenuSectionLabel("استكشف الآية")
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReadingAction(
                    modifier = Modifier.weight(1f),
                    title = "التفسير",
                    subtitle = "التفسير الميسر",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    onClick = onTafsir,
                )
                ReadingAction(
                    modifier = Modifier.weight(1f),
                    title = "المعاني",
                    subtitle = "غريب القرآن",
                    icon = Icons.AutoMirrored.Outlined.ManageSearch,
                    onClick = onMeanings,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
            )

            MenuSectionLabel("إجراءات سريعة")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showBookmark) {
                    CompactAyahAction(
                        modifier = Modifier.weight(1f),
                        title = "علامة ١",
                        icon = if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        tint = BookmarkGoldColor,
                        selected = bookmarked,
                        onClick = onBookmark,
                    )
                }
                if (showBookmark2) {
                    CompactAyahAction(
                        modifier = Modifier.weight(1f),
                        title = "علامة ٢",
                        icon = if (bookmarked2) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        tint = BookmarkVioletColor,
                        selected = bookmarked2,
                        onClick = onBookmark2,
                    )
                }
                CompactAyahAction(
                    modifier = Modifier.weight(1f),
                    title = "نسخ",
                    icon = Icons.Filled.ContentCopy,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        clipboardScope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("آية قرآنية", shareText)),
                            )
                            Toast.makeText(context, "تم نسخ الآية", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                CompactAyahAction(
                    modifier = Modifier.weight(1f),
                    title = "مشاركة",
                    icon = Icons.Filled.Share,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuSectionLabel(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
    )
}

@Composable
private fun ReadingAction(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CompactAyahAction(
    modifier: Modifier,
    title: String,
    icon: ImageVector,
    tint: Color,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 54.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) tint.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        border = if (selected) BorderStroke(1.dp, tint.copy(alpha = 0.3f)) else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = if (selected) 0.13f else 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = tint,
                )
            }
            Text(
                text = title,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** Ornamental circular ayah-number medallion echoing the mushaf's printed ayah-end circles:
 *  two concentric green hairline rings + (when [ornate]) an 8-dot rosette, with the ayah
 *  number centered and Ltr-isolated so multi-digit numbers never bidi-flip. */
@Composable
private fun AyahMedallion(ayahNumber: Int, accent: Color, ornate: Boolean) {
    Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2f
            val ringStroke = 1.4.dp.toPx()
            drawCircle(color = accent, radius = outer - ringStroke, style = Stroke(ringStroke))
            val inner = outer - 6.dp.toPx()
            drawCircle(color = accent.copy(alpha = 0.35f), radius = inner, style = Stroke(1.dp.toPx()))
            if (ornate) {
                val petalR = (outer - ringStroke + inner) / 2f
                val dotR = 1.3.dp.toPx()
                repeat(8) { i ->
                    val a = (PI / 4.0) * i
                    drawCircle(
                        color = accent.copy(alpha = 0.22f),
                        radius = dotR,
                        center = Offset(
                            center.x + (petalR * cos(a)).toFloat(),
                            center.y + (petalR * sin(a)).toFloat()
                        )
                    )
                }
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = ayahNumber.toArabicDigits(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun GoToPageDialog(
    pageCount: Int,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val target = text.toIntOrNull()
    val valid = target != null && target in 1..pageCount
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الذهاب إلى صفحة") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() }.take(4) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { if (valid) target?.let(onGo) }),
                label = {
                    Text("رقم الصفحة (${1.toArabicDigits()} – ${pageCount.toArabicDigits()})")
                },
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onGo) }, enabled = valid) { Text("اذهب") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
internal fun SajdahBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = "۩ سجدة",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

fun Int.toArabicDigits(): String =
    toString().map { c -> if (c in '0'..'9') arabicDigits[c - '0'] else c }.joinToString("")

fun String.toArabicDigits(): String =
    map { c -> if (c in '0'..'9') arabicDigits[c - '0'] else c }.joinToString("")

/** Current wall-clock time as h:mm (12-hour) with a ص/م suffix, in Arabic digits. */
private fun formatClock(ms: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ms
    val h = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val m = cal.get(Calendar.MINUTE)
    val suffix = if (cal.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"
    return "${"$h:${"%02d".format(m)}".toArabicDigits()} $suffix"
}

/** Elapsed session time as m:ss (or h:mm:ss past an hour) in Arabic digits. */
private fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val str = if (h > 0) "$h:${"%02d".format(m)}:${"%02d".format(s)}" else "$m:${"%02d".format(s)}"
    return str.toArabicDigits()
}

private fun headerStatusColor(id: String, muted: Color): Color =
    when (id) {
        "red" -> StatusRedColor
        "green" -> StatusGreenColor
        "gold" -> StatusGoldColor
        "blue" -> StatusBlueColor
        else -> muted
    }
