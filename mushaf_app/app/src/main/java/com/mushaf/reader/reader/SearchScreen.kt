package com.mushaf.reader.reader

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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

/** Full-screen search over ayah text + surah names; each hit jumps to its page. */
@Composable
fun SearchScreen(
    onSearch: suspend (String) -> SearchResults,
    onJump: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
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
                                is ResultRow.SurahRow -> MushafNavigationRow(
                                    title = "سورة ${row.surah.nameAr}",
                                    meta = "صفحة ${row.surah.firstPage.toArabicDigits()}",
                                    onClick = { onJump(row.surah.firstPage) },
                                )
                                is ResultRow.AyahRow -> AyahRow(row.result, mark, onClick = { onJump(row.result.page) })
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
    SearchTier.VERSE -> null
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
