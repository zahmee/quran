package com.mushaf.reader.reader

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mushaf.reader.data.content.SurahCard
import com.mushaf.reader.data.content.SurahCardText
import com.mushaf.reader.ui.theme.ReadingType
import kotlinx.coroutines.launch

/*
 * The Tafsir Center's page fawaed and surah introductions, wherever the reader meets them: the
 * header's «فوائد الصفحة» button and the index's info button open them in a sheet of their own, and
 * the explanation sheet lists them among its sources.
 */

/** A Quran quote in a faidah, between ornate brackets: drawn like the quotes in the tafsirs. */
private val FaidahQuote = Regex("﴿[^﴾]*﴾")

private fun formatFaidah(raw: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    FaidahQuote.findAll(raw).forEach { match ->
        append(raw.substring(cursor, match.range.first))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(match.value) }
        cursor = match.range.last + 1
    }
    append(raw.substring(cursor))
}

/** What is copied or shared from a page's fawaed. */
internal fun fawaedShareText(page: Int, fawaed: List<String>): String? =
    fawaed.takeIf { it.isNotEmpty() }?.let { list ->
        "فوائد الصفحة ${page.toArabicDigits()}\n\n" +
            list.joinToString("\n") { "• $it" } +
            "\n\n${CenterAttribution.FAWAED_CREDIT}"
    }

/** What is copied or shared from a surah card: its facts and aim, not the long sections. */
internal fun surahCardShareText(card: SurahCard, surahName: String, ayahCount: Int): String =
    "عن سورة $surahName\n\n${card.summary(surahName, ayahCount)}\n\n${CenterAttribution.SURAH_CARD_CREDIT}"

/** The fawaed as a list; null while they load. */
@Composable
internal fun PageFawaedContent(
    page: Int,
    fawaed: List<String>?,
    scale: Float,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (fawaed == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    val body = ReadingType.bodyLarge.scaled(scale)
    LazyColumn(
        modifier = modifier.nestedScroll(KeepFlingInSheet),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "ما يُستفاد من آيات الصفحة، من «المختصر في التفسير»",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (fawaed.isEmpty()) {
            item { EmptyExplanationMessage("لا فوائد مسجّلة لهذه الصفحة.") }
        }
        itemsIndexed(fawaed) { _, faidah ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 11.dp * scale)
                        .size(7.dp)
                        .background(accent, CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Text(text = formatFaidah(faidah, accent), style = body, modifier = Modifier.weight(1f))
            }
        }
        item { SourcesLink(onOpenSources) }
    }
}

