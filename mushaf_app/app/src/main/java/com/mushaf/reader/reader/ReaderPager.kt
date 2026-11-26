package com.mushaf.reader.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.PaddingValues
import com.mushaf.reader.data.notes.NoteEntity
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.mushaf.reader.update.AppUpdateUi
import com.mushaf.reader.ui.theme.pageRecolor
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.QuranPagePosition
import com.mushaf.reader.data.recitation.AyahRef
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun ReaderPager(
    modifier: Modifier,
    viewModel: ReaderViewModel,
    pagerState: PagerState,
    selected: AyahMarker?,
    fillScreen: Boolean,
    verticalPaging: Boolean,
    readerVisible: Boolean,
    updates: AppUpdateUi?,
    bookmarkMenu: BookmarkMenu,
    onEditNote: (NoteTarget) -> Unit,
    onOpenRepeat: (AyahRef) -> Unit,
    onShareAyah: (AyahMarker) -> Unit,
) {
    // The long-pressed ayah's bounds on screen, so its menu can sit clear of it.
    var ayahBounds by remember { mutableStateOf(Rect.Zero) }
    var longPressAyah by remember { mutableStateOf<AyahMarker?>(null) }
    var explanationRequest by remember { mutableStateOf<ExplanationRequest?>(null) }
    // Turns the page behind the explanation sheet when it steps to an ayah on another page.
    val pageScope = rememberCoroutineScope()

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
        val recolor = remember(viewModel.display.palette) { viewModel.display.palette.pageRecolor() }
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
            enabled = viewModel.display.showReadingPosition,
            readerVisible = readerVisible && longPressAyah == null && explanationRequest == null,
            scrolling = pagerState.isScrollInProgress,
            updates = updates,
        )

        // Same page content for both orientations; only the pager axis differs.
        // Each bookmarked ayah and the colour of its bookmark. Where two share an ayah the earlier
        // slot shows, so gold stays gold. A bookmark the reader hid leaves no colour.
        val showBookmarks = viewModel.display.isButtonVisible("bookmark")
        val visibleSlots = viewModel.visibleBookmarkSlots
        val bookmarkColors = remember(viewModel.bookmarks, showBookmarks, visibleSlots) {
            if (!showBookmarks) emptyMap()
            else visibleSlots.reversed()
                .mapNotNull { slot -> viewModel.bookmarks[slot]?.let { it to BookmarkSlotStyles[slot].color } }
                .toMap()
        }
        val pageContent: @Composable PagerScope.(Int) -> Unit = { index ->
            val pageNumber = index + 1
            MushafPage(
                model = viewModel.assetModel(pageNumber),
                recolor = recolor,
                palette = viewModel.display.palette,
                markers = viewModel.markersForPage(pageNumber),
                imageWidth = viewModel.imageWidth,
                imageHeight = viewModel.imageHeight,
                selectedAyah = selected,
                bookmarkColors = bookmarkColors,
                noteMarks = NoteMarks(
                    keys = if (viewModel.display.showNoteMarks) viewModel.notedVerseKeys else emptySet(),
                    alpha = viewModel.display.noteMarkOpacity / 100f,
                ),
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
                            palette = viewModel.display.palette,
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
            palette = viewModel.display.palette,
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
                bookmarkMenu = bookmarkMenu,
                onBookmark = { slot -> viewModel.toggleBookmark(slot, ayah) },
                showBookmark = viewModel.display.isButtonVisible("bookmark"),
                hasAyahNote = viewModel.noteAt(ayah.surahNumber, ayah.ayahNumber) != null,
                hasSurahNote = viewModel.noteAt(ayah.surahNumber, NoteEntity.SURAH_NOTE) != null,
                onNote = { onSurah ->
                    longPressAyah = null
                    viewModel.clearSelection()
                    onEditNote(
                        if (onSurah) NoteTarget(
                            surah = ayah.surahNumber,
                            ayah = NoteEntity.SURAH_NOTE,
                            title = "ملاحظة على سورة ${ayah.surahNameAr}",
                        ) else NoteTarget(
                            surah = ayah.surahNumber,
                            ayah = ayah.ayahNumber,
                            title = "ملاحظة على الآية ${ayah.ayahNumber.toArabicDigits()} من سورة ${ayah.surahNameAr}",
                            quote = ayah.textUthmani,
                        )
                    )
                },
                onExplain = {
                    longPressAyah = null
                    viewModel.explanation.loadExplanation(ayah)
                    explanationRequest = ExplanationRequest(ayah, viewModel.explanation.openingSource())
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
                state = viewModel.explanation.explanationState,
                onRetry = { viewModel.explanation.loadExplanation(request.ayah) },
                onSourceChosen = viewModel.explanation::updateExplanationSource,
                pinnedSources = viewModel.explanation.pinnedSources,
                onTogglePin = viewModel.explanation::togglePinnedSource,
                textScale = viewModel.explanation.explanationTextScale,
                onTextScaleStep = viewModel.explanation::stepExplanationTextScale,
                packs = viewModel.explanation.installedPacks,
                packState = viewModel.explanation.packState,
                onLoadPack = viewModel.explanation::loadPackContent,
                downloads = viewModel.packDownloads,
                loadFawaed = viewModel.explanation::pageFawaed,
                loadSurahCard = viewModel.explanation::surahCard,
                loadTajweed = viewModel.explanation::tajweed,
                previousAyah = viewModel.neighbourAyah(request.ayah, -1),
                nextAyah = viewModel.neighbourAyah(request.ayah, +1),
                onGoToAyah = { target, source ->
                    viewModel.explanation.loadExplanation(target)
                    explanationRequest = ExplanationRequest(target, source, stepped = true)
                    // The mushaf behind follows, so closing the sheet lands on the page of the
                    // last ayah read.
                    viewModel.selectAyah(target)
                    if (target.page != pagerState.currentPage + 1) {
                        pageScope.launch { pagerState.scrollToPage(target.page - 1) }
                    }
                },
                onDismiss = {
                    explanationRequest = null
                    viewModel.explanation.clearExplanation()
                    viewModel.clearSelection()
                    // After stepping through ayahs, the one the reader stopped at may not be the one
                    // they opened: it lights up briefly, as a search result does, to show where they are.
                    if (request.stepped) viewModel.flashSearchResult(request.ayah.verseKey, request.ayah.page)
                },
            )
        }
    }
}

