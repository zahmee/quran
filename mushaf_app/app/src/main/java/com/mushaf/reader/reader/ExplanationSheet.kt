package com.mushaf.reader.reader

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mushaf.reader.ui.theme.ReadingType
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.SurahAyahCounts
import com.mushaf.reader.data.content.MukhtasarText
import com.mushaf.reader.data.content.SharedText
import com.mushaf.reader.data.packs.InstalledPack
import com.mushaf.reader.data.packs.PackContent
import com.mushaf.reader.data.packs.PackDownloads
import com.mushaf.reader.data.content.SurahCard
import com.mushaf.reader.data.content.TajweedNote
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * What the sheet lays out for a source. [menuLabel] names it, above its text and in the sources
 * menu; [title] heads anything copied or shared from it; [credit] closes that.
 */
internal enum class ExplanationTab(
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

internal data class ExplanationRequest(
    val ayah: AyahMarker,
    /** An [ExplanationSources] id. */
    val initialSource: String,
    /** True once the reader has stepped to this ayah from another inside the sheet. */
    val stepped: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AyahExplanationSheet(
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
    /** The ayahs either side of this one in mushaf order; null at the mushaf's two ends. */
    previousAyah: AyahMarker?,
    nextAyah: AyahMarker?,
    /** Moves the sheet to another ayah, keeping the source being read. */
    onGoToAyah: (ayah: AyahMarker, source: String) -> Unit,
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
            // Keyed on the ayah, so stepping to the next one opens its text at the top, not
            // scrolled as far down as the last one was read.
            key(request.ayah.verseKey) {
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

            // The neighbouring ayahs, at the foot of the sheet: where the reader's eye and thumb are
            // when they finish the text, whichever source it is in.
            AyahStepBar(
                current = request.ayah,
                previous = previousAyah,
                next = nextAyah,
                onGo = { onGoToAyah(it, source) },
            )
        }
    }
}