/**
 * A surah's card: its facts and aim at the top, then its long sections folded, each opened on its own.
 * An open section lays out paragraph by paragraph, so al-Baqara's hundred-odd pages of merits scroll
 * like any list. [card] is null while it loads; [loaded] then tells a missing card from a slow one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SurahCardContent(
    surahNumber: Int,
    ayahCount: Int,
    card: SurahCard?,
    loaded: Boolean,
    scale: Float,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSurah: (() -> Unit)? = null,
) {
    if (card == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loaded) EmptyExplanationMessage("تعذّر فتح بطاقة السورة.") else CircularProgressIndicator()
        }
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    // Which sections are open, by position; saved so turning the phone keeps them open.
    var open by rememberSaveable(surahNumber) { mutableStateOf(emptyList<Int>()) }
    LazyColumn(
        modifier = modifier.nestedScroll(KeepFlingInSheet),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FactChip(card.revelationPlace, emphasized = true)
                FactChip(SurahCardText.ayahs(ayahCount))
                FactChip("ترتيبها في النزول ${card.revelationOrder.toArabicDigits()}")
                FactChip("من ${card.surahClass}")
                if (card.sajdahAyahs.isNotEmpty()) {
                    FactChip("سجدة: ${SurahCardText.sajdahPlaces(card.sajdahAyahs)}")
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "مقصد السورة",
                        style = MaterialTheme.typography.labelLarge.scaled(scale),
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = card.goal,
                        modifier = Modifier.padding(top = 4.dp),
                        style = ReadingType.bodyLarge.scaled(scale),
                    )
                }
            }
        }
        card.sections.forEachIndexed { index, section ->
            val isOpen = index in open
            item(key = "section-$index") {
                SectionHeader(
                    title = section.title,
                    open = isOpen,
                    scale = scale,
                    onToggle = { open = if (isOpen) open - index else open + index },
                )
            }
            if (isOpen) sectionBody(index, section.row, accent, scale)
        }
        if (onOpenSurah != null) {
            item {
                TextButton(onClick = onOpenSurah, modifier = Modifier.fillMaxWidth()) {
                    Text("الانتقال إلى السورة")
                }
            }
        }
        item { SourcesLink(onOpenSources) }
    }
}

@Composable
private fun FactChip(text: String, emphasized: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SectionHeader(title: String, open: Boolean, scale: Float, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    onClickLabel = if (open) "طيّ $title" else "إظهار $title",
                    role = Role.Button,
                    onClick = onToggle,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall.scaled(scale),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = if (open) 180f else 0f },
            )
        }
    }
}

/** An open section, one item per paragraph, then its notes. */
private fun LazyListScope.sectionBody(
    index: Int,
    row: com.mushaf.reader.data.content.TafsirPassageRow,
    accent: Color,
    scale: Float,
) {
    val paragraphs = row.text.split('\n').filter { it.isNotBlank() }
    itemsIndexed(paragraphs, key = { line, _ -> "section-$index-$line" }) { _, paragraph ->
        val heading = SurahCardText.SubHeading.matchEntire(paragraph)
        val starred = SurahCardText.StarredLine.matchEntire(paragraph)
        when {
            heading != null -> Text(
                text = heading.groupValues[1],
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp),
                style = MaterialTheme.typography.titleSmall.scaled(scale),
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            SurahCardText.Divider.matches(paragraph) -> Text(
                text = "⁂",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            starred != null -> Text(
                text = formatSaadiText(starred.groupValues[1], accent),
                modifier = Modifier.padding(horizontal = 6.dp),
                style = ReadingType.bodyMedium.scaled(scale),
                fontWeight = FontWeight.Bold,
            )
            else -> Text(
                text = formatSaadiText(paragraph, accent),
                modifier = Modifier.padding(horizontal = 6.dp),
                style = ReadingType.bodyMedium.scaled(scale),
            )
        }
    }
    if (row.notes.isNotEmpty()) {
        item(key = "section-$index-notes") { SaadiNotes(notes = row.notes, scale = scale) }
    }
}

/** The sheets' shared frame: a title and a line under it, the text tools, a close button, the body. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CenterExtrasSheet(
    title: String,
    subtitle: String,
    shareTitle: String,
    shareText: String?,
    textScale: Int,
    onTextScaleStep: (Int) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable (onOpenSources: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showSources by rememberSaveable { mutableStateOf(false) }
    if (showSources) SourcesDialog(onDismiss = { showSources = false })
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
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 6.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SheetTextTools(text = shareText, title = shareTitle, scale = textScale, onStep = onTextScaleStep)
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
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
            Box(modifier = Modifier.weight(1f)) { content { showSources = true } }
        }
    }
}

/** The header's «فوائد الصفحة»: the fawaed of the page the reader is on. */
@Composable
internal fun PageFawaedSheet(
    page: Int,
    surahName: String?,
    load: suspend (Int) -> List<String>,
    textScale: Int,
    onTextScaleStep: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val fawaed by produceState<List<String>?>(initialValue = null, page) { value = load(page) }
    CenterExtrasSheet(
        title = "فوائد الصفحة ${page.toArabicDigits()}",
        subtitle = surahName?.let { "سورة $it" } ?: "المختصر في التفسير",
        shareTitle = "فوائد الصفحة",
        shareText = fawaed?.let { fawaedShareText(page, it) },
        textScale = textScale,
        onTextScaleStep = onTextScaleStep,
        onDismiss = onDismiss,
    ) { openSources ->
        PageFawaedContent(
            page = page,
            fawaed = fawaed,
            scale = textScale / 100f,
            onOpenSources = openSources,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The index's info button: a surah's card, with the way to the surah itself. */
@Composable
internal fun SurahCardSheet(
    surah: SurahEntry,
    load: suspend (Int) -> SurahCard?,
    textScale: Int,
    onTextScaleStep: (Int) -> Unit,
    onOpenSurah: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by produceState(initialValue = SurahCardLoad(), surah.number) {
        value = SurahCardLoad(card = load(surah.number), loaded = true)
    }
    CenterExtrasSheet(
        title = "سورة ${surah.nameAr}",
        subtitle = "السورة ${surah.number.toArabicDigits()} • تبدأ في الصفحة ${surah.firstPage.toArabicDigits()}",
        shareTitle = "عن سورة ${surah.nameAr}",
        shareText = state.card?.let { surahCardShareText(it, surah.nameAr, surah.ayahCount) },
        textScale = textScale,
        onTextScaleStep = onTextScaleStep,
        onDismiss = onDismiss,
    ) { openSources ->
        SurahCardContent(
            surahNumber = surah.number,
            ayahCount = surah.ayahCount,
            card = state.card,
            loaded = state.loaded,
            scale = textScale / 100f,
            onOpenSources = openSources,
            onOpenSurah = onOpenSurah,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

internal data class SurahCardLoad(val card: SurahCard? = null, val loaded: Boolean = false)
