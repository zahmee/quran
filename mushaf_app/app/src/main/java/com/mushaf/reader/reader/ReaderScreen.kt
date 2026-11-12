package com.mushaf.reader.reader

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin as OutlinedPushPin
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mushaf.reader.update.AppUpdateUi
import com.mushaf.reader.update.UpdateBanner
import com.mushaf.reader.ui.theme.BookmarkGoldColor
import com.mushaf.reader.ui.theme.BookmarkVioletColor
import com.mushaf.reader.ui.theme.MushafPalettes
import com.mushaf.reader.ui.theme.ReadingType
import com.mushaf.reader.ui.theme.StatusBlueColor
import com.mushaf.reader.ui.theme.StatusGoldColor
import com.mushaf.reader.ui.theme.StatusGreenColor
import com.mushaf.reader.ui.theme.StatusRedColor
import com.mushaf.reader.ui.theme.pageRecolor
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.QuranPagePosition
import com.mushaf.reader.data.SurahAyahCounts
import com.mushaf.reader.data.ExplanationTextScale
import com.mushaf.reader.data.content.MukhtasarText
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.platform.LocalConfiguration
import com.mushaf.reader.data.content.SharedText
import com.mushaf.reader.data.packs.InstalledPack
import com.mushaf.reader.data.packs.PackContent
import com.mushaf.reader.data.packs.PackDownloads
import com.mushaf.reader.data.content.TafsirPassage
import com.mushaf.reader.data.content.SurahCard
import com.mushaf.reader.data.content.TafsirPassageRow
import com.mushaf.reader.data.content.TajweedNote
import com.mushaf.reader.data.content.TafsirRowKind
import com.mushaf.reader.data.recitation.AyahRef
import com.mushaf.reader.data.recitation.RepeatPlan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val edgeMargin = curvedEdgeMargin(viewModel.edgeMargin)
    var showGoTo by remember { mutableStateOf(false) }
    var showStatsScreen by remember { mutableStateOf(false) }
    var showKhatmaMap by remember { mutableStateOf(false) }
    var showIndex by remember { mutableStateOf(false) }
    var indexTab by remember { mutableStateOf(0) }
    var showSearch by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showTafsirLibrary by remember { mutableStateOf(false) }
    var headerVisible by remember { mutableStateOf(true) }
    var showReciterPicker by remember { mutableStateOf(false) }
    // The ayah the memorisation sheet starts from, while it is open.
    var repeatFrom by remember { mutableStateOf<AyahRef?>(null) }
    // The share-as-image sheet's choices, while it (or its ayah picker) is open.
    var share by remember { mutableStateOf<ShareSession?>(null) }
    // The page whose fawaed are open from the header, and the surah whose card is open from the index.
    var fawaedPage by remember { mutableStateOf<Int?>(null) }
    var surahCardFor by remember { mutableStateOf<SurahEntry?>(null) }
    val recitation = viewModel.recitation
    // Bar-placed header actions the current window has no room for; settings flags them.
    var barOverflow by remember { mutableStateOf(emptySet<String>()) }
    val selected = viewModel.selectedAyah

    // Screens are overlays in this activity, so its lifecycle alone cannot time reading.
    // Focus also excludes system pickers, update dialogs and other windows covering the reader.
    val canRead = !showStatsScreen && !showKhatmaMap && !showIndex && !showSearch &&
        !showAbout && !showSettings && !showBackup && !showGoTo && selected == null && share == null &&
        fawaedPage == null &&
        !viewModel.backupUiState.busy && LocalWindowInfo.current.isWindowFocused
    LifecycleResumeEffect(viewModel, canRead) {
        viewModel.setReaderVisible(canRead)
        onPauseOrDispose { viewModel.setReaderVisible(false) }
    }

    val jumpToPage: (Int) -> Unit = { page ->
        scope.launch { pagerState.scrollToPage((page - 1).coerceIn(0, pageCount - 1)) }
    }

    BackHandler(enabled = showStatsScreen) { showStatsScreen = false }
    // Registered after stats so Back from the map returns to the stats screen it opened from.
    BackHandler(enabled = showKhatmaMap) { showKhatmaMap = false }
    BackHandler(enabled = showIndex) { showIndex = false }
    BackHandler(enabled = showSearch) { showSearch = false }
    BackHandler(enabled = showSettings) { showSettings = false }
    // The backup screen opens above Settings, so Back returns to the Settings app tab.
    BackHandler(enabled = showBackup) { showBackup = false }
    // Registered after index/settings so Back from About returns to the screen it was opened from.
    BackHandler(enabled = showAbout) { showAbout = false }
    // When the header is hidden, Back brings it back instead of leaving the app.
    BackHandler(enabled = !showStatsScreen && !showIndex && !showSearch && !showAbout &&
        !showSettings && !showBackup && !headerVisible
    ) {
        headerVisible = true
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.clearSelection()
        viewModel.onPageVisible(pagerState.currentPage + 1)
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
                        paletteId = viewModel.themeId,
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
                        sessionDurationMs = viewModel::currentSessionDurationMs,
                        edgeMargin = edgeMargin,
                        isVisible = { id -> viewModel.isButtonVisible(id) },
                        isInBar = { id -> viewModel.isButtonInBar(id) },
                        buttonTint = { id, fallback ->
                            headerStatusColor(viewModel.buttonColor(id), fallback)
                        },
                        onBarOverflowChange = { barOverflow = it },
                        onOpenSettings = { showSettings = true },
                        onOpenIndex = { tab ->
                            indexTab = tab
                            showIndex = true
                        },
                        onOpenSearch = { showSearch = true },
                        onToggleFillScreen = { viewModel.toggleFillScreen() },
                        onHideHeader = { headerVisible = false },
                        onListen = {
                            viewModel.markersForPage(currentPage).firstOrNull()?.let {
                                recitation.playFrom(AyahRef(it.surahNumber, it.ayahNumber))
                            }
                        },
                        onSharePage = { share = ShareSession(currentPage, ayah = null, viewModel.themeId) },
                        onPageFawaed = { fawaedPage = currentPage },
                        onPaletteChange = { viewModel.updateThemeId(it) },
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
                        onPageClick = { showGoTo = true },
                        onAbout = { showAbout = true }
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
                        .weight(1f)
                        .padding(horizontal = edgeMargin),
                    viewModel = viewModel,
                    pagerState = pagerState,
                    selected = selected,
                    fillScreen = viewModel.fillScreen,
                    verticalPaging = viewModel.verticalPaging,
                    readerVisible = canRead,
                    updates = updates,
                    onOpenRepeat = { repeatFrom = it },
                    onShareAyah = { share = ShareSession(it.page, it, viewModel.themeId) },
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
                    edgeMargin = edgeMargin,
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
                        showReciterPicker = true
                    },
                    onSpeed = recitation::changeSpeed,
                    onRepeat = { repeatFrom = recitingAyah },
                )
            }

            // Update nudge: drawn inside the page Box, before every full-screen overlay, so
            // opening the index or the stats screen hides it instead of stacking on top of them.
            // The bar reads the update state itself, so a download's progress ticks redraw the
            // bar alone instead of invalidating this whole scope on every percent.
            if (updates != null) {
                UpdateBanner(
                    updates = updates,
                    modifier = Modifier.align(Alignment.BottomCenter),
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

        repeatFrom?.let { from ->
            val page = viewModel.markerFor(from)?.page
            val pageLastAyah = page?.let { p ->
                viewModel.markersForPage(p).filter { it.surahNumber == from.surah }
                    .maxOfOrNull { it.ayahNumber }
            }
            val (ayahTimes, rangeTimes) = recitation.repeatDefaults
            RepeatSheet(
                surahName = viewModel.surahNameAr(from.surah),
                surahAyahCount = viewModel.surahAyahCount(from.surah),
                fromAyah = from.ayah,
                pageLastAyah = pageLastAyah,
                initialAyahTimes = ayahTimes,
                initialRangeTimes = rangeTimes,
                onStart = { toAyah, each, whole ->
                    repeatFrom = null
                    recitation.playPlan(RepeatPlan(from.surah, from.ayah, toAyah, each, whole))
                },
                onDismiss = { repeatFrom = null },
            )
        }

        share?.let { session ->
            val initialRun = session.initialRun(viewModel.markersForPage(session.page))
            if (session.picking && initialRun.isNotEmpty()) {
                AyahRangePicker(
                    pageCount = pageCount,
                    model = viewModel::assetModel,
                    markersFor = viewModel::markersForPage,
                    recolor = remember(viewModel.palette) { viewModel.palette.pageRecolor() },
                    palette = viewModel.palette,
                    imageWidth = viewModel.imageWidth,
                    imageHeight = viewModel.imageHeight,
                    initial = initialRun,
                    onDone = { run ->
                        session.run = run
                        session.kind = ShareKind.Span
                        session.picking = false
                    },
                    onCancel = { session.picking = false },
                )
            } else {
                ShareImageSheet(
                    session = session,
                    markersFor = viewModel::markersForPage,
                    recitation = RecitationAccess(
                        reciter = recitation.reciter,
                        prepare = recitation::prepareReciter,
                        pick = {
                            recitation.requestReciters()
                            showReciterPicker = true
                        },
                        timing = recitation::timing,
                    ),
                    onDismiss = {
                        share = null
                        viewModel.clearSelection()
                    },
                )
            }
        }

        if (showReciterPicker) {
            ReciterPickerSheet(
                reciters = recitation.reciters,
                selectedId = recitation.reciter?.id,
                loading = recitation.listLoading,
                error = recitation.error,
                onRetry = recitation::requestReciters,
                onPick = { choice ->
                    showReciterPicker = false
                    recitation.selectReciter(choice)
                },
                onDismiss = { showReciterPicker = false },
            )
        }

        if (showStatsScreen) {
            StatsScreen(
                stats = viewModel.fullStats,
                sessions = viewModel.sessions,
                khatmas = viewModel.khatmas,
                khatmaStartedAt = viewModel.khatmaStartedAt,
                onCompleteKhatma = { viewModel.completeKhatma(); showStatsScreen = false },
                onResetKhatma = { viewModel.resetKhatmaProgress(); showStatsScreen = false },
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
                onBack = { showIndex = false },
                onSurahInfo = { surahCardFor = it },
            )
        }

        fawaedPage?.let { page ->
            PageFawaedSheet(
                page = page,
                surahName = remember(page, viewModel.ayahLoaded) {
                    viewModel.surahIndex().lastOrNull { it.firstPage <= page }?.nameAr
                },
                load = viewModel::pageFawaed,
                textScale = viewModel.explanationTextScale,
                onTextScaleStep = viewModel::stepExplanationTextScale,
                onDismiss = { fawaedPage = null },
            )
        }

        surahCardFor?.let { surah ->
            SurahCardSheet(
                surah = surah,
                load = viewModel::surahCard,
                textScale = viewModel.explanationTextScale,
                onTextScaleStep = viewModel::stepExplanationTextScale,
                onOpenSurah = {
                    surahCardFor = null
                    showIndex = false
                    jumpToPage(surah.firstPage)
                },
                onDismiss = { surahCardFor = null },
            )
        }

        if (showSettings) {
            SettingsScreen(
                isVisible = { id -> viewModel.isButtonVisible(id) },
                onToggle = { id, v -> viewModel.setButtonVisible(id, v) },
                paletteId = viewModel.themeId,
                onPaletteChange = { viewModel.updateThemeId(it) },
                isInBar = { id -> viewModel.isButtonInBar(id) },
                onSetInBar = { id, v -> viewModel.setButtonInBar(id, v) },
                barOverflow = barOverflow,
                buttonColor = { id -> viewModel.buttonColor(id) },
                onButtonColorChange = { id, c -> viewModel.updateButtonColor(id, c) },
                bigButtons = viewModel.bigButtons,
                onBigButtonsChange = { viewModel.updateBigButtons(it) },
                verticalPaging = viewModel.verticalPaging,
                onVerticalPagingChange = { viewModel.updateVerticalPaging(it) },
                keepScreenOn = viewModel.keepScreenOn,
                onKeepScreenOnChange = { viewModel.updateKeepScreenOn(it) },
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
                showReadingPosition = viewModel.showReadingPosition,
                onShowReadingPositionChange = viewModel::updateShowReadingPosition,
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
                edgeMargin = viewModel.edgeMargin,
                onEdgeMarginChange = { viewModel.updateEdgeMargin(it) },
                onBackup = { showBackup = true },
                onAbout = { showAbout = true },
                onTafsirLibrary = { showTafsirLibrary = true },
                onClearAllStats = { viewModel.clearAllStats() },
                recitationTab = {
                    val page = pagerState.currentPage + 1
                    val juz = viewModel.juzInfoForPage(page).juz
                    val juzSurahs = remember(juz, viewModel.ayahLoaded) { viewModel.surahsInJuz(juz) }
                    val allSurahs = remember(viewModel.ayahLoaded) { viewModel.surahIndex() }
                    RecitationSettingsTab(
                        recitation = recitation,
                        downloads = viewModel.recitationDownloads,
                        currentSurah = viewModel.markersForPage(page).firstOrNull()?.surahNumber,
                        currentJuz = juz,
                        juzSurahs = juzSurahs,
                        surahs = allSurahs,
                        surahName = viewModel::surahNameAr,
                        followPage = viewModel.recitationFollowPage,
                        onFollowPageChange = viewModel::updateRecitationFollowPage,
                        handleOpacity = viewModel.recitationHandleOpacity,
                        onHandleOpacityChange = viewModel::updateRecitationHandleOpacity,
                        mobileDataPolicy = viewModel.mobileDataPolicy,
                        onMobileDataPolicyChange = viewModel::updateMobileDataPolicy,
                        onPickReciter = {
                            recitation.requestReciters()
                            showReciterPicker = true
                        },
                    )
                },
                onBack = { showSettings = false }
            )
        }

        // First time recitation would stream on mobile data: ask once, remember the answer.
        if (recitation.mobileDataPrompt) {
            AlertDialog(
                onDismissRequest = {
                    recitation.answerMobileData(null)
                },
                title = { Text("بيانات الجوال") },
                text = {
                    Text(
                        "أنت متصل ببيانات الجوال. تستهلك التلاوة نحو ميغابايت في الدقيقة، " +
                            "وما سُمع من قبل لا يُحمَّل مرة أخرى. يمكنك تغيير هذا من الإعدادات."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        recitation.answerMobileData(true)
                        viewModel.refreshMobileDataPolicy()
                    }) { Text("استخدم بيانات الجوال") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        recitation.answerMobileData(false)
                        viewModel.refreshMobileDataPolicy()
                    }) { Text("الشبكة اللاسلكية فقط") }
                },
            )
        }

        if (showBackup) {
            BackupScreen(
                viewModel = viewModel,
                onBack = { showBackup = false },
            )
        }

        if (showTafsirLibrary) {
            TafsirLibraryDialog(downloads = viewModel.packDownloads, onDismiss = { showTafsirLibrary = false })
        }

        if (showAbout) {
            AboutScreen(
                onBack = { showAbout = false },
                updates = updates,
                // Backup renders below About in this stack, so About has to step aside for it.
                onOpenBackup = {
                    showAbout = false
                    showBackup = true
                },
            )
        }

        if (showSearch) {
            SearchScreen(
                onSearch = { q -> viewModel.search(q) },
                onJump = { page ->
                    showSearch = false
                    jumpToPage(page)
                },
                onJumpToAyah = { result ->
                    showSearch = false
                    jumpToPage(result.page)
                    viewModel.flashSearchResult(result.verseKey, result.page)
                },
                onBack = { showSearch = false },
                onRootResults = viewModel::rootResults,
            )
        }
    }
}

