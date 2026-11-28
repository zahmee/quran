package com.mushaf.reader.reader

import android.widget.Toast
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.mushaf.reader.data.recitation.RepeatPlan
import com.mushaf.reader.ui.theme.pageRecolor
import com.mushaf.reader.update.AppUpdateUi

/**
 * Everything the reader opens above the page: full screens (index, search, notes, statistics,
 * settings, backup, about), sheets (share, repeat, fawaed, surah card) and dialogs. Drawn after
 * the page, in this order, so a later one stacks above an earlier one.
 */
@Composable
internal fun ReaderOverlays(
    ui: ReaderScreenState,
    viewModel: ReaderViewModel,
    pagerState: PagerState,
    bookmarkMenu: BookmarkMenu,
    updates: AppUpdateUi?,
    jumpToPage: (Int) -> Unit,
) {
    val pageCount = viewModel.pageCount.coerceAtLeast(1)
    val recitation = viewModel.recitation

    if (ui.showGoTo) {
        GoToPageDialog(
            pageCount = pageCount,
            onDismiss = { ui.showGoTo = false },
            onGo = { n ->
                ui.showGoTo = false
                jumpToPage(n)
            }
        )
    }

    ui.repeatFrom?.let { from ->
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
                ui.repeatFrom = null
                recitation.playPlan(RepeatPlan(from.surah, from.ayah, toAyah, each, whole))
            },
            onDismiss = { ui.repeatFrom = null },
        )
    }

    ui.share?.let { session ->
        val initialRun = session.initialRun(viewModel.markersForPage(session.page))
        if (session.picking && initialRun.isNotEmpty()) {
            AyahRangePicker(
                pageCount = pageCount,
                model = viewModel::assetModel,
                markersFor = viewModel::markersForPage,
                recolor = remember(viewModel.display.palette) { viewModel.display.palette.pageRecolor() },
                palette = viewModel.display.palette,
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
                        ui.showReciterPicker = true
                    },
                    timing = recitation::timing,
                ),
                onDismiss = {
                    ui.share = null
                    viewModel.clearSelection()
                },
            )
        }
    }

    if (ui.showReciterPicker) {
        ReciterPickerSheet(
            reciters = recitation.reciters,
            selectedId = recitation.reciter?.id,
            loading = recitation.listLoading,
            error = recitation.error,
            onRetry = recitation::requestReciters,
            onPick = { choice ->
                ui.showReciterPicker = false
                recitation.selectReciter(choice)
            },
            onDismiss = { ui.showReciterPicker = false },
        )
    }

    if (ui.showStatsScreen) {
        StatsScreen(
            stats = viewModel.fullStats,
            sessions = viewModel.sessions,
            khatmas = viewModel.khatmas,
            khatmaStartedAt = viewModel.khatmaStartedAt,
            onCompleteKhatma = { viewModel.completeKhatma(); ui.showStatsScreen = false },
            onResetKhatma = { viewModel.resetKhatmaProgress(); ui.showStatsScreen = false },
            onBack = { ui.showStatsScreen = false },
            onOpenKhatmaMap = { ui.showKhatmaMap = true },
        )
    }

    if (ui.showBookmarkManager) {
        BookmarkManageDialog(
            names = viewModel.bookmarkNames,
            hidden = viewModel.hiddenBookmarks,
            place = viewModel::bookmarkPlace,
            onSaveNames = viewModel::saveBookmarkNames,
            onSaveHidden = viewModel::saveHiddenBookmarks,
            onJump = bookmarkMenu.onJump,
            onClear = viewModel::clearBookmark,
            onDismiss = { ui.showBookmarkManager = false },
        )
    }

    if (ui.showKhatmaMap) {
        KhatmaMapScreen(
            totalPages = pageCount,
            readPages = viewModel.readPagesAll,
            visitedPages = viewModel.visitedPagesAll,
            bookmarkPage = viewModel.bookmarkJumpPage(),
            currentPage = pagerState.currentPage + 1,
            onToggleRead = { viewModel.togglePageRead(it) },
            onJump = { page ->
                ui.showKhatmaMap = false
                ui.showStatsScreen = false
                jumpToPage(page)
            },
            onBack = { ui.showKhatmaMap = false }
        )
    }

    if (ui.showIndex) {
        val loaded = viewModel.ayahLoaded
        val surahs = remember(loaded) { viewModel.surahIndex() }
        val juzs = remember(loaded) { viewModel.juzIndex() }
        IndexScreen(
            surahs = surahs,
            juzs = juzs,
            initialTab = ui.indexTab,
            currentPage = pagerState.currentPage + 1,
            onJump = { page ->
                ui.showIndex = false
                jumpToPage(page)
            },
            onAbout = { ui.showAbout = true },
            onBack = { ui.showIndex = false },
            onSurahInfo = { ui.surahCardFor = it },
        )
    }

    ui.fawaedPage?.let { page ->
        PageFawaedSheet(
            page = page,
            surahName = remember(page, viewModel.ayahLoaded) {
                viewModel.surahIndex().lastOrNull { it.firstPage <= page }?.nameAr
            },
            load = viewModel.explanation::pageFawaed,
            textScale = viewModel.explanation.explanationTextScale,
            onTextScaleStep = viewModel.explanation::stepExplanationTextScale,
            onDismiss = { ui.fawaedPage = null },
        )
    }

    ui.surahCardFor?.let { surah ->
        SurahCardSheet(
            surah = surah,
            load = viewModel.explanation::surahCard,
            textScale = viewModel.explanation.explanationTextScale,
            onTextScaleStep = viewModel.explanation::stepExplanationTextScale,
            onOpenSurah = {
                ui.surahCardFor = null
                ui.showIndex = false
                jumpToPage(surah.firstPage)
            },
            onDismiss = { ui.surahCardFor = null },
        )
    }

    if (ui.showSettings) {
        SettingsScreen(
            isVisible = { id -> viewModel.display.isButtonVisible(id) },
            onToggle = { id, v -> viewModel.display.setButtonVisible(id, v) },
            paletteId = viewModel.display.themeId,
            onPaletteChange = { viewModel.display.updateThemeId(it) },
            isInBar = { id -> viewModel.display.isButtonInBar(id) },
            onSetInBar = { id, v -> viewModel.display.setButtonInBar(id, v) },
            barOverflow = ui.barOverflow,
            buttonColor = { id -> viewModel.display.buttonColor(id) },
            onButtonColorChange = { id, c -> viewModel.display.updateButtonColor(id, c) },
            bigButtons = viewModel.display.bigButtons,
            onBigButtonsChange = { viewModel.display.updateBigButtons(it) },
            verticalPaging = viewModel.display.verticalPaging,
            onVerticalPagingChange = { viewModel.display.updateVerticalPaging(it) },
            keepScreenOn = viewModel.display.keepScreenOn,
            onKeepScreenOnChange = { viewModel.display.updateKeepScreenOn(it) },
            showClock = viewModel.display.showClock,
            onShowClockChange = { viewModel.display.updateShowClock(it) },
            showSessionTimer = viewModel.display.showSessionTimer,
            onShowSessionTimerChange = { viewModel.display.updateShowSessionTimer(it) },
            showSurahNumber = viewModel.display.showSurahNumber,
            onShowSurahNumberChange = { viewModel.display.updateShowSurahNumber(it) },
            showSurahAyahCount = viewModel.display.showSurahAyahCount,
            onShowSurahAyahCountChange = { viewModel.display.updateShowSurahAyahCount(it) },
            showSurahProgress = viewModel.display.showSurahProgress,
            onShowSurahProgressChange = { viewModel.display.updateShowSurahProgress(it) },
            showJuzProgressPercent = viewModel.display.showJuzProgressPercent,
            onShowJuzProgressPercentChange = { viewModel.display.updateShowJuzProgressPercent(it) },
            showJuzProgressPages = viewModel.display.showJuzProgressPages,
            onShowJuzProgressPagesChange = { viewModel.display.updateShowJuzProgressPages(it) },
            showReadingPosition = viewModel.display.showReadingPosition,
            onShowReadingPositionChange = viewModel.display::updateShowReadingPosition,
            clockColor = viewModel.display.clockColor,
            onClockColorChange = { viewModel.display.updateClockColor(it) },
            sessionTimerColor = viewModel.display.sessionTimerColor,
            onSessionTimerColorChange = { viewModel.display.updateSessionTimerColor(it) },
            showButtonPage = viewModel.display.showButtonPage,
            onShowButtonPageChange = { viewModel.display.updateShowButtonPage(it) },
            buttonPageColor = viewModel.display.buttonPageColor,
            onButtonPageColorChange = { viewModel.display.updateButtonPageColor(it) },
            showHeaderButtonOpacity = viewModel.display.showHeaderButtonOpacity,
            onShowHeaderButtonOpacityChange = { viewModel.display.updateShowHeaderButtonOpacity(it) },
            showBottomJuzBar = viewModel.display.showBottomJuzBar,
            onShowBottomJuzBarChange = { viewModel.display.updateShowBottomJuzBar(it) },
            bottomJuzBarColor = viewModel.display.bottomJuzBarColor,
            onBottomJuzBarColorChange = { viewModel.display.updateBottomJuzBarColor(it) },
            bottomJuzBarThickness = viewModel.display.bottomJuzBarThickness,
            onBottomJuzBarThicknessChange = { viewModel.display.updateBottomJuzBarThickness(it) },
            bottomJuzBarOpacity = viewModel.display.bottomJuzBarOpacity,
            onBottomJuzBarOpacityChange = { viewModel.display.updateBottomJuzBarOpacity(it) },
            showTopSurahBar = viewModel.display.showTopSurahBar,
            onShowTopSurahBarChange = { viewModel.display.updateShowTopSurahBar(it) },
            topSurahBarColor = viewModel.display.topSurahBarColor,
            onTopSurahBarColorChange = { viewModel.display.updateTopSurahBarColor(it) },
            topSurahBarThickness = viewModel.display.topSurahBarThickness,
            onTopSurahBarThicknessChange = { viewModel.display.updateTopSurahBarThickness(it) },
            topSurahBarOpacity = viewModel.display.topSurahBarOpacity,
            onTopSurahBarOpacityChange = { viewModel.display.updateTopSurahBarOpacity(it) },
            showPageSideIndicator = viewModel.display.showPageSideIndicator,
            onShowPageSideIndicatorChange = { viewModel.display.updateShowPageSideIndicator(it) },
            pageSideIndicatorColor = viewModel.display.pageSideIndicatorColor,
            onPageSideIndicatorColorChange = { viewModel.display.updatePageSideIndicatorColor(it) },
            pageSideIndicatorThickness = viewModel.display.pageSideIndicatorThickness,
            onPageSideIndicatorThicknessChange = { viewModel.display.updatePageSideIndicatorThickness(it) },
            pageSideIndicatorLength = viewModel.display.pageSideIndicatorLength,
            onPageSideIndicatorLengthChange = { viewModel.display.updatePageSideIndicatorLength(it) },
            pageSideIndicatorOpacity = viewModel.display.pageSideIndicatorOpacity,
            onPageSideIndicatorOpacityChange = { viewModel.display.updatePageSideIndicatorOpacity(it) },
            edgeMargin = viewModel.display.edgeMargin,
            onEdgeMarginChange = { viewModel.display.updateEdgeMargin(it) },
            showNoteMarks = viewModel.display.showNoteMarks,
            onShowNoteMarksChange = { viewModel.display.updateShowNoteMarks(it) },
            noteMarkOpacity = viewModel.display.noteMarkOpacity,
            onNoteMarkOpacityChange = { viewModel.display.updateNoteMarkOpacity(it) },
            onBackup = { ui.showBackup = true },
            onAbout = { ui.showAbout = true },
            onTafsirLibrary = { ui.showTafsirLibrary = true },
            onClearAllStats = { viewModel.clearAllStats() },
            menuTabExtra = { AnaaSettingsPanel(viewModel.anaa) },
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
                        ui.showReciterPicker = true
                    },
                )
            },
            onBack = { ui.showSettings = false }
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

    if (ui.showBackup) {
        BackupScreen(
            viewModel = viewModel,
            onBack = { ui.showBackup = false },
            onOpenPlace = { point ->
                // Back to the page itself, through every screen stacked above it.
                ui.showBackup = false
                ui.showSettings = false
                ui.showAbout = false
                viewModel.goToSyncedPlace(point)
            },
        )
    }

    if (ui.showTafsirLibrary) {
        TafsirLibraryDialog(downloads = viewModel.packDownloads, onDismiss = { ui.showTafsirLibrary = false })
    }

    if (viewModel.showWhatsNew) {
        WhatsNewDialog(onDismiss = viewModel::dismissWhatsNew)
    }

    if (ui.showAbout) {
        AboutScreen(
            onBack = { ui.showAbout = false },
            updates = updates,
            // Backup renders below About in this stack, so About has to step aside for it.
            onOpenBackup = {
                ui.showAbout = false
                ui.showBackup = true
            },
        )
    }

    if (ui.showSearch) {
        SearchScreen(
            session = ui.search,
            onSearch = { q -> viewModel.search(q) },
            onJump = { page ->
                ui.showSearch = false
                jumpToPage(page)
            },
            onJumpToAyah = { result ->
                ui.showSearch = false
                jumpToPage(result.page)
                viewModel.flashSearchResult(result.verseKey, result.page)
            },
            onBack = { ui.showSearch = false },
            onRootResults = viewModel::rootResults,
        )
    }

    if (ui.showNotes) {
        NotesScreen(
            notes = viewModel.notes,
            surahName = viewModel::surahNameAr,
            onOpen = { note ->
                viewModel.notePage(note)?.let { page ->
                    ui.showNotes = false
                    jumpToPage(page)
                    note.verseKey?.let { viewModel.flashSearchResult(it, page) }
                }
            },
            onEdit = { note ->
                val name = viewModel.surahNameAr(note.surah)
                ui.noteTarget = NoteTarget(
                    surah = note.surah,
                    ayah = note.ayah,
                    title = "ملاحظة على ${notePlaceTitle(note.surah, note.ayah, name)}",
                    quote = note.verseKey?.let(viewModel::ayahText),
                )
            },
            onBack = { ui.showNotes = false },
        )
    }

    ui.noteTarget?.let { target ->
        NoteEditorDialog(
            target = target,
            existing = viewModel.noteAt(target.surah, target.ayah),
            onSave = { text -> viewModel.saveNote(target.surah, target.ayah, text) },
            onDelete = { viewModel.deleteNote(target.surah, target.ayah) },
            onDismiss = { ui.noteTarget = null },
        )
    }

    // A note that failed to save or delete is said out loud, never dropped quietly.
    val noteError = viewModel.noteError
    val noteContext = LocalContext.current
    LaunchedEffect(noteError) {
        if (noteError != null) {
            Toast.makeText(noteContext, noteError, Toast.LENGTH_LONG).show()
            viewModel.clearNoteError()
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
                keyboardActions = KeyboardActions(onGo = { if (valid) onGo(target) }),
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
