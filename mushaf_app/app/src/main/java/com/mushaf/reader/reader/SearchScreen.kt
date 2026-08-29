package com.mushaf.reader.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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

/** Full-screen search over ayah text + surah names; each hit jumps to its page. */
@Composable
fun SearchScreen(
    onSearch: suspend (String) -> List<SearchResult>,
    onJump: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
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
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(SearchDebounceMs)
        results = onSearch(trimmed)
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
                results.isEmpty() -> Hint("لا توجد نتائج")
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(results) { index, r ->
                        // The widened matches are labelled once, where they begin. A reader who
                        // found their ayah above the line never has to weigh them.
                        // index == 0 is the real case where the exact pass found nothing at all.
                        if (r.expanded && (index == 0 || !results[index - 1].expanded)) {
                            ExpandedResultsLabel()
                        }
                        SearchRow(r, onClick = { onJump(r.page) })
                    }
                }
            }
        }
    }
}

/**
 * Marks where the exact matches end and the stemmed ones begin.
 *
 * Named rather than merely ruled off: a widened match can be a different word that happens to
 * share a stem, and the reader is owed that warning before they read an ayah as an answer.
 */
@Composable
private fun ExpandedResultsLabel() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MushafSoftDivider(verticalPadding = 6.dp)
        Text(
            text = "نتائج قريبة",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "مشتقة من الكلمة نفسها، وقد لا تطابق ما تبحث عنه تمامًا",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
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
private fun SearchRow(r: SearchResult, onClick: () -> Unit) {
    MushafNavigationRow(
        title = "${r.surahNameAr} • آية ${r.ayahNumber.toArabicDigits()}",
        meta = "صفحة ${r.page.toArabicDigits()}",
        supportingText = r.text.takeIf { it.isNotBlank() },
        supportingStyle = ReadingType.bodyMedium,
        onClick = onClick,
    )
}