/**
 * What the sheet lays out for a source. [menuLabel] names it, above its text and in the sources
 * menu; [title] heads anything copied or shared from it; [credit] closes that.
 */
private enum class ExplanationTab(
    val menuLabel: String,
    val title: String,
    val source: String?,
    val credit: String,
) {
    Saadi("تفسير السعدي", "تفسير السعدي", ExplanationSources.SAADI, CenterAttribution.SAADI_CREDIT),
    Mukhtasar(
        "المختصر في التفسير",
        "المختصر في تفسير القرآن الكريم",
        ExplanationSources.MUKHTASAR,
        CenterAttribution.MUKHTASAR_CREDIT,
    ),
    Muyassar("التفسير الميسر", "التفسير الميسر", ExplanationSources.MUYASSAR, MUYASSAR_CREDIT),
    Meanings("معاني الكلمات", "معاني الكلمات", ExplanationSources.MEANINGS, GHARIB_CREDIT),
    Tajweed("التجويد", "أحكام التجويد", ExplanationSources.TAJWEED, CenterAttribution.TAJWEED_CREDIT),
    PageFawaed("فوائد الصفحة", "فوائد الصفحة", ExplanationSources.PAGE_FAWAED, CenterAttribution.FAWAED_CREDIT),
    AboutSurah("عن السورة", "عن السورة", ExplanationSources.SURAH_CARD, CenterAttribution.SURAH_CARD_CREDIT),

    /** A downloaded pack. Its name, title and credit are the pack's own, from the pack file. */
    Pack("", "", null, ""),
    ;

    companion object {
        fun forSource(source: String): ExplanationTab =
            if (ExplanationSources.packId(source) != null) Pack
            else entries.firstOrNull { it.source == source } ?: Saadi
    }
}

private data class ExplanationRequest(
    val ayah: AyahMarker,
    /** An [ExplanationSources] id. */
    val initialSource: String,
)

