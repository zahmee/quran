package com.mushaf.reader.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.RootSearch
import com.mushaf.reader.data.SearchTier
import com.mushaf.reader.ui.components.MushafNavigationRow
import com.mushaf.reader.ui.components.MushafSoftDivider
import com.mushaf.reader.ui.components.MushafTopBar
import com.mushaf.reader.ui.theme.ReadingType
import kotlinx.coroutines.delay

/** Shortest query worth scanning the mushaf for. One letter matches most of the text and tells the
 *  reader nothing; two is the shortest real Arabic word (من، في، ما). */
private const val MinQueryLength = 2

/** How long the typing has to pause before a search runs, so a fast typist triggers one scan. */
private const val SearchDebounceMs = 220L

/** Results together with the query they answer, so the labels describe that query and not one the
 *  reader has typed since. */
private class Answer(val query: String, val results: SearchResults)

/** Full-screen search over ayah text + surah names; a surah jumps to its page, an ayah to its page with
 *  the ayah flashed there. */
@Composable
fun SearchScreen(
    onSearch: suspend (String) -> SearchResults,
    onJump: (Int) -> Unit,
    onJumpToAyah: (SearchResult) -> Unit,
    onBack: () -> Unit,
    onRootResults: suspend (RootSearch.Root, Int?) -> RootResults,
) {
    var query by remember { mutableStateOf("") }
    // The root whose words are listed, over the results it was chosen from; Back returns to them.
    var openRoot by remember { mutableStateOf<RootSearch.Root?>(null) }
    BackHandler(enabled = openRoot != null) { openRoot = null }
    var answer by remember { mutableStateOf(Answer("", SearchResults())) }
    var searching by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val trimmed = query.trim()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // Keyed on the query, so a new keystroke cancels the pending scan before it starts — that is
    // the debounce. The scan itself runs off the main thread inside onSearch.
    LaunchedEffect(trimmed) {
        if (trimmed.length < MinQueryLength) {
            answer = Answer(trimmed, SearchResults())
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(SearchDebounceMs)
        answer = Answer(trimmed, onSearch(trimmed))
        searching = false
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            val root = openRoot
            if (root != null) {
                MushafTopBar(title = "جذر «${root.root}»", onBack = { openRoot = null })
                RootResultsView(root = root, load = onRootResults, onJumpToAyah = onJumpToAyah)
                return@Column
            }
            MushafTopBar(title = "البحث", onBack = onBack)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .focusRequester(focusRequester),
                shape = MaterialTheme.shapes.medium,
                placeholder = { Text("ابحث عن آية أو سورة") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })
            )

            when {
                trimmed.isEmpty() -> Hint("اكتب كلمة للبحث في نص المصحف أو اسم السورة")
                trimmed.length < MinQueryLength -> Hint("اكتب حرفين على الأقل")
                searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                answer.results.isEmpty -> Hint("لا توجد نتائج")
                else -> {
                    val rows = remember(answer) { searchRows(answer) }
                    val mark = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                is ResultRow.Header -> GroupHeader(row, first = row === rows.first())
                                is ResultRow.RootRow -> RootSuggestionRow(row.root, onClick = { openRoot = row.root })
                                is ResultRow.SurahRow -> MushafNavigationRow(
                                    title = "سورة ${row.surah.nameAr}",
                                    meta = "صفحة ${row.surah.firstPage.toArabicDigits()}",
                                    onClick = { onJump(row.surah.firstPage) },
                                )
                                is ResultRow.AyahRow -> AyahRow(row.result, mark, onClick = { onJumpToAyah(row.result) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One line of the result list: a group header, a surah, or an ayah. */
private sealed interface ResultRow {
    val key: String

    class Header(val title: String, val count: String, val note: String?) : ResultRow {
        override val key get() = "header:$title"
    }

    class RootRow(val root: RootSearch.Root) : ResultRow {
        override val key get() = "root:${root.id}"
    }

    class SurahRow(val surah: SurahSearchResult) : ResultRow {
        override val key get() = "surah:${surah.number}"
    }

    class AyahRow(val result: SearchResult) : ResultRow {
        override val key get() = "ayah:${result.verseKey}"
    }
}

/**
 * Flatten the results into rows, opening each group with a header that says what it holds and how
 * many ayahs are in it.
 *
 * The groups arrive already ordered (surahs, then ayahs by [SearchTier]), so a header goes wherever
 * the tier changes. A verse-key jump gets no header: it is one ayah the reader named, not a count.
 */
private fun searchRows(answer: Answer): List<ResultRow> = buildList {
    val results = answer.results
    if (results.roots.isNotEmpty()) {
        add(ResultRow.Header("البحث بالجذر", "", note = "كل الكلمات المشتقة من الجذر، بكل صيغها"))
        results.roots.forEach { add(ResultRow.RootRow(it)) }
    }
    if (results.surahs.isNotEmpty()) {
        add(ResultRow.Header("السور", surahsLabel(results.surahs.size), note = null))
        results.surahs.forEach { add(ResultRow.SurahRow(it)) }
    }
    val counts = results.ayahs.groupingBy { it.tier }.eachCount()
    val phrase = ' ' in answer.query
    var tier: SearchTier? = null
    for (r in results.ayahs) {
        if (r.tier != tier) {
            tier = r.tier
            tierHeader(r.tier, counts[r.tier] ?: 0, phrase)?.let(::add)
        }
        add(ResultRow.AyahRow(r))
    }
}

private fun tierHeader(tier: SearchTier, count: Int, phrase: Boolean): ResultRow.Header? = when (tier) {
    SearchTier.VERSE, SearchTier.ROOT -> null
    SearchTier.WORD -> ResultRow.Header(
        title = if (phrase) "العبارة نفسها" else "الكلمة نفسها",
        count = ayahsLabel(count),
        note = null,
    )
    // Named rather than merely ruled off: these ayahs hold the letters, not the word, and the
    // reader should know that before reading one as an answer.
    SearchTier.PARTIAL -> ResultRow.Header(
        title = "ضمن كلمات أخرى",
        count = ayahsLabel(count),
        note = "الحروف التي كتبتها جزء من كلمة مختلفة",
    )
    // A widened match can be a different word that happens to share a stem.
    SearchTier.STEMMED -> ResultRow.Header(
        title = "نتائج قريبة",
        count = ayahsLabel(count),
        note = "مشتقة من الكلمة نفسها، وقد لا تطابق ما تبحث عنه تمامًا",
    )
}

/** Arabic counted noun: سورة واحدة، سورتان، 3–10 سور، 11+ سورة. */
private fun surahsLabel(n: Int): String = when {
    n == 1 -> "سورة واحدة"
    n == 2 -> "سورتان"
    n in 3..10 -> "${n.toArabicDigits()} سور"
    else -> "${n.toArabicDigits()} سورة"
}

@Composable
private fun GroupHeader(header: ResultRow.Header, first: Boolean) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
        if (first) Spacer(Modifier.height(10.dp)) else MushafSoftDivider(verticalPadding = 6.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = header.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = header.count,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        header.note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AyahRow(r: SearchResult, mark: SpanStyle, onClick: () -> Unit) {
    val text = remember(r, mark) { markedSnippet(r.text, r.highlights, mark) }
    MushafNavigationRow(
        title = "${r.surahNameAr} • آية ${r.ayahNumber.toArabicDigits()}",
        meta = "صفحة ${r.page.toArabicDigits()}",
        supportingText = text.takeIf { it.isNotBlank() },
        supportingStyle = ReadingType.bodyMedium,
        onClick = onClick,
    )
}

/** Words shown before the first marked word when the snippet has to start mid-ayah. */
private const val LeadWords = 3

/** How many words may precede the first marked word before the snippet is cut — the row shows two
 *  lines, and a mark past them would be invisible. */
private const val MaxWordsBeforeMark = 6

/**
 * The ayah text with its matched words marked, starting a few words before the first one when it
 * sits too deep in the ayah to show within the row's two lines.
 */
private fun markedSnippet(text: String, highlights: List<IntRange>, mark: SpanStyle): AnnotatedString {
    val body = text.trimEnd()
    var begin = body.indexOfFirst { it != ' ' }.coerceAtLeast(0)
    val firstMark = highlights.firstOrNull()?.first
    if (firstMark != null) {
        val wordStarts = (begin until minOf(firstMark, body.length))
            .filter { body[it] != ' ' && (it == 0 || body[it - 1] == ' ') }
        if (wordStarts.size > MaxWordsBeforeMark) begin = wordStarts[wordStarts.size - LeadWords]
    }
    val cut = begin > 0 && body.substring(0, begin).isNotBlank()
    return buildAnnotatedString {
        if (cut) append("… ")
        val shift = length - begin
        append(body.substring(begin))
        for (h in highlights) {
            val start = maxOf(h.first, begin)
            val end = minOf(h.last + 1, body.length)
            if (start < end) addStyle(mark, start + shift, end + shift)
        }
    }
}

/** «موضع واحد»، «موضعان»، «٣ مواضع»، «٣٣٩ موضعًا». */
private fun placesLabel(n: Int): String = when {
    n == 1 -> "موضع واحد"
    n == 2 -> "موضعان"
    n % 100 in 3..10 -> "${n.toArabicDigits()} مواضع"
    else -> "${n.toArabicDigits()} موضعًا"
}

/** «صيغة واحدة»، «صيغتين»، «٣ صيغ»، «٤٧ صيغة» — after «على», hence the genitive dual. */
private fun formsLabel(n: Int): String = when {
    n == 1 -> "صيغة واحدة"
    n == 2 -> "صيغتين"
    n % 100 in 3..10 -> "${n.toArabicDigits()} صيغ"
    else -> "${n.toArabicDigits()} صيغة"
}

/** A root offered for the typed word: how often it occurs, and its commonest forms to recognise it by. */
@Composable
private fun RootSuggestionRow(root: RootSearch.Root, onClick: () -> Unit) {
    MushafNavigationRow(
        title = "جذر «${root.root}»",
        meta = placesLabel(root.wordCount),
        supportingText = "في ${ayahsLabel(root.ayahCount)} • منه: " +
            root.forms.take(4).joinToString("، ") { it.text },
        onClick = onClick,
    )
}

/**
 * Every ayah holding a word from [root], the words marked. The forms above narrow the list to one of
 * them — «النار» and «النور» are both from نور — and «الكل» widens it again.
 */
@Composable
private fun RootResultsView(
    root: RootSearch.Root,
    load: suspend (RootSearch.Root, Int?) -> RootResults,
    onJumpToAyah: (SearchResult) -> Unit,
) {
    var form by remember(root.id) { mutableStateOf<Int?>(null) }
    var results by remember(root.id) { mutableStateOf<RootResults?>(null) }
    LaunchedEffect(root.id, form) {
        results = null
        results = load(root, form)
    }
    val mark = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    )
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "${placesLabel(root.wordCount)} في ${ayahsLabel(root.ayahCount)}، " +
                "على ${formsLabel(root.forms.size)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = form == null,
                    onClick = { form = null },
                    label = { Text("الكل") },
                )
            }
            itemsIndexed(root.forms) { index, f ->
                FilterChip(
                    selected = form == index,
                    onClick = { form = if (form == index) null else index },
                    label = { Text("${f.text} (${f.count.toArabicDigits()})") },
                )
            }
        }
        val shown = results
        when {
            shown == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            shown.ayahs.isEmpty() -> Hint("لا توجد نتائج")
            else -> {
                Text(
                    text = ayahsLabel(shown.ayahs.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(shown.ayahs, key = { "root-ayah:${it.verseKey}" }) { r ->
                        AyahRow(r, mark, onClick = { onJumpToAyah(r) })
                    }
                }
            }
        }
    }
}