/**
 * Stepping to the ayah before or after the one being explained, without leaving the sheet. Each
 * side names where it goes: "الآية ١٢" within the surah, the surah's name too when it crosses
 * into another. At the mushaf's two ends that side is simply empty.
 */
@Composable
internal fun AyahStepBar(
    current: AyahMarker,
    previous: AyahMarker?,
    next: AyahMarker?,
    onGo: (AyahMarker) -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        previous?.let { AyahStepButton(it, current, forward = false, onGo = onGo) }
        Spacer(Modifier.weight(1f))
        next?.let { AyahStepButton(it, current, forward = true, onGo = onGo) }
    }
}

@Composable
private fun AyahStepButton(
    target: AyahMarker,
    current: AyahMarker,
    forward: Boolean,
    onGo: (AyahMarker) -> Unit,
) {
    val place = if (target.surahNumber == current.surahNumber) "الآية ${target.ayahNumber.toArabicDigits()}"
    else "${target.surahNameAr} ${target.ayahNumber.toArabicDigits()}"
    val caption = if (forward) "التالية" else "السابقة"
    // Mirrored with the layout: «السابقة» sits at the start (the right, in Arabic) pointing back,
    // «التالية» at the end pointing on, the way the reading goes.
    val arrow = if (forward) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft
    TextButton(
        onClick = { onGo(target) },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        if (!forward) Icon(arrow, contentDescription = null, modifier = Modifier.size(22.dp))
        Column(
            horizontalAlignment = if (forward) Alignment.End else Alignment.Start,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(place, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        if (forward) Icon(arrow, contentDescription = null, modifier = Modifier.size(22.dp))
    }
}