@Composable
private fun ReaderPager(
    modifier: Modifier,
    viewModel: ReaderViewModel,
    pagerState: PagerState,
    selected: AyahMarker?,
    fillScreen: Boolean,
    verticalPaging: Boolean,
    readerVisible: Boolean,
    updates: AppUpdateUi?,
    onOpenRepeat: (AyahRef) -> Unit,
    onShareAyah: (AyahMarker) -> Unit,
) {
    // The long-pressed ayah's bounds on screen, so its menu can sit clear of it.
    var ayahBounds by remember { mutableStateOf(Rect.Zero) }
    var longPressAyah by remember { mutableStateOf<AyahMarker?>(null) }
    var explanationRequest by remember { mutableStateOf<ExplanationRequest?>(null) }

    LaunchedEffect(selected) {
        if (selected == null) {
            longPressAyah = null
        }
    }

    val flash = viewModel.searchFlash
    // Any touch on the page puts out a search result's light at once; it is only there to be found.
    val flashTouch = if (flash == null) Modifier else Modifier.pointerInput(flash.token) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            viewModel.clearSearchFlash()
        }
    }

    BoxWithConstraints(modifier = modifier.then(flashTouch)) {
        val boxW = constraints.maxWidth
        val boxH = constraints.maxHeight
        val recolor = remember(viewModel.palette) { viewModel.palette.pageRecolor() }
        val flashMarker = flash?.let { f ->
            viewModel.markersForPage(f.page).firstOrNull { it.verseKey == f.verseKey }
        }
        val settledPage = pagerState.settledPage + 1
        val position = remember(settledPage, viewModel.ayahLoaded) {
            QuranPagePosition.fromAyahs(viewModel.markersForPage(settledPage))
        }
        val hizbMarkers = viewModel.hizbMarkersForPage(settledPage)
        val recitingMarker = viewModel.recitation.current?.let { viewModel.markerFor(it) }
        val positionAllowed = readingPositionAllowed(
            enabled = viewModel.showReadingPosition,
            readerVisible = readerVisible && longPressAyah == null && explanationRequest == null,
            scrolling = pagerState.isScrollInProgress,
            updates = updates,
        )

        // Same page content for both orientations; only the pager axis differs.
        val pageContent: @Composable PagerScope.(Int) -> Unit = { index ->
            val pageNumber = index + 1
            MushafPage(
                model = viewModel.assetModel(pageNumber),
                recolor = recolor,
                palette = viewModel.palette,
                markers = viewModel.markersForPage(pageNumber),
                imageWidth = viewModel.imageWidth,
                imageHeight = viewModel.imageHeight,
                selectedAyah = selected,
                bookmarkedKeys = if (viewModel.isButtonVisible("bookmark")) viewModel.bookmarks else emptySet(),
                bookmarkedKeys2 = if (viewModel.isButtonVisible("bookmark2")) viewModel.bookmarks2 else emptySet(),
                onLongPressAyah = { m, _, bounds ->
                    viewModel.selectAyah(m)
                    ayahBounds = bounds
                    longPressAyah = m
                },
                fillScreen = fillScreen,
                pageOverlay = { scale, viewportTop, viewportHeight ->
                    if (recitingMarker != null && recitingMarker.page == pageNumber) {
                        RecitationHighlight(marker = recitingMarker, baseScale = scale)
                    }
                    if (flash != null && flashMarker != null && flashMarker.page == pageNumber) {
                        SearchFlashHighlight(
                            marker = flashMarker,
                            baseScale = scale,
                            token = flash.token,
                            onDone = viewModel::clearSearchFlash,
                        )
                    }
                    if (pageNumber == settledPage && hizbMarkers.isNotEmpty()) {
                        HizbMarkerLabels(
                            markers = hizbMarkers,
                            baseScale = scale,
                            viewportTop = viewportTop,
                            viewportHeight = viewportHeight,
                            allowed = positionAllowed,
                            palette = viewModel.palette,
                        )
                    }
                },
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

        // Match the fitted image's bottom edge. This is an overlay sibling of the pager:
        // it never reserves height or changes the page's scale, even in whole-page mode.
        val imageHeight = viewModel.imageHeight.coerceAtLeast(1)
        val imageWidth = viewModel.imageWidth.coerceAtLeast(1)
        val fittedHeight = minOf(maxHeight.value, maxWidth.value * imageHeight / imageWidth).dp
        val imageBottomMargin = if (fillScreen) 0.dp else (maxHeight - fittedHeight) / 2
        ReadingPositionOverlay(
            page = settledPage,
            position = position,
            allowed = positionAllowed && hizbMarkers.isEmpty(),
            palette = viewModel.palette,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = imageBottomMargin)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        )

        longPressAyah?.let { ayah ->
            // The page's own width: the whole box, except a whole-page fit narrower than the screen.
            val pageWidthPx = if (fillScreen) boxW
            else minOf(boxW, (boxH.toFloat() * imageWidth / imageHeight).roundToInt())
            AyahLongPressMenu(
                ayah = ayah,
                ayahBounds = ayahBounds,
                boxWidthPx = boxW,
                boxHeightPx = boxH,
                pageWidthPx = pageWidthPx,
                bookmarked = viewModel.isBookmarked(ayah.verseKey),
                onBookmark = { viewModel.toggleBookmark(ayah) },
                showBookmark = viewModel.isButtonVisible("bookmark"),
                bookmarked2 = viewModel.isBookmarked2(ayah.verseKey),
                onBookmark2 = { viewModel.toggleBookmark2(ayah) },
                showBookmark2 = viewModel.isButtonVisible("bookmark2"),
                onExplain = {
                    longPressAyah = null
                    viewModel.loadExplanation(ayah)
                    explanationRequest = ExplanationRequest(ayah, viewModel.openingSource())
                },
                onListenAyah = {
                    longPressAyah = null
                    viewModel.clearSelection()
                    viewModel.recitation.listenTo(AyahRef(ayah.surahNumber, ayah.ayahNumber))
                },
                onPlayFrom = {
                    longPressAyah = null
                    viewModel.clearSelection()
                    viewModel.recitation.playFrom(AyahRef(ayah.surahNumber, ayah.ayahNumber))
                },
                onRepeat = {
                    longPressAyah = null
                    viewModel.clearSelection()
                    onOpenRepeat(AyahRef(ayah.surahNumber, ayah.ayahNumber))
                },
                // The ayah stays highlighted behind the sheet; the sheet clears it on close.
                onShare = {
                    longPressAyah = null
                    onShareAyah(ayah)
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
                onSourceChosen = viewModel::updateExplanationSource,
                pinnedSources = viewModel.pinnedSources,
                onTogglePin = viewModel::togglePinnedSource,
                textScale = viewModel.explanationTextScale,
                onTextScaleStep = viewModel::stepExplanationTextScale,
                packs = viewModel.installedPacks,
                packState = viewModel.packState,
                onLoadPack = viewModel::loadPackContent,
                downloads = viewModel.packDownloads,
                loadFawaed = viewModel::pageFawaed,
                loadSurahCard = viewModel::surahCard,
                loadTajweed = viewModel::tajweed,
                onDismiss = {
                    explanationRequest = null
                    viewModel.clearExplanation()
                    viewModel.clearSelection()
                },
            )
        }
    }
}

/** Widths (px) the header's fixed pieces have been laid out at; 0 until first measured. */
@Stable
private class HeaderPieceWidths {
    var page by mutableIntStateOf(0)
    var juz by mutableIntStateOf(0)
    var timer by mutableIntStateOf(0)
    /** Two-row layout only: the settings · clock corner. */
    var start by mutableIntStateOf(0)
}

/** Tells the reader screen which bar-placed actions this layout had to list in More instead. */
@Composable
private fun ReportBarOverflow(
    requested: List<String>,
    visible: Set<String>,
    onChange: (Set<String>) -> Unit,
) {
    val overflow = requested.filterNotTo(LinkedHashSet()) { it in visible }
    val latest by rememberUpdatedState(onChange)
    LaunchedEffect(overflow) { latest(overflow) }
}

/** Extra room the single-row header gives each physical side to step past a camera hole. */
private data class CutoutClearance(val left: Dp, val right: Dp)

/**
 * Clearance for a punch-hole that falls inside the top [bandHeight] and sits in the outer third
 * of the window: that side is padded to just past the hole, minus whatever the horizontal cutout
 * inset already reserves there. A hole near the middle is left alone.
 */
@Composable
private fun cornerCutoutClearance(bandHeight: Dp): CutoutClearance {
    val density = LocalDensity.current
    // Reading the insets subscribes to cutout changes (fold, rotation) so the rects stay current.
    val cutout = WindowInsets.displayCutout
    val insetLeft = cutout.getLeft(density, LayoutDirection.Ltr)
    val insetRight = cutout.getRight(density, LayoutDirection.Ltr)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return CutoutClearance(0.dp, 0.dp)
    val rects = LocalView.current.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
    val width = LocalWindowInfo.current.containerSize.width
    val bandPx = with(density) { bandHeight.roundToPx() }
    var left = 0
    var right = 0
    for (rect in rects) {
        if (rect.top >= bandPx || rect.isEmpty) continue
        when {
            rect.centerX() < width / 3 -> left = maxOf(left, rect.right)
            rect.centerX() > width * 2 / 3 -> right = maxOf(right, width - rect.left)
        }
    }
    return with(density) {
        CutoutClearance(
            left = (left - insetLeft).coerceAtLeast(0).toDp(),
            right = (right - insetRight).coerceAtLeast(0).toDp(),
        )
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
    paletteId: String,
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
    sessionDurationMs: () -> Long,
    /** Extra pull-back from the screen's side edges, for curved corners. 0.dp by default. */
    edgeMargin: Dp,
    isVisible: (String) -> Boolean,
    isInBar: (String) -> Boolean,
    /** (action id, the tint it would have had) -> the color the user picked for it. */
    buttonTint: (String, Color) -> Color,
    /** Bar-placed actions this window has no room for (they are listed in More instead). */
    onBarOverflowChange: (Set<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenIndex: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onToggleFillScreen: () -> Unit,
    onHideHeader: () -> Unit,
    onListen: () -> Unit,
    onSharePage: () -> Unit,
    onPageFawaed: () -> Unit,
    onPaletteChange: (String) -> Unit,
    onBookmarkJump: () -> Unit,
    onBookmark2Jump: () -> Unit,
    onStats: () -> Unit,
    onPageClick: () -> Unit,
    onAbout: () -> Unit,
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
    val windowSize = LocalWindowInfo.current.containerSize
    val windowWidth = with(LocalDensity.current) { windowSize.width.toDp() }
    val singleRow = windowSize.width > windowSize.height || windowWidth >= 600.dp

    // A placement preference means "keep this action on the bar when there is room". Each layout
    // below works out the room its fixed pieces really leave and fits the requested actions into it
    // with fitBarActions (fill/hide/bookmark keep their seats first); the rest are listed in More,
    // and that overflow is reported so the settings screen can say so rather than look unsaved.
    val requestedBarActions = HeaderActions.filter { isVisible(it.id) && isInBar(it.id) }.map { it.id }
    val showTimer = showSessionTimer && sessionStartedAt > 0L
    // Widest each fixed piece has been laid out at. Only growing keeps a ticking timer or a page
    // turn from bouncing a button between the bar and More; it starts over when the content changes.
    val pieces = remember(
        singleRow, bigButtons, showClock, showTimer, showJuzProgressPercent, showJuzProgressPages
    ) { HeaderPieceWidths() }
    val density = LocalDensity.current
    // A piece not laid out yet (the very first frame) counts at a conservative estimate.
    fun Int.orDp(estimate: Dp): Dp = if (this > 0) with(density) { this@orDp.toDp() } else estimate

    // Live ticker for the optional clock / current-session timer (only loops while one is shown).
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(showClock, showSessionTimer) {
        while (showClock || showSessionTimer) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val readingDurationMs = remember(nowMs, sessionStartedAt, sessionDurationMs) { sessionDurationMs() }

    Surface(
        color = headerColor,
        contentColor = contentColor,
        shadowElevation = 1.dp
    ) {
        if (singleRow) {
            // One-row header for wide screens. It sits in the camera band itself rather than below
            // it, so an unfolded foldable shows a single line with the punch-hole inside it. A hole
            // near either end pushes that end inward so no control lands under the lens; a side
            // notch in landscape is kept clear by the horizontal inset.
            val cornerHole = cornerCutoutClearance(bandHeight = btnSize + 4.dp)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .absolutePadding(left = cornerHole.left, right = cornerHole.right)
                    .padding(horizontal = 8.dp + edgeMargin, vertical = 2.dp)
                    .height(btnSize)
            ) {
                // The end group may grow toward the centred page badge but never under it. Its
                // fixed pieces (juz chip, timer, the always-present More) are measured, and every
                // bar action costs one button plus the row's spacing.
                val gap = 4.dp
                val endRoom = maxWidth / 2 - pieces.page.orDp(btnSize * 2) / 2 - gap * 2
                val actionRoom = endRoom - pieces.juz.orDp(112.dp) - gap -
                    (if (showTimer) pieces.timer.orDp(72.dp) + gap else 0.dp) - btnSize
                val visibleBarActionIds = fitBarActions(
                    requestedBarActions, barSlots(actionRoom.value, (btnSize + gap).value)
                )
                ReportBarOverflow(requestedBarActions, visibleBarActionIds, onBarOverflowChange)
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
                // Center: page badge.
                PageChip(
                    page = page,
                    contentColor = contentColor,
                    quietContentColor = quietContentColor,
                    accentColor = activeContentColor,
                    big = bigButtons,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .onSizeChanged { if (it.width > pieces.page) pieces.page = it.width },
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
                        modifier = Modifier.onSizeChanged { if (it.width > pieces.juz) pieces.juz = it.width },
                        onClick = { onOpenIndex(1) }
                    )
                    if (showTimer) {
                        Text(
                            text = formatElapsed(readingDurationMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = sessionTimerTextColor,
                            modifier = Modifier
                                .onSizeChanged { if (it.width > pieces.timer) pieces.timer = it.width }
                                .padding(horizontal = 6.dp)
                        )
                    }
                    HeaderBarActions(
                        btnSize = btnSize,
                        iconSize = iconSize,
                        quietContentColor = quietContentColor,
                        activeContentColor = activeContentColor,
                        fillScreen = fillScreen,
                        hasBookmark = hasBookmark,
                        hasBookmark2 = hasBookmark2,
                        paletteId = paletteId,
                        isVisible = isVisible,
                        visibleBarActionIds = visibleBarActionIds,
                        buttonTint = buttonTint,
                        onOpenSearch = onOpenSearch,
                        onBookmarkJump = onBookmarkJump,
                        onBookmark2Jump = onBookmark2Jump,
                        onStats = onStats,
                        onOpenIndex = onOpenIndex,
                        onPaletteChange = onPaletteChange,
                        onToggleFillScreen = onToggleFillScreen,
                        onHideHeader = onHideHeader,
                        onListen = onListen,
                        onSharePage = onSharePage,
                        onPageFawaed = onPageFawaed,
                    )
                    HeaderMoreMenu(
                        btnSize = btnSize,
                        iconSize = iconSize,
                        tint = quietContentColor,
                        quietContentColor = quietContentColor,
                        hasBookmark = hasBookmark,
                        hasBookmark2 = hasBookmark2,
                        fillScreen = fillScreen,
                        paletteId = paletteId,
                        isVisible = isVisible,
                        visibleBarActionIds = visibleBarActionIds,
                        onOpenSearch = onOpenSearch,
                        onBookmarkJump = onBookmarkJump,
                        onBookmark2Jump = onBookmark2Jump,
                        onStats = onStats,
                        onOpenIndex = onOpenIndex,
                        onPaletteChange = onPaletteChange,
                        onToggleFillScreen = onToggleFillScreen,
                        onHideHeader = onHideHeader,
                        onListen = onListen,
                        onSharePage = onSharePage,
                        onPageFawaed = onPageFawaed,
                        onAbout = onAbout
                    )
                }
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Keep clear of a side notch in landscape; the top band is handled by the strip.
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                // …and clear of a curved corner, which no inset reports.
                .padding(horizontal = edgeMargin)
        ) {
            // Camera-safe top strip: every quick-access button lives here, split between the two
            // corners so the center stays clear for a punch-hole/notch. Trim a little of the inset
            // to tighten the top gap, but never below the icon height (32dp).
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxOf(topInset - 6.dp, btnSize))
            ) {
                // Bar actions get whatever the start corner (settings · clock), the timer and the
                // always-present More leave; this row has no spacing between its buttons.
                val actionRoom = maxWidth -
                    pieces.start.orDp(btnSize + if (showClock) 64.dp else 0.dp) - 6.dp -
                    (if (showTimer) pieces.timer.orDp(72.dp) else 0.dp) - btnSize
                val visibleBarActionIds = fitBarActions(
                    requestedBarActions, barSlots(actionRoom.value, btnSize.value)
                )
                ReportBarOverflow(requestedBarActions, visibleBarActionIds, onBarOverflowChange)
                // Start corner (top-right in RTL): keep the always-needed controls close and quiet.
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .onSizeChanged { if (it.width > pieces.start) pieces.start = it.width },
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
                    if (showTimer) {
                        Text(
                            text = formatElapsed(readingDurationMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = sessionTimerTextColor,
                            modifier = Modifier
                                .onSizeChanged { if (it.width > pieces.timer) pieces.timer = it.width }
                                .padding(horizontal = 6.dp)
                        )
                    }
                    HeaderBarActions(
                        btnSize = btnSize,
                        iconSize = iconSize,
                        quietContentColor = quietContentColor,
                        activeContentColor = activeContentColor,
                        fillScreen = fillScreen,
                        hasBookmark = hasBookmark,
                        hasBookmark2 = hasBookmark2,
                        paletteId = paletteId,
                        isVisible = isVisible,
                        visibleBarActionIds = visibleBarActionIds,
                        buttonTint = buttonTint,
                        onOpenSearch = onOpenSearch,
                        onBookmarkJump = onBookmarkJump,
                        onBookmark2Jump = onBookmark2Jump,
                        onStats = onStats,
                        onOpenIndex = onOpenIndex,
                        onPaletteChange = onPaletteChange,
                        onToggleFillScreen = onToggleFillScreen,
                        onHideHeader = onHideHeader,
                        onListen = onListen,
                        onSharePage = onSharePage,
                        onPageFawaed = onPageFawaed,
                    )
                    HeaderMoreMenu(
                        btnSize = btnSize,
                        iconSize = iconSize,
                        tint = quietContentColor,
                        quietContentColor = quietContentColor,
                        hasBookmark = hasBookmark,
                        hasBookmark2 = hasBookmark2,
                        fillScreen = fillScreen,
                        paletteId = paletteId,
                        isVisible = isVisible,
                        visibleBarActionIds = visibleBarActionIds,
                        onOpenSearch = onOpenSearch,
                        onBookmarkJump = onBookmarkJump,
                        onBookmark2Jump = onBookmark2Jump,
                        onStats = onStats,
                        onOpenIndex = onOpenIndex,
                        onPaletteChange = onPaletteChange,
                        onToggleFillScreen = onToggleFillScreen,
                        onHideHeader = onHideHeader,
                        onListen = onListen,
                        onSharePage = onSharePage,
                        onPageFawaed = onPageFawaed,
                        onAbout = onAbout
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

/**
 * Overflow ("more") menu shared by both header layouts: the secondary reading actions live behind a
 * single button so the strip stays uncluttered. Owns its own open/closed state.
 *
 * "About" sits at the bottom, below a divider, and is NOT one of [HeaderActions]: it is not a
 * reading tool you would ever want on the bar, and — being the answer to "what is this app, and
 * who made it" — it has to be somewhere a reader can always find it. Everything above the divider
 * can be moved or hidden from settings; this cannot, which is the point of it being here.
 *
 * Because of it the menu is never empty, so the overflow button no longer steps aside when every
 * reading action has been moved onto the bar.
 */
@Composable
private fun HeaderMoreMenu(
    btnSize: Dp,
    iconSize: Dp,
    tint: Color,
    quietContentColor: Color,
    hasBookmark: Boolean,
    hasBookmark2: Boolean,
    fillScreen: Boolean,
    paletteId: String,
    isVisible: (String) -> Boolean,
    visibleBarActionIds: Set<String>,
    onOpenSearch: () -> Unit,
    onBookmarkJump: () -> Unit,
    onBookmark2Jump: () -> Unit,
    onStats: () -> Unit,
    onOpenIndex: (Int) -> Unit,
    onPaletteChange: (String) -> Unit,
    onToggleFillScreen: () -> Unit,
    onHideHeader: () -> Unit,
    onListen: () -> Unit,
    onSharePage: () -> Unit,
    onPageFawaed: () -> Unit,
    onAbout: () -> Unit,
) {
    // Actions assigned to the menu plus any preferred bar actions that did not fit this window.
    val menuActions = HeaderActions.filter { isVisible(it.id) && it.id !in visibleBarActionIds }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(btnSize)) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "\u0627\u0644\u0645\u0632\u064a\u062f",
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
        DropdownMenu(
            expanded = showMoreMenu,
            onDismissRequest = { showMoreMenu = false }
        ) {
            menuActions.forEach { action ->
                val enabled = when (action.id) {
                    "bookmark" -> hasBookmark
                    "bookmark2" -> hasBookmark2
                    else -> true
                }
                DropdownMenuItem(
                    text = { Text(headerActionLabel(action, fillScreen)) },
                    leadingIcon = {
                        Icon(
                            imageVector = headerActionIcon(action, fillScreen),
                            contentDescription = null,
                            tint = headerActionDefaultTint(
                                action.id, hasBookmark, hasBookmark2, fillScreen,
                                quietContentColor, quietContentColor
                            )
                        )
                    },
                    enabled = enabled,
                    onClick = {
                        showMoreMenu = false
                        if (action.id == "theme") {
                            showThemeMenu = true
                        } else {
                            runHeaderAction(
                                action.id, onOpenSearch, onBookmarkJump, onBookmark2Jump,
                                onStats, onOpenIndex, onToggleFillScreen, onHideHeader, onListen, onSharePage, onPageFawaed
                            )
                        }
                    }
                )
            }

            if (menuActions.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("حول التطبيق") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = quietContentColor
                    )
                },
                onClick = {
                    showMoreMenu = false
                    onAbout()
                }
            )
        }
        // Second menu on the same anchor: picking the theme without leaving the reader.
        PaletteMenu(
            expanded = showThemeMenu,
            paletteId = paletteId,
            onDismiss = { showThemeMenu = false },
            onPaletteChange = onPaletteChange
        )
    }
}

/** The actions the user placed on the bar itself, each drawn in the color chosen for it. Actions
 *  left in the More menu are skipped here — [HeaderMoreMenu] renders exactly the complement. */
@Composable
private fun HeaderBarActions(
    btnSize: Dp,
    iconSize: Dp,
    quietContentColor: Color,
    activeContentColor: Color,
    fillScreen: Boolean,
    hasBookmark: Boolean,
    hasBookmark2: Boolean,
    paletteId: String,
    isVisible: (String) -> Boolean,
    visibleBarActionIds: Set<String>,
    buttonTint: (String, Color) -> Color,
    onOpenSearch: () -> Unit,
    onBookmarkJump: () -> Unit,
    onBookmark2Jump: () -> Unit,
    onStats: () -> Unit,
    onOpenIndex: (Int) -> Unit,
    onPaletteChange: (String) -> Unit,
    onToggleFillScreen: () -> Unit,
    onHideHeader: () -> Unit,
    onListen: () -> Unit,
    onSharePage: () -> Unit,
    onPageFawaed: () -> Unit,
) {
    HeaderActions.forEach { action ->
        if (!isVisible(action.id) || action.id !in visibleBarActionIds) return@forEach
        val tint = buttonTint(
            action.id,
            headerActionDefaultTint(
                action.id, hasBookmark, hasBookmark2, fillScreen,
                quietContentColor, activeContentColor
            )
        )
        if (action.id == "theme") {
            HeaderThemeButton(btnSize, iconSize, tint, paletteId, onPaletteChange)
            return@forEach
        }
        val enabled = when (action.id) {
            "bookmark" -> hasBookmark
            "bookmark2" -> hasBookmark2
            else -> true
        }
        IconButton(
            onClick = {
                runHeaderAction(
                    action.id, onOpenSearch, onBookmarkJump, onBookmark2Jump,
                    onStats, onOpenIndex, onToggleFillScreen, onHideHeader, onListen, onSharePage, onPageFawaed
                )
            },
            enabled = enabled,
            modifier = Modifier.size(btnSize)
        ) {
            Icon(
                imageVector = headerActionIcon(action, fillScreen),
                contentDescription = headerActionLabel(action, fillScreen),
                tint = if (enabled) tint else quietContentColor.copy(alpha = 0.38f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/** The palette picker as its own bar button; it opens the same list the More menu shows. */
@Composable
private fun HeaderThemeButton(
    btnSize: Dp,
    iconSize: Dp,
    tint: Color,
    paletteId: String,
    onPaletteChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(btnSize)) {
            Icon(
                imageVector = Icons.Filled.Palette,
                contentDescription = "\u0627\u0644\u0645\u0638\u0647\u0631",
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
        PaletteMenu(
            expanded = expanded,
            paletteId = paletteId,
            onDismiss = { expanded = false },
            onPaletteChange = onPaletteChange
        )
    }
}

/** The six reading themes as menu items, each swatch painted in that theme's own paper and ink. */
@Composable
private fun PaletteMenu(
    expanded: Boolean,
    paletteId: String,
    onDismiss: () -> Unit,
    onPaletteChange: (String) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MushafPalettes.forEach { choice ->
            DropdownMenuItem(
                text = { Text(choice.label) },
                leadingIcon = {
                    Surface(
                        modifier = Modifier.size(16.dp),
                        shape = CircleShape,
                        color = choice.paper,
                        border = BorderStroke(1.dp, choice.ink.copy(alpha = 0.55f))
                    ) {}
                },
                trailingIcon = if (choice.id == paletteId) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else null,
                onClick = {
                    onDismiss()
                    onPaletteChange(choice.id)
                }
            )
        }
    }
}

private fun runHeaderAction(
    id: String,
    onOpenSearch: () -> Unit,
    onBookmarkJump: () -> Unit,
    onBookmark2Jump: () -> Unit,
    onStats: () -> Unit,
    onOpenIndex: (Int) -> Unit,
    onToggleFillScreen: () -> Unit,
    onHideHeader: () -> Unit,
    onListen: () -> Unit,
    onSharePage: () -> Unit,
    onPageFawaed: () -> Unit,
) {
    when (id) {
        "search" -> onOpenSearch()
        "share" -> onSharePage()
        "fawaed" -> onPageFawaed()
        "bookmark" -> onBookmarkJump()
        "bookmark2" -> onBookmark2Jump()
        "stats" -> onStats()
        "index" -> onOpenIndex(0)
        "fill" -> onToggleFillScreen()
        "hide" -> onHideHeader()
        "listen" -> onListen()
    }
}

/** Fill-screen is the one action that reads as a state, so its icon and wording follow it. */
private fun headerActionIcon(action: HeaderAction, fillScreen: Boolean) =
    if (action.id == "fill" && fillScreen) Icons.Filled.FullscreenExit else action.barIcon

private fun headerActionLabel(action: HeaderAction, fillScreen: Boolean) =
    if (action.id == "fill" && fillScreen) "\u0639\u0631\u0636 \u0627\u0644\u0635\u0641\u062d\u0629 \u0643\u0627\u0645\u0644\u0629" else action.title

/** What an action is tinted when its color is left on "\u0647\u0627\u062f\u0626" — the look it had before the setting
 *  existed: the bookmarks flag whether they point anywhere, fill-screen flags that it is on. */
private fun headerActionDefaultTint(
    id: String,
    hasBookmark: Boolean,
    hasBookmark2: Boolean,
    fillScreen: Boolean,
    quiet: Color,
    active: Color,
): Color = when (id) {
    "bookmark" -> if (hasBookmark) BookmarkGoldColor else quiet
    "bookmark2" -> if (hasBookmark2) BookmarkVioletColor else quiet
    "fill" -> if (fillScreen) active else quiet
    else -> quiet
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
    /** Keeps the chip's travel clear of a curved top/bottom corner. 0.dp by default. */
    edgeMargin: Dp,
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
            val edgePx = with(density) { edgeMargin.toPx() }
            val floorPx = topInsetPx + edgePx
            val maxY = (maxHpx - btnSize.height - edgePx).coerceAtLeast(floorPx)
            val travel = maxY - floorPx
            val y = floorPx + posFraction.coerceIn(0f, 1f) * travel
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
    onSourceChosen: (String) -> Unit,
    pinnedSources: List<String>,
    onTogglePin: (String) -> Boolean,
    textScale: Int,
    onTextScaleStep: (Int) -> Unit,
    packs: List<InstalledPack>,
    packState: PackContentUiState,
    onLoadPack: (verseKey: String, packId: String) -> Unit,
    downloads: PackDownloads,
    loadFawaed: suspend (Int) -> List<String>,
    loadSurahCard: suspend (Int) -> SurahCard?,
    loadTajweed: suspend (surah: Int, ayah: Int) -> TajweedNote?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()
    val scale = textScale / 100f
    // The page's fawaed and the surah's card, loaded with the sheet: they are small, and the share
    // button needs them whichever source is showing.
    val ayahPage = request.ayah.page
    val ayahSurah = request.ayah.surahNumber
    val surahAyahCount = SurahAyahCounts.of(ayahSurah)
    val fawaed by produceState<List<String>?>(initialValue = null, ayahPage) { value = loadFawaed(ayahPage) }
    val surahCard by produceState(initialValue = SurahCardLoad(), ayahSurah) {
        value = SurahCardLoad(card = loadSurahCard(ayahSurah), loaded = true)
    }
    val tajweed by produceState(initialValue = TajweedLoad(), request.ayah.verseKey) {
        value = TajweedLoad(note = loadTajweed(ayahSurah, request.ayah.ayahNumber), loaded = true)
    }
    val bodyLarge = ReadingType.bodyLarge.scaled(scale)
    val bodyMedium = ReadingType.bodyMedium.scaled(scale)
    val wordStyle = MaterialTheme.typography.titleMedium.scaled(scale)
    var source by remember(request.ayah.verseKey, request.initialSource) {
        mutableStateOf(request.initialSource)
    }
    val selectedTab = ExplanationTab.forSource(source)
    val packOf: (String) -> InstalledPack? = { s ->
        ExplanationSources.packId(s)?.let { id -> packs.firstOrNull { it.id == id } }
    }
    val currentPack = packOf(source)
    val installedIds = remember(packs) { packs.map { it.id }.toSet() }
    val visiblePinned = ExplanationSources.visiblePinned(pinnedSources, installedIds)
    // A source's full name, heading its text. A pack deleted while the sheet is open has none left.
    val titleOf: (String) -> String = { s ->
        if (ExplanationSources.packId(s) == null) ExplanationTab.forSource(s).menuLabel
        else packOf(s)?.title.orEmpty().ifBlank { "المصادر" }
    }
    // The pages the sheet swipes between: the pinned sources, and the one being read when it is not
    // among them.
    val pages = if (source in visiblePinned) visiblePinned else visiblePinned + source
    val pagerState = rememberPagerState(initialPage = pages.indexOf(source).coerceAtLeast(0)) { pages.size }
    // Set when the reader drags the pages, so that only a swipe of theirs — never the pager catching
    // up with a source chosen by name — changes the source.
    val swiped = remember { BooleanArray(1) }
    val setSource: (String) -> Unit = { s ->
        source = s
        onSourceChosen(s)
    }
    val choose: (String) -> Unit = { s ->
        swiped[0] = false
        setSource(s)
    }
    val currentPages by rememberUpdatedState(pages)
    val currentSource by rememberUpdatedState(source)
    val currentSetSource by rememberUpdatedState(setSource)
    // A source chosen by name or by an arrow: the pager follows it.
    LaunchedEffect(source, pages) {
        val page = pages.indexOf(source)
        if (page >= 0 && page != pagerState.currentPage) pagerState.animateScrollToPage(page)
    }
    // A swipe: once the pager comes to rest, the source follows it. Rest means on a page, not just
    // between the drag and the fling that ends it: leaving an unpinned source drops its page, and
    // dropping a page under a fling still running leaves the pager stranded between two pages.
    LaunchedEffect(pagerState) {
        launch {
            pagerState.interactionSource.interactions.collect {
                if (it is DragInteraction.Start) swiped[0] = true
            }
        }
        snapshotFlow {
            val moving = pagerState.isScrollInProgress || abs(pagerState.currentPageOffsetFraction) > 0.01f
            if (moving) -1 else pagerState.currentPage
        }.collect { page ->
            if (page >= 0 && swiped[0]) {
                swiped[0] = false
                currentPages.getOrNull(page)?.takeIf { it != currentSource }?.let { currentSetSource(it) }
            }
        }
    }
    val currentState = state.takeIf { it.verseKey == request.ayah.verseKey }
    var showSources by rememberSaveable { mutableStateOf(false) }
    val openSources = { showSources = true }
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    val packContent = packState.takeIf {
        it.verseKey == request.ayah.verseKey && it.packId == currentPack?.id
    }
    LaunchedEffect(currentPack?.id, request.ayah.verseKey) {
        if (currentPack != null) onLoadPack(request.ayah.verseKey, currentPack.id)
    }
    val shareTitle = if (selectedTab == ExplanationTab.Pack) currentPack?.title.orEmpty() else selectedTab.title
    val shareText = remember(request.ayah, currentState, selectedTab, packContent, currentPack, fawaed, surahCard, tajweed) {
        if (currentState == null || currentState.loading || currentState.errorMessage != null) {
            null
        } else {
            val content = when (selectedTab) {
                ExplanationTab.Muyassar -> currentState.tafsirHtml?.let {
                    formatTafsirText(it, Color.Unspecified).text
                }
                ExplanationTab.Saadi -> currentState.saadi?.shareText
                ExplanationTab.Mukhtasar -> currentState.mukhtasar?.let(MukhtasarText::plain)
                ExplanationTab.Meanings -> currentState.meanings.joinToString("\n\n") {
                    "${it.word}: ${it.meaning}"
                }
                ExplanationTab.Pack -> when (val pack = packContent?.content) {
                    is PackContent.Passage -> pack.passage.shareText
                    is PackContent.Text -> pack.row.plainText
                    null -> null
                }
                ExplanationTab.Tajweed, ExplanationTab.PageFawaed, ExplanationTab.AboutSurah -> null
            }
            // Page- and surah-wide, or a note that may cover several ayahs: their text names its own place.
            when (selectedTab) {
                ExplanationTab.Tajweed -> return@remember tajweed.note?.let {
                    tajweedShareText(request.ayah.surahNameAr, it)
                }
                ExplanationTab.PageFawaed -> return@remember fawaed?.let { fawaedShareText(ayahPage, it) }
                ExplanationTab.AboutSurah -> return@remember surahCard.card?.let {
                    surahCardShareText(it, request.ayah.surahNameAr, surahAyahCount)
                }
                else -> Unit
            }
            // The text leaves the app here, where the sources screen cannot follow it.
            val supplement = selectedTab == ExplanationTab.Saadi && currentState.saadi?.sharesSupplement == true
            val credit = when (selectedTab) {
                ExplanationTab.Pack -> currentPack?.credit.orEmpty()
                else -> selectedTab.credit + if (supplement) " ${CenterAttribution.SUPPLEMENT_CREDIT}" else ""
            }
            content?.takeIf { it.isNotBlank() }?.let {
                "$shareTitle\n" +
                    "سورة ${request.ayah.surahNameAr} • الآية ${request.ayah.ayahNumber.toArabicDigits()}\n\n" +
                    "${SharedText.limit(it)}\n\n$credit"
            }
        }
    }

    if (showSources) SourcesDialog(onDismiss = { showSources = false })
    if (showLibrary) TafsirLibraryDialog(downloads = downloads, onDismiss = { showLibrary = false })

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
            // Whose explanation this is, on one line; the tools for its text beside it, then close.
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 6.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "${request.ayah.surahNameAr} • الآية ${request.ayah.ayahNumber.toArabicDigits()}",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (request.ayah.isSajdah) SajdahBadge()
                    }
                    Text(
                        text = "صفحة ${request.ayah.page.toArabicDigits()} • الجزء ${request.ayah.juz.toArabicDigits()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SheetTextTools(
                    text = shareText,
                    title = shareTitle,
                    scale = textScale,
                    onStep = onTextScaleStep,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        sheetScope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) onDismiss()
                        }
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "إغلاق",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // The page being shown names the bar, so the name turns with the swipe, not after it.
            val shownPage = pagerState.currentPage.coerceIn(0, pages.lastIndex)
            SourceBar(
                title = titleOf(pages[shownPage]),
                index = shownPage,
                count = pages.size,
                onStep = { by -> pages.getOrNull(shownPage + by)?.let(choose) },
                current = source,
                pinned = visiblePinned,
                packs = packs,
                onChoose = choose,
                onTogglePin = onTogglePin,
                onOpenLibrary = { showLibrary = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            currentState?.nozool?.takeIf {
                !currentState.loading &&
                    selectedTab != ExplanationTab.PageFawaed && selectedTab != ExplanationTab.AboutSurah
            }?.let { row ->
                NozoolBar(row = row, scale = scale)
            }

            // One page per source: a sideways swipe moves between them, as the bar's arrows do.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                key = { pages.getOrNull(it) ?: it },
            ) { page ->
                val pageSource = pages.getOrElse(page) { source }
                val pageTab = ExplanationTab.forSource(pageSource)
                val pagePack = packOf(pageSource)
                // Only the pack being read is loaded; a neighbouring pack's page waits to be swiped to.
                val pagePackContent = packState.takeIf {
                    it.verseKey == request.ayah.verseKey && it.packId == pagePack?.id
                }
                when {
                    currentState == null || currentState.loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    currentState.errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
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

                    pageTab == ExplanationTab.Saadi -> {
                        SaadiPassageContent(
                            passage = currentState.saadi,
                            onOpenSources = openSources,
                            scale = scale,
                            modifier = Modifier
                                .fillMaxSize(),
                        )
                    }

                    pageTab == ExplanationTab.Mukhtasar -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(KeepFlingInSheet),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            item {
                                val text = currentState.mukhtasar
                                if (text.isNullOrBlank()) {
                                    EmptyExplanationMessage("لا يتوفر تفسير لهذه الآية.")
                                } else {
                                    Text(
                                        text = formatMukhtasarText(
                                            raw = text,
                                            accent = MaterialTheme.colorScheme.primary,
                                        ),
                                        style = bodyLarge,
                                    )
                                }
                            }
                            item { SourcesLink(openSources) }
                        }
                    }

                    pageTab == ExplanationTab.Muyassar -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(KeepFlingInSheet),
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
                                        style = bodyLarge,
                                    )
                                }
                            }
                            item { SourcesLink(openSources) }
                        }
                    }

                    pageTab == ExplanationTab.Tajweed -> {
                        TajweedContent(
                            note = tajweed.note,
                            loaded = tajweed.loaded,
                            scale = scale,
                            onOpenSources = openSources,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    pageTab == ExplanationTab.PageFawaed -> {
                        PageFawaedContent(
                            page = ayahPage,
                            fawaed = fawaed,
                            scale = scale,
                            onOpenSources = openSources,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    pageTab == ExplanationTab.AboutSurah -> {
                        SurahCardContent(
                            surahNumber = ayahSurah,
                            ayahCount = surahAyahCount,
                            card = surahCard.card,
                            loaded = surahCard.loaded,
                            scale = scale,
                            onOpenSources = openSources,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    pageTab == ExplanationTab.Pack -> {
                        PackTabContent(
                            current = pagePack,
                            state = pagePackContent,
                            onOpenLibrary = { showLibrary = true },
                            onOpenSources = openSources,
                            scale = scale,
                            modifier = Modifier
                                .fillMaxSize(),
                        )
                    }

                    pageTab == ExplanationTab.Meanings -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(KeepFlingInSheet),
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
                                                style = wordStyle,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                text = meaning.meaning,
                                                modifier = Modifier.padding(top = 5.dp),
                                                style = bodyMedium,
                                            )
                                        }
                                    }
                                }
                            }
                            item { SourcesLink(openSources) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A downloaded book chosen from the sources menu: its text for the ayah, or — when the book was
 * deleted while the sheet was open — the way back to the library.
 */
@Composable
private fun PackTabContent(
    current: InstalledPack?,
    state: PackContentUiState?,
    onOpenLibrary: () -> Unit,
    onOpenSources: () -> Unit,
    scale: Float,
    modifier: Modifier,
) {
    if (current == null) {
        Column(
            modifier = modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "هذا الكتاب لم يعد منزَّلًا على الجهاز.\nيمكنك تنزيله من جديد من مكتبة التفاسير، أو اختيار مصدر آخر من قائمة «المصادر».",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onOpenLibrary) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("مكتبة التفاسير")
            }
        }
        return
    }
    Column(modifier = modifier) {
        val content = state?.content
        when {
            state == null || state.loading -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            content is PackContent.Passage -> PackPassageContent(
                passage = content.passage,
                author = current.author.ifBlank { current.title },
                onOpenSources = onOpenSources,
                scale = scale,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            content is PackContent.Text -> PackPassageContent(
                passage = TafsirPassage(listOf(content.row), content.row.fromAyah),
                author = current.author.ifBlank { current.title },
                onOpenSources = onOpenSources,
                scale = scale,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            else -> Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                EmptyExplanationMessage("لا يتوفر نص لهذه الآية في هذا الكتاب.")
                SourcesLink(onOpenSources)
            }
        }
    }
}

/** One paragraph of a row, laid out as its own list item: a pack's row can be book-length. */
private data class PackParagraph(val row: Int, val text: String, val lastOfRow: Boolean, val firstOfRow: Boolean)

/**
 * A downloaded book's text for the ayah. Its rows are long and usually just one, so each paragraph
 * is its own item, the notes follow the row's last paragraph, and a row explaining several ayahs
 * together says which.
 */
@Composable
private fun PackPassageContent(
    passage: TafsirPassage,
    author: String,
    onOpenSources: () -> Unit,
    scale: Float,
    modifier: Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val reading = ReadingType.bodyLarge.scaled(scale)
    val paragraphs = remember(passage) {
        passage.rows.flatMapIndexed { index, row ->
            val parts = row.text.split('\n').filter { it.isNotBlank() }
            parts.mapIndexed { i, part -> PackParagraph(index, part, i == parts.lastIndex, i == 0) }
        }
    }
    val listState = rememberLazyListState()
    val lead = if (!passage.hasOwnTafsir) 1 else 0
    LaunchedEffect(passage) {
        if (passage.rows.size > 1 && passage.hasOwnTafsir) {
            val first = paragraphs.indexOfFirst { it.row == passage.focusIndex }
            if (first > 0) listState.scrollToItem(first + lead)
        }
    }
    LazyColumn(
        modifier = modifier.nestedScroll(KeepFlingInSheet),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!passage.hasOwnTafsir) {
            item {
                Text(
                    text = "لم يُفرد $author هذه الآية بتفسير مستقل، وهذا تفسير الموضع الذي تقع فيه.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            }
        }
        itemsIndexed(paragraphs) { _, paragraph ->
            val row = passage.rows[paragraph.row]
            Column {
                if (paragraph.firstOfRow && row.toAyah > row.fromAyah && row.fromAyah > 0) {
                    Text(
                        text = "تفسير الآيات (${row.fromAyah.toArabicDigits()}–${row.toAyah.toArabicDigits()})",
                        modifier = Modifier.padding(bottom = 6.dp),
                        style = MaterialTheme.typography.labelLarge.scaled(scale),
                        color = muted,
                    )
                }
                Text(text = formatSaadiText(paragraph.text, accent), style = reading)
                if (paragraph.lastOfRow) SaadiNotes(notes = row.notes, scale = scale)
            }
        }
        item { SourcesLink(onOpenSources) }
    }
}

/** The occasion of the ayah's revelation, folded into one line above the tabs until it is opened. */
@Composable
private fun NozoolBar(row: TafsirPassageRow, scale: Float) {
    var open by rememberSaveable(row.text) { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "سبب النزول",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge.scaled(scale),
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "طيّ سبب النزول" else "إظهار سبب النزول",
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = if (open) 180f else 0f },
                )
            }
            if (open) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.text.split('\n').filter { it.isNotBlank() }.forEach { paragraph ->
                        Text(
                            text = formatSaadiText(paragraph, accent),
                            style = ReadingType.bodyMedium.scaled(scale),
                        )
                    }
                    SaadiNotes(notes = row.notes, scale = scale)
                }
            }
        }
    }
}

