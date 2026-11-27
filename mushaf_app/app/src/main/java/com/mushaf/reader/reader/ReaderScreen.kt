package com.mushaf.reader.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.recitation.AyahRef
import com.mushaf.reader.data.recitation.RepeatPlan
import com.mushaf.reader.update.AppUpdateUi
import com.mushaf.reader.update.UpdateBanner
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ReaderScreen(viewModel: ReaderViewModel, updates: AppUpdateUi? = null) {
    val pageCount = viewModel.pageCount.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = (viewModel.initialPage - 1).coerceIn(0, pageCount - 1),
        pageCount = { pageCount }
    )
    val scope = rememberCoroutineScope()
    // Pull-back from a curved screen edge. 0.dp unless the reader asks for it, so the default
    // layout is untouched.
    val edgeMargin = curvedEdgeMargin(viewModel.display.edgeMargin)
    val ui = rememberReaderScreenState()
    // What every bookmark menu (header bar, More menu, ayah card) reads and does.
    val bookmarkMenu = remember(viewModel) {
        BookmarkMenu(
            // Read where the menus are drawn, so hiding a bookmark updates them at once.
            slots = { viewModel.visibleBookmarkSlots },
            place = viewModel::bookmarkPlace,
            title = viewModel::bookmarkTitle,
            onJump = { slot ->
                viewModel.bookmarkJumpPage(slot)?.let { p ->
                    // Read the count now, not when this was remembered: it is set once the mushaf loads.
                    val last = viewModel.pageCount.coerceAtLeast(1) - 1
                    scope.launch { pagerState.scrollToPage((p - 1).coerceIn(0, last)) }
                }
            },
            onManage = { ui.showBookmarkManager = true },
        )
    }
    // Unfolding or folding a phone changes this, and with it which fill choice is in force.
    val wideScreen = ReadingStore.isWideScreen(LocalConfiguration.current.screenWidthDp)
    val fillScreen = viewModel.display.fillScreen(wideScreen)
    val recitation = viewModel.recitation
    val selected = viewModel.selectedAyah

    // Screens are overlays in this activity, so its lifecycle alone cannot time reading.
    // Focus also excludes system pickers, update dialogs and other windows covering the reader.
    val canRead = !ui.showStatsScreen && !ui.showKhatmaMap && !ui.showIndex && !ui.showSearch && !ui.showNotes &&
        ui.noteTarget == null &&
        !ui.showAbout && !ui.showSettings && !ui.showBackup && !ui.showGoTo && selected == null && ui.share == null &&
        ui.fawaedPage == null &&
        !viewModel.backupSync.backupUiState.busy && LocalWindowInfo.current.isWindowFocused
    LifecycleResumeEffect(viewModel, canRead) {
        viewModel.setReaderVisible(canRead)
        onPauseOrDispose { viewModel.setReaderVisible(false) }
    }
    // Reading sync: look for a newer place on another device when the app comes to the front,
    // and report this one's when it leaves.
    LifecycleStartEffect(viewModel) {
        viewModel.backupSync.onAppForeground()
        onStopOrDispose { viewModel.backupSync.onAppBackground() }
    }

    val jumpToPage: (Int) -> Unit = { page ->
        scope.launch { pagerState.scrollToPage((page - 1).coerceIn(0, pageCount - 1)) }
    }

    BackHandler(enabled = ui.showStatsScreen) { ui.showStatsScreen = false }
    // Registered after stats so Back from the map returns to the stats screen it opened from.
    BackHandler(enabled = ui.showKhatmaMap) { ui.showKhatmaMap = false }
    BackHandler(enabled = ui.showIndex) { ui.showIndex = false }
    BackHandler(enabled = ui.showSearch) { ui.showSearch = false }
    BackHandler(enabled = ui.showNotes) { ui.showNotes = false }
    BackHandler(enabled = ui.showSettings) { ui.showSettings = false }
    // The backup screen opens above Settings, so Back returns to the Settings app tab.
    BackHandler(enabled = ui.showBackup) { ui.showBackup = false }
    // Registered after index/settings so Back from About returns to the screen it was opened from.
    BackHandler(enabled = ui.showAbout) { ui.showAbout = false }
    // When the header is hidden, Back brings it back instead of leaving the app.
    BackHandler(enabled = !ui.showStatsScreen && !ui.showIndex && !ui.showSearch && !ui.showNotes && !ui.showAbout &&
        !ui.showSettings && !ui.showBackup && !ui.headerVisible
    ) {
        ui.headerVisible = true
    }

    LaunchedEffect(pagerState.currentPage) {
        // A selection made on another page is let go; one already on this page — the ayah the
        // explanation sheet just stepped to — is the reason the page turned, and stays.
        if (viewModel.selectedAyah?.page != pagerState.currentPage + 1) viewModel.clearSelection()
        viewModel.onPageVisible(pagerState.currentPage + 1)
        // Paging well away from an opened search result ends the offer to go back to the results.
        val back = ui.search.returnPage
        if (back != null && abs(pagerState.currentPage + 1 - back) > SearchReturnReach) {
            ui.search.returnPage = null
        }
    }

    // The page follows the recitation: when the ayah being recited moves onto another page, turn
    // to it — unless the reader turned that off, or is swiping right now.
    val recitingAyah = recitation.current
    LaunchedEffect(recitingAyah, viewModel.ayahLoaded, viewModel.recitationFollowPage) {
        if (!viewModel.recitationFollowPage) return@LaunchedEffect
        val page = recitingAyah?.let { viewModel.markerFor(it)?.page } ?: return@LaunchedEffect
        if (pagerState.currentPage != page - 1 && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage((page - 1).coerceIn(0, pageCount - 1))
        }
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

                if (ui.headerVisible) {
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
                        paletteId = viewModel.display.themeId,
                        hasBookmark = viewModel.visibleBookmarkSlots.any { viewModel.bookmarks[it] != null },
                        bookmarkMenu = bookmarkMenu,
                        fillScreen = fillScreen,
                        bigButtons = viewModel.display.bigButtons,
                        showClock = viewModel.display.showClock,
                        showSessionTimer = viewModel.display.showSessionTimer,
                        showSurahNumber = viewModel.display.showSurahNumber,
                        showSurahAyahCount = viewModel.display.showSurahAyahCount,
                        showSurahProgress = viewModel.display.showSurahProgress,
                        showJuzProgressPercent = viewModel.display.showJuzProgressPercent,
                        showJuzProgressPages = viewModel.display.showJuzProgressPages,
                        clockColor = viewModel.display.clockColor,
                        sessionTimerColor = viewModel.display.sessionTimerColor,
                        sessionStartedAt = viewModel.sessionStartedAt,
                        sessionDurationMs = viewModel::currentSessionDurationMs,
                        edgeMargin = edgeMargin,
                        isVisible = { id -> viewModel.display.isButtonVisible(id) },
                        isInBar = { id -> viewModel.display.isButtonInBar(id) },
                        buttonTint = { id, fallback ->
                            headerStatusColor(viewModel.display.buttonColor(id), fallback)
                        },
                        onBarOverflowChange = { ui.barOverflow = it },
                        onOpenSettings = { ui.showSettings = true },
                        onOpenIndex = { tab ->
                            ui.indexTab = tab
                            ui.showIndex = true
                        },
                        onOpenSearch = { ui.showSearch = true },
                        onToggleFillScreen = { viewModel.display.toggleFillScreen(wideScreen) },
                        onHideHeader = { ui.headerVisible = false },
                        onListen = {
                            viewModel.markersForPage(currentPage).firstOrNull()?.let {
                                recitation.playFrom(AyahRef(it.surahNumber, it.ayahNumber))
                            }
                        },
                        onSharePage = { ui.share = ShareSession(currentPage, ayah = null, viewModel.display.themeId) },
                        onPageFawaed = { ui.fawaedPage = currentPage },
                        onPaletteChange = { viewModel.display.updateThemeId(it) },
                        onOpenNotes = { ui.showNotes = true },
                        onStats = {
                            viewModel.openStats()
                            ui.showStatsScreen = true
                        },
                        onPageClick = { ui.showGoTo = true },
                        onAbout = { ui.showAbout = true }
                    )
                }

                // Optional thin surah-progress bar directly above the page. It always hugs the very
                // top of its slot — under the header when that shows, at the screen's top edge in
                // full-screen — and deliberately does NOT reserve the camera band, so the page fills
                // the screen right up to the top instead of leaving a gap (the bar simply sits behind
                // a punch-hole camera if one is there).
                if (viewModel.display.showTopSurahBar) {
                    TopSurahBar(
                        fraction = pageInfo
                            ?.let { viewModel.surahProgressPercent(currentPage, it.surahNumber) / 100f }
                            ?: 0f,
                        colorId = viewModel.display.topSurahBarColor,
                        thickness = viewModel.display.topSurahBarThickness.dp,
                        opacity = viewModel.display.topSurahBarOpacity / 100f
                    )
                }

                ReaderPager(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = edgeMargin),
                    viewModel = viewModel,
                    pagerState = pagerState,
                    selected = selected,
                    fillScreen = fillScreen,
                    verticalPaging = viewModel.display.verticalPaging,
                    readerVisible = canRead,
                    updates = updates,
                    bookmarkMenu = bookmarkMenu,
                    onEditNote = { ui.noteTarget = it },
                    onOpenRepeat = { ui.repeatFrom = it },
                    onShareAyah = { ui.share = ShareSession(it.page, it, viewModel.display.themeId) },
                )
            }

            // When the header is hidden, a small draggable button restores it (and shows the page).
            if (!ui.headerVisible) {
                ShowHeaderButton(
                    page = pagerState.currentPage + 1,
                    showPage = viewModel.display.showButtonPage,
                    pageColorId = viewModel.display.buttonPageColor,
                    opacity = viewModel.display.showHeaderButtonOpacity / 100f,
                    posFraction = viewModel.display.buttonPosFraction,
                    edgeMargin = edgeMargin,
                    onDrag = viewModel.display::dragButtonPosFraction,
                    onDragEnd = viewModel.display::saveButtonPosFraction,
                    onClick = { ui.headerVisible = true }
                )
            }

            // Full-screen only: a small edge bar marks whether the page sits on the right or left.
            if (!ui.headerVisible && viewModel.display.showPageSideIndicator) {
                PageSideIndicator(
                    page = pagerState.currentPage + 1,
                    colorId = viewModel.display.pageSideIndicatorColor,
                    thickness = viewModel.display.pageSideIndicatorThickness.dp,
                    length = viewModel.display.pageSideIndicatorLength.dp,
                    opacity = viewModel.display.pageSideIndicatorOpacity / 100f
                )
            }

            // Optional thin juz-progress bar pinned to the bottom of the page.
            if (viewModel.display.showBottomJuzBar) {
                val bbPage = pagerState.currentPage + 1
                val bbJuz = viewModel.juzInfoForPage(bbPage)
                BottomJuzBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    fraction = bbJuz.pageInJuz.toFloat() / bbJuz.pagesInJuz.coerceAtLeast(1),
                    colorId = viewModel.display.bottomJuzBarColor,
                    thickness = viewModel.display.bottomJuzBarThickness.dp,
                    opacity = viewModel.display.bottomJuzBarOpacity / 100f
                )
            }

            // Recitation: a drawer on the right edge, over the page, so the page never resizes.
            if (recitation.active) {
                FloatingRecitationBar(
                    surahName = recitingAyah?.let { viewModel.surahNameAr(it.surah) } ?: "",
                    ayah = recitingAyah?.ayah,
                    reciterName = recitation.reciter?.name,
                    isPlaying = recitation.isPlaying,
                    buffering = recitation.buffering,
                    error = recitation.error,
                    speed = recitation.speed,
                    repeatLabel = recitation.clip?.let { c ->
                        when {
                            // "٣ من ٧" reads unambiguously; a slash between Arabic digits does not.
                            c.ayahTimes > 1 ->
                                "${c.ayahPass.toArabicDigits()} من ${c.ayahTimes.toArabicDigits()}"
                            c.rangeTimes > 1 ->
                                "${c.rangePass.toArabicDigits()} من ${c.rangeTimes.toArabicDigits()}"
                            c.rangeTimes == RepeatPlan.ENDLESS -> "∞"
                            else -> null
                        }
                    },
                    startKey = recitation.startCount,
                    handlePos = viewModel.recitationHandlePos,
                    foldedOpacity = viewModel.recitationHandleOpacity / 100f,
                    onHandleDrag = viewModel::dragRecitationHandle,
                    onHandleDragEnd = viewModel::saveRecitationHandle,
                    onToggle = recitation::togglePlay,
                    onPrevious = recitation::previous,
                    onNext = recitation::next,
                    onClose = recitation::close,
                    onPickReciter = {
                        recitation.requestReciters()
                        ui.showReciterPicker = true
                    },
                    onSpeed = recitation::changeSpeed,
                    onRepeat = { ui.repeatFrom = recitingAyah },
                )
            }

            // Update nudge: drawn inside the page Box, before every full-screen overlay, so
            // opening the index or the stats screen hides it instead of stacking on top of them.
            // The bar reads the update state itself, so a download's progress ticks redraw the
            // bar alone instead of invalidating this whole scope on every percent.
            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Back to the search results this page was opened from, while the reader stays near it.
                if (ui.search.returnPage != null && !ui.showSearch) {
                    SearchReturnChip(
                        query = ui.search.answer.query,
                        onOpen = { ui.showSearch = true },
                        onDismiss = { ui.search.returnPage = null },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                    )
                }
                // A broken or stuck cloud feature: said plainly, never left to fail in silence.
                CloudProblemBanner(
                    problems = viewModel.backupSync.cloudProblems,
                    hidden = viewModel.backupSync.cloudProblemsHidden,
                    onRetry = viewModel.backupSync::retryCloudProblem,
                    onDetails = { ui.showBackup = true },
                    onLater = viewModel.backupSync::hideCloudProblems,
                )
                // A newer place on another device. Shown only while reading, like the update bar.
                SyncPlaceBanner(
                    place = viewModel.backupSync.syncUiState.elsewhere,
                    describeVerse = viewModel::describeVerse,
                    surahOfPage = viewModel::surahOfPage,
                    onGo = viewModel::goToSyncedPlace,
                    onDismiss = viewModel.backupSync::dismissElsewhere,
                )
                if (updates != null) UpdateBanner(updates = updates)
            }
        }

        ReaderOverlays(
            ui = ui,
            viewModel = viewModel,
            pagerState = pagerState,
            bookmarkMenu = bookmarkMenu,
            updates = updates,
            jumpToPage = jumpToPage,
        )
    }
}
