package com.mushaf.reader.reader

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.AyahMarker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

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
    // Registered after index/settings so Back from About returns to the screen it was opened from.
    BackHandler(enabled = showAbout) { showAbout = false }
    // When the header is hidden, Back brings it back instead of leaving the app.
    BackHandler(enabled = !showStatsScreen && !showIndex && !showSearch && !showAbout && !showSettings && !headerVisible) {
        headerVisible = true
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.clearSelection()
        viewModel.onPageVisible(pagerState.currentPage + 1)
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
                        onStats = {
                            viewModel.openStats()
                            showStatsScreen = true
                        },
                        onPageClick = { showGoTo = true }
                    )
                }

                ReaderPager(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    viewModel = viewModel,
                    pagerState = pagerState,
                    selected = selected,
                    fillScreen = viewModel.fillScreen
                )
            }

            // When the header is hidden, a small floating button restores it.
            if (!headerVisible) {
                ShowHeaderButton(
                    onClick = { headerVisible = true },
                    modifier = Modifier.align(Alignment.TopEnd)
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
                onBack = { showStatsScreen = false },
                onOpenKhatmaMap = { showKhatmaMap = true },
                onDeleteSessions = { viewModel.deleteSessions(it) }
            )
        }

        if (showKhatmaMap) {
            KhatmaMapScreen(
                totalPages = pageCount,
                readPages = viewModel.readPagesAll,
                visitedPages = viewModel.visitedPagesAll,
                bookmarkPage = viewModel.bookmarkJumpPage(),
                currentPage = pagerState.currentPage + 1,
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
                onAbout = { showAbout = true },
                onClearAllStats = { viewModel.clearAllStats() },
                onBack = { showSettings = false }
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

@Composable
private fun ReaderPager(
    modifier: Modifier,
    viewModel: ReaderViewModel,
    pagerState: PagerState,
    selected: AyahMarker?,
    fillScreen: Boolean,
) {
    var anchor by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = modifier) {
        val boxW = constraints.maxWidth
        val boxH = constraints.maxHeight

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { index ->
            val pageNumber = index + 1
            ZoomablePage(
                model = viewModel.assetModel(pageNumber),
                markers = viewModel.markersForPage(pageNumber),
                imageWidth = viewModel.imageWidth,
                imageHeight = viewModel.imageHeight,
                selectedAyah = selected,
                bookmarkedKeys = viewModel.bookmarks,
                onSelectAyah = { m, off ->
                    viewModel.selectAyah(if (viewModel.selectedAyah == m) null else m)
                    anchor = off
                },
                onEmptyTap = { },
                fillScreen = fillScreen
            )
        }

        if (selected != null) {
            AyahFloatingMenu(
                ayah = selected,
                anchor = anchor,
                boxWidthPx = boxW,
                boxHeightPx = boxH,
                bookmarked = viewModel.isBookmarked(selected.verseKey),
                onBookmark = { viewModel.toggleBookmark(selected) },
                onClose = { viewModel.clearSelection() }
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
    onStats: () -> Unit,
    onPageClick: () -> Unit,
) {
    val headerColor = if (dark) Color(0xFF202020) else MaterialTheme.colorScheme.surface
    val contentColor = if (dark) Color(0xFFEDEDED) else MaterialTheme.colorScheme.onSurface
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

    // Live ticker for the optional clock / current-session timer (only loops while one is shown).
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var showMoreMenu by remember { mutableStateOf(false) }
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
                    // Optional live wall-clock (red), shown right after the fill-screen button.
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
                    if (isVisible("hide")) IconButton(onClick = onHideHeader, modifier = Modifier.size(btnSize)) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "إخفاء الشريط العلوي وملء الشاشة",
                            tint = quietContentColor,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(btnSize)) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "المزيد",
                                tint = quietContentColor,
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
                                            tint = if (hasBookmark) LocalContentColor.current else quietContentColor
                                        )
                                    },
                                    enabled = hasBookmark,
                                    onClick = {
                                        showMoreMenu = false
                                        onBookmarkJump()
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
                            if (isVisible("fill")) {
                                DropdownMenuItem(
                                    text = { Text(if (fillScreen) "عرض الصفحة كاملة" else "ملء الصفحة") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (fillScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                            contentDescription = null,
                                            tint = if (fillScreen) activeContentColor else LocalContentColor.current
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onToggleFillScreen()
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
            }

            // Info row — surah identity (start) · page number (center) · juz position (end).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                if (surah != null) {
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
                        color = quietContentColor,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onOpenIndex(0) }
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onPageClick() }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isVisible("index")) {
                        val onRightPage = page % 2 == 0
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "إدخال رقم الصفحة",
                            tint = contentColor,
                            modifier = Modifier
                                .size(iconSize)
                                .graphicsLayer { scaleX = if (onRightPage) -1f else 1f }
                        )
                    }
                    Text(
                        text = page.toArabicDigits(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                Text(
                    text = buildString {
                        append("الجزء ${juz.toArabicDigits()}")
                        if (showJuzProgressPages) append(" (${pageInJuz.toArabicDigits()}/${pagesInJuz.toArabicDigits()})")
                        if (showJuzProgressPercent) append(" ${juzPercent.toArabicDigits()}٪")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = quietContentColor,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenIndex(1) }
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/** Small round floating button shown (top corner) while the header is hidden; restores it. */
@Composable
private fun ShowHeaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .padding(8.dp)
            .size(40.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "إظهار الشريط العلوي",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** Floating menu that appears over the page, anchored near the tapped position. */
@Composable
private fun AyahFloatingMenu(
    ayah: AyahMarker,
    anchor: Offset,
    boxWidthPx: Int,
    boxHeightPx: Int,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val reference = "﴿${ayah.surahNameAr} • آية ${ayah.ayahNumber.toArabicDigits()}﴾"
    val shareText = if (ayah.textUthmani.isNotBlank()) "${ayah.textUthmani}\n$reference" else reference

    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.toPx() }
    val gap = with(density) { 14.dp.toPx() }

    val x = (anchor.x - menuSize.width / 2f)
        .coerceIn(margin, (boxWidthPx - menuSize.width - margin).coerceAtLeast(margin))
    val above = anchor.y - menuSize.height - gap
    val y = (if (above >= margin) above else anchor.y + gap)
        .coerceIn(margin, (boxHeightPx - menuSize.height - margin).coerceAtLeast(margin))

    Surface(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .onSizeChanged { menuSize = it }
            .widthIn(max = 330.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "سورة ${ayah.surahNameAr} • آية ${ayah.ayahNumber.toArabicDigits()}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (ayah.isSajdah) SajdahBadge()
            }
            Text(
                text = "صفحة ${ayah.page.toArabicDigits()}  •  الجزء ${ayah.juz.toArabicDigits()}  •  الحزب ${ayah.hizb.toArabicDigits()}  •  الربع ${ayah.rub.toArabicDigits()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onBookmark, modifier = Modifier.size(42.dp)) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "علامة",
                        tint = if (bookmarked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(shareText))
                        Toast.makeText(context, "تم نسخ الآية", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "نسخ")
                }
                IconButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = "مشاركة")
                }
                IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "إغلاق")
                }
            }
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
        "red" -> Color(0xFFE53935)
        "green" -> Color(0xFF2E9E45)
        "gold" -> Color(0xFFC28A16)
        "blue" -> Color(0xFF2F6FE4)
        else -> muted
    }