/**
 * Sa'di explains the Quran passage by passage, so the whole passage is shown — its lead-in, the
 * Quran text, and every ayah's explanation — with the asked-about ayah's own explanation outlined
 * and scrolled into view. The Quran text is the mushaf text the rest of the app shows.
 */
@Composable
private fun SaadiPassageContent(
    passage: TafsirPassage?,
    onOpenSources: () -> Unit,
    scale: Float,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val leadItems = if (passage != null && !passage.hasOwnTafsir) 1 else 0
    LaunchedEffect(passage) {
        if (passage != null && passage.hasOwnTafsir) {
            listState.scrollToItem(passage.focusIndex + leadItems)
        }
    }
    LazyColumn(
        modifier = modifier.nestedScroll(KeepFlingInSheet),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (passage == null || passage.isEmpty) {
            item { EmptyExplanationMessage("لا يتوفر تفسير لهذه الآية.") }
        } else {
            if (!passage.hasOwnTafsir) {
                item {
                    Text(
                        text = "لم يُفرد السعدي هذه الآية بتفسير مستقل، وهذا تفسير المقطع الذي تقع فيه.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(passage.rows) { index, row ->
                SaadiRow(row = row, focused = index in passage.focusRows, scale = scale)
            }
        }
        item { SourcesLink(onOpenSources) }
    }
}

@Composable
private fun SaadiRow(row: TafsirPassageRow, focused: Boolean, scale: Float) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val centered = Modifier.fillMaxWidth()
    val reading = ReadingType.bodyLarge.scaled(scale)
    when (row.kind) {
        TafsirRowKind.SurahTitle -> Text(
            text = row.text,
            modifier = centered,
            style = MaterialTheme.typography.titleMedium.scaled(scale),
            fontWeight = FontWeight.ExtraBold,
            color = accent,
            textAlign = TextAlign.Center,
        )
        // Surah headings can carry a note: Maryam's says it is Madinan, and the editor corrects it.
        TafsirRowKind.RevelationPlace -> Column(modifier = centered) {
            Text(
                text = formatSaadiText(row.text, accent),
                modifier = centered,
                style = MaterialTheme.typography.labelLarge.scaled(scale),
                color = muted,
                textAlign = TextAlign.Center,
            )
            SaadiNotes(notes = row.notes, scale = scale)
        }
        TafsirRowKind.Basmala -> Text(
            text = row.text,
            modifier = centered,
            style = reading,
            color = accent,
            textAlign = TextAlign.Center,
        )
        TafsirRowKind.Divider -> Text(
            text = row.text,
            modifier = centered,
            color = muted,
            textAlign = TextAlign.Center,
        )
        TafsirRowKind.QuranText -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ) {
            Text(
                text = row.text,
                modifier = Modifier.padding(16.dp),
                style = reading,
                color = accent,
            )
        }
        TafsirRowKind.AyahTafsir, TafsirRowKind.Prose, TafsirRowKind.Supplement -> {
            val text = formatSaadiText(row.text, accent)
            val body: @Composable (Modifier) -> Unit = { bodyModifier ->
                Column(modifier = bodyModifier) {
                    if (row.kind == TafsirRowKind.Supplement) {
                        Text(
                            text = "تكملة من موسوعة القرآن الكريم، غير موجودة في نسخة مركز تفسير:",
                            modifier = Modifier.padding(bottom = 4.dp),
                            style = MaterialTheme.typography.labelMedium.scaled(scale),
                            color = muted,
                        )
                    }
                    Text(text = text, style = reading)
                    SaadiNotes(notes = row.notes, scale = scale)
                }
            }
            if (focused) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = accent.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
                ) {
                    body(Modifier.padding(14.dp))
                }
            } else {
                body(Modifier)
            }
        }
    }
}

/** The editor's notes on a row, folded away under a small button until the reader asks for them. */
@Composable
internal fun SaadiNotes(notes: List<String>, scale: Float) {
    if (notes.isEmpty()) return
    var open by rememberSaveable(notes) { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelLarge.scaled(scale)
    TextButton(
        onClick = { open = !open },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        Text(text = "حواشي المحقق (${notes.size.toArabicDigits()})", style = labelStyle)
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = if (open) "طيّ الحواشي" else "إظهار الحواشي",
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = if (open) 180f else 0f },
        )
    }
    if (open) {
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            notes.forEachIndexed { index, note ->
                Text(
                    text = "(${(index + 1).toArabicDigits()}) $note",
                    style = MaterialTheme.typography.bodySmall.scaled(scale),
                    color = muted,
                )
            }
        }
    }
}

/**
 * The sheet's tools for the text it shows: its size, then copying and sharing it. The size opens its
 * two steps, down and up (fixed steps in [ExplanationTextScale], each end disabling its button). In
 * the RTL sheet the smaller size sits on the right and the larger on the left, following the reading
 * direction.
 */
@Composable
internal fun SheetTextTools(text: String?, title: String, scale: Int, onStep: (Int) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var sizing by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            SmallToolButton(Icons.Filled.FormatSize, "حجم الخط") { sizing = true }
            DropdownMenu(expanded = sizing, onDismissRequest = { sizing = false }) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallToolButton(
                        Icons.Filled.TextDecrease,
                        "تصغير الخط",
                        enabled = ExplanationTextScale.canShrink(scale),
                    ) { onStep(-1) }
                    Text(
                        text = "${ExplanationTextScale.normalize(scale).toArabicDigits()}٪",
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 52.dp),
                    )
                    SmallToolButton(
                        Icons.Filled.TextIncrease,
                        "تكبير الخط",
                        enabled = ExplanationTextScale.canGrow(scale),
                    ) { onStep(+1) }
                }
            }
        }
        SmallToolButton(Icons.Filled.ContentCopy, "نسخ $title", enabled = text != null) {
            text?.let { content ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(title, content)))
                    // Android 13 and newer already show a clipboard confirmation.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, "تم نسخ $title", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        SmallToolButton(Icons.Filled.Share, "مشاركة $title", enabled = text != null) {
            text?.let { content ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                context.startActivity(Intent.createChooser(send, null))
            }
        }
    }
}

/** A compact icon button for a tools group: small glyph, the group's muted colour. */
@Composable
private fun SmallToolButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(19.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
    }
}

/**
 * The source being read, heading its text. Its name opens every source the sheet can show: the app's
 * own, the downloaded books, and the way to download more, with a pin beside each. The pinned ones
 * are the pages the sheet swipes between; the marks under the name count them and the arrows step
 * through them. A source read without being pinned is one more page, the last.
 */
@Composable
private fun SourceBar(
    title: String,
    index: Int,
    count: Int,
    onStep: (Int) -> Unit,
    current: String,
    pinned: List<String>,
    packs: List<InstalledPack>,
    onChoose: (String) -> Unit,
    onTogglePin: (String) -> Boolean,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val pin: (String) -> Unit = { source ->
        if (!onTogglePin(source)) {
            Toast.makeText(
                context,
                "يمكن تثبيت ${ExplanationSources.MAX_PINNED.toArabicDigits()} مصادر على الأكثر؛ ألغِ تثبيت أحدها أولًا.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (count > 1) {
                SourceStepButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    description = "المصدر السابق",
                    enabled = index > 0,
                    onClick = { onStep(-1) },
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "اختيار المصدر", role = Role.Button) { open = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (count > 1) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            repeat(count) { page ->
                                val width by animateDpAsState(
                                    targetValue = if (page == index) 16.dp else 6.dp,
                                    label = "sourceMark",
                                )
                                Box(
                                    modifier = Modifier
                                        .size(width = width, height = 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (page == index) accent
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        ),
                                )
                            }
                        }
                    }
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    Text(
                        text = "ثبّت بالدبوس ما تريد التنقّل بينه بالسحب",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    ExplanationSources.BUILT_IN.forEach { source ->
                        SourceMenuItem(
                            label = ExplanationTab.forSource(source).menuLabel,
                            selected = source == current,
                            pinned = source in pinned,
                            onClick = {
                                open = false
                                onChoose(source)
                            },
                            onPin = { pin(source) },
                        )
                    }
                    if (packs.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        packs.forEach { pack ->
                            val source = ExplanationSources.pack(pack.id)
                            SourceMenuItem(
                                label = pack.title,
                                selected = source == current,
                                pinned = source in pinned,
                                onClick = {
                                    open = false
                                    onChoose(source)
                                },
                                onPin = { pin(source) },
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("تنزيل تفاسير أخرى") },
                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null, tint = accent) },
                        onClick = {
                            open = false
                            onOpenLibrary()
                        },
                    )
                }
            }
            if (count > 1) {
                SourceStepButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    description = "المصدر التالي",
                    enabled = index < count - 1,
                    onClick = { onStep(+1) },
                )
            }
        }
    }
}

/** An arrow of the source bar: one source back or on, dimmed at either end. */
@Composable
private fun SourceStepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.3f),
        )
    }
}

@Composable
private fun SourceMenuItem(
    label: String,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (selected) accent else Color.Transparent,
            )
        },
        trailingIcon = {
            IconButton(onClick = onPin) {
                Icon(
                    imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.OutlinedPushPin,
                    contentDescription = if (pinned) "إلغاء تثبيت $label" else "تثبيت $label",
                    modifier = Modifier.size(20.dp),
                    tint = if (pinned) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        },
        onClick = onClick,
    )
}

/** [this] with its size and line height multiplied by [factor], for the sheet's text-size steps. */
internal fun TextStyle.scaled(factor: Float): TextStyle {
    if (factor == 1f) return this
    return copy(
        fontSize = if (fontSize.isSpecified) fontSize * factor else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
    )
}

/**
 * Sa'di marks quoted Quran words — and each explanation's ayah number — with braces. A note's anchor
 * is what points the eye from the text to the numbered note below it, so it is drawn as a raised number.
 */
private val SaadiMarkup = Regex("""\{[^{}\n]*\}|${TafsirPassageRow.NoteAnchor.pattern}""")

internal fun formatSaadiText(raw: String, accent: Color): AnnotatedString = buildAnnotatedString {
    fun appendAnchored(part: String) {
        var at = 0
        TafsirPassageRow.NoteAnchor.findAll(part).forEach { anchor ->
            append(part.substring(at, anchor.range.first))
            withStyle(SpanStyle(color = accent, fontSize = 0.7.em, baselineShift = BaselineShift.Superscript)) {
                append("(${anchor.groupValues[1].toInt().toArabicDigits()})")
            }
            at = anchor.range.last + 1
        }
        append(part.substring(at))
    }
    var cursor = 0
    SaadiMarkup.findAll(raw).forEach { match ->
        appendAnchored(raw.substring(cursor, match.range.first))
        if (match.value.startsWith("{")) {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                appendAnchored(match.value)
            }
        } else {
            appendAnchored(match.value)
        }
        cursor = match.range.last + 1
    }
    appendAnchored(raw.substring(cursor))
}

@Composable
internal fun EmptyExplanationMessage(message: String) {
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

/** The words the Mukhtasar explains stand out the way Sa'di's Quran quotes do. */
private fun formatMukhtasarText(raw: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    MukhtasarText.Gloss.findAll(raw).forEach { match ->
        append(raw.substring(cursor, match.range.first))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
            append(match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    append(raw.substring(cursor))
}

/**
 * A fling that reaches the end of a text would pass its leftover speed up to the bottom sheet, and
 * the reader's next tap, say on the link below the text, would only stop that motion. A fling
 * downwards still goes up to the sheet, which is how it is swiped away.
 */
internal object KeepFlingInSheet : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0f) available else Velocity.Zero
}

/** All that an explanation says about its source: the way to the screen that names every source. */
@Composable
internal fun SourcesLink(onOpen: () -> Unit) {
    TextButton(onClick = onOpen, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("المصادر والتراخيص", style = MaterialTheme.typography.labelMedium)
    }
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

/**
 * The single ayah menu, opened by a long press: a card as wide as the page. It sits above the whole
 * ayah when there is room, else below it, so the selected ayah stays visible; only an ayah too tall
 * for either gets covered, and then on the roomier side.
 *
 * The ayah's text leads, centred, with where it is beneath it; one bar of named actions closes the
 * card. The text takes up to [AyahCardTextLines] lines, fewer when the card would otherwise cover
 * the ayah. Everything above the bar is a handle: dragging it moves the card up or down anywhere
 * over the page.
 *
 * The popup window covers the whole page area and never moves; only the card moves inside it.
 * Moving the window itself under the finger makes a drag jitter, because every touch is then
 * measured against a window that has just been repositioned.
 */
@Composable
private fun AyahLongPressMenu(
    ayah: AyahMarker,
    ayahBounds: Rect,
    boxWidthPx: Int,
    boxHeightPx: Int,
    pageWidthPx: Int,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    showBookmark: Boolean,
    bookmarked2: Boolean,
    onBookmark2: () -> Unit,
    showBookmark2: Boolean,
    onExplain: () -> Unit,
    onListenAyah: () -> Unit,
    onPlayFrom: () -> Unit,
    onRepeat: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val reference = "﴿${ayah.surahNameAr} • آية ${ayah.ayahNumber.toArabicDigits()}﴾"
    val shareText = if (ayah.textUthmani.isNotBlank()) "${ayah.textUthmani}\n$reference" else reference

    val density = LocalDensity.current
    val margin = with(density) { 10.dp.toPx() }
    val gap = with(density) { 10.dp.toPx() }
    val currentOnClose by rememberUpdatedState(onClose)
    // Where the card was last placed: [top, height]. Read by the outside-touch test and the drag.
    val placed = remember { FloatArray(2) }
    // Null until the header is dragged; until then the card sits beside the ayah.
    var draggedTop by remember { mutableStateOf<Float?>(null) }

    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val hasText = ayah.textUthmani.isNotBlank()

    val coverPageArea = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = anchorBounds.topLeft
        }
    }

    Popup(
        popupPositionProvider = coverPageArea,
        onDismissRequest = onClose,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        // The card, its ayah text cut to [textLines]; the layout below picks how many fit.
        val cardWith: @Composable (Int) -> Unit = { textLines ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
                shadowElevation = 12.dp,
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    // The ayah itself comes first. Everything above the actions is the drag
                    // handle; the bar on top says so.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(boxHeightPx) {
                                detectVerticalDragGestures { change, dy ->
                                    change.consume()
                                    val maxTop = (boxHeightPx - placed[1]).coerceAtLeast(0f)
                                    draggedTop = ((draggedTop ?: placed[0]) + dy).coerceIn(0f, maxTop)
                                }
                            },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(width = 36.dp, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                            )
                            if (hasText) {
                                Text(
                                    text = ayah.textUthmani,
                                    style = ReadingType.bodyLarge,
                                    color = ink,
                                    textAlign = TextAlign.Center,
                                    maxLines = textLines,
                                    overflow = TextOverflow.Ellipsis,
                                    // Starts below the close button in the corner.
                                    modifier = Modifier.padding(top = 18.dp),
                                )
                            }
                            // Where the ayah is: its surah and number under the text, the rest in chips.
                            Row(
                                modifier = Modifier.padding(top = if (hasText) 6.dp else 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "${ayah.surahNameAr} • الآية ${ayah.ayahNumber.toArabicDigits()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (ayah.isSajdah) SajdahBadge()
                            }
                            Row(
                                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AyahPlaceChip("صفحة ${ayah.page.toArabicDigits()}")
                                AyahPlaceChip("الجزء ${ayah.juz.toArabicDigits()}")
                                AyahPlaceChip("الحزب ${ayah.hizb.toArabicDigits()}")
                            }
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 4.dp)
                                .size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "إغلاق",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )

                    // One bar for every action, each named: reading the ayah (green) and listening
                    // to it (blue) first, then the bookmarks in their own colours, then the tools.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    ) {
                        val slot = Modifier.weight(1f)
                        AyahBarAction(
                            modifier = slot,
                            label = "التفسير",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            tint = accent,
                            onClickLabel = "التفسير والمعاني",
                            onClick = onExplain,
                        )
                        ListenBarAction(
                            modifier = slot,
                            onPlayFrom = onPlayFrom,
                            onListenAyah = onListenAyah,
                            onRepeat = onRepeat,
                        )
                        // Numbered only when both are shown; alone, either is simply the bookmark.
                        val bothBookmarks = showBookmark && showBookmark2
                        if (showBookmark) {
                            BookmarkBarAction(
                                modifier = slot,
                                label = if (bothBookmarks) "علامة ${1.toArabicDigits()}" else "علامة",
                                set = bookmarked,
                                tint = BookmarkGoldColor,
                                name = "العلامة المرجعية",
                                onClick = onBookmark,
                            )
                        }
                        if (showBookmark2) {
                            BookmarkBarAction(
                                modifier = slot,
                                label = if (bothBookmarks) "علامة ${2.toArabicDigits()}" else "علامة",
                                set = bookmarked2,
                                tint = BookmarkVioletColor,
                                name = "العلامة الثانية",
                                onClick = onBookmark2,
                            )
                        }
                        AyahBarAction(
                            modifier = slot,
                            label = "نسخ",
                            icon = Icons.Filled.ContentCopy,
                            tint = null,
                            onClickLabel = "نسخ الآية",
                            onClick = {
                                clipboardScope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("آية قرآنية", shareText)),
                                    )
                                    Toast.makeText(context, "تم نسخ الآية", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        // Opens the share sheet: the ayah as an image, its text one tap away.
                        AyahBarAction(
                            modifier = slot,
                            label = "مشاركة",
                            icon = Icons.Filled.Share,
                            tint = null,
                            onClickLabel = "مشاركة الآية",
                            onClick = onShare,
                        )
                    }
                }
            }
        }
        SubcomposeLayout(
            modifier = Modifier.pointerInput(Unit) {
                // Touching the page around the card closes it, as touching outside the old popup did.
                awaitEachGesture {
                    val y = awaitFirstDown(requireUnconsumed = false).position.y
                    if (y < placed[0] || y > placed[0] + placed[1]) currentOnClose()
                }
            },
        ) { _ ->
            val width = pageWidthPx.coerceIn(0, boxWidthPx)
            val cardConstraints = Constraints(minWidth = width, maxWidth = width, maxHeight = boxHeightPx)
            // As many lines of the ayah's text as still leave the ayah itself in view on the page;
            // when even one line cannot, the shortest card covers the least of it.
            val card = (AyahCardTextLines downTo 1).firstNotNullOf { lines ->
                subcompose(lines) { cardWith(lines) }.first().measure(cardConstraints).takeIf {
                    lines == 1 ||
                        fitsBesideAyah(ayahBounds, it.height.toFloat(), boxHeightPx.toFloat(), margin, gap)
                }
            }
            layout(boxWidthPx, boxHeightPx) {
                val h = card.height.toFloat()
                val maxTop = (boxHeightPx - h).coerceAtLeast(0f)
                val top = (draggedTop ?: menuTopBesideAyah(ayahBounds, h, boxHeightPx.toFloat(), margin, gap))
                    .coerceIn(0f, maxTop)
                placed[0] = top
                placed[1] = h
                // Physical pixels: the page is centred the same way in either layout direction.
                card.place((boxWidthPx - width) / 2, top.roundToInt())
            }
        }
    }
}

/** The most lines of its ayah's text the ayah card shows; it shows fewer to keep clear of the ayah. */
private const val AyahCardTextLines = 3

/** Whether a card [h] tall fits wholly above the ayah or wholly below it. */
private fun fitsBesideAyah(ayah: Rect, h: Float, boxH: Float, margin: Float, gap: Float): Boolean =
    ayah.top - h - gap >= margin || ayah.bottom + gap + h <= boxH - margin

/** The menu's top beside the ayah: above it if it fits, else below, else on the roomier side. */
private fun menuTopBesideAyah(ayah: Rect, h: Float, boxH: Float, margin: Float, gap: Float): Float {
    val above = ayah.top - h - gap
    val below = ayah.bottom + gap
    return when {
        above >= margin -> above
        below + h <= boxH - margin -> below
        // Neither fits: cover as little as possible, on the roomier side.
        ayah.top > boxH - ayah.bottom -> margin
        else -> boxH - h - margin
    }.coerceIn(margin, (boxH - h - margin).coerceAtLeast(margin))
}

/** Where the ayah falls — its page, juz or hizb — as a quiet chip under the reference. */
@Composable
private fun AyahPlaceChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * One action in the ayah card's bar: its icon in a tinted square, its name beneath. [tint] colours
 * the action by its group; null leaves it neutral, for the plain tools. [hasMenu] adds the arrow
 * that says a tap opens choices rather than acting at once.
 */
@Composable
private fun AyahBarAction(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    tint: Color?,
    onClickLabel: String,
    hasMenu: Boolean = false,
    onClick: () -> Unit,
) {
    val glyph = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    val container = tint?.copy(alpha = 0.12f) ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = glyph)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (hasMenu) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Listening, as one action in the bar: a tap opens the ways to listen — on from this ayah, this
 * ayah alone, or repeating it to memorise.
 */
@Composable
private fun ListenBarAction(
    modifier: Modifier,
    onPlayFrom: () -> Unit,
    onListenAyah: () -> Unit,
    onRepeat: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val tint = StatusBlueColor
    Box(modifier = modifier) {
        AyahBarAction(
            modifier = Modifier.fillMaxWidth(),
            label = "استماع",
            icon = Icons.Filled.PlayArrow,
            tint = tint,
            onClickLabel = "خيارات الاستماع",
            hasMenu = true,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("من هذه الآية فما بعدها") },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tint) },
                onClick = {
                    open = false
                    onPlayFrom()
                },
            )
            DropdownMenuItem(
                text = { Text("الآية وحدها") },
                leadingIcon = { Icon(Icons.Filled.Headphones, contentDescription = null, tint = tint) },
                onClick = {
                    open = false
                    onListenAyah()
                },
            )
            DropdownMenuItem(
                text = { Text("التكرار والحفظ") },
                leadingIcon = { Icon(Icons.Filled.Repeat, contentDescription = null, tint = tint) },
                onClick = {
                    open = false
                    onRepeat()
                },
            )
        }
    }
}

/**
 * A bookmark in the ayah card's bar: its own colour (gold for the first, violet for the second)
 * even when unset, so the two are told apart; filled while this ayah holds it. A short message
 * confirms each change, since the glyph alone is small.
 */
@Composable
private fun BookmarkBarAction(
    modifier: Modifier,
    label: String,
    set: Boolean,
    tint: Color,
    name: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    AyahBarAction(
        modifier = modifier,
        label = label,
        icon = if (set) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
        tint = tint,
        onClickLabel = if (set) "إزالة $name" else "حفظ $name",
        onClick = {
            onClick()
            val message = if (set) "أُزيلت $name" else "حُفظت $name على هذه الآية"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )
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
