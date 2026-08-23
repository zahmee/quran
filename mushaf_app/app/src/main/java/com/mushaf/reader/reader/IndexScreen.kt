package com.mushaf.reader.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.mushaf.reader.ui.components.MushafNavigationRow
import com.mushaf.reader.ui.components.MushafSegmentedTabs
import com.mushaf.reader.ui.components.MushafTopBar

/** Full-screen navigation index: surahs + juz, each jumps to its starting page. */
@Composable
fun IndexScreen(
    surahs: List<SurahEntry>,
    juzs: List<JuzEntry>,
    onJump: (Int) -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit,
    initialTab: Int = 0,
    currentPage: Int = 1,
) {
    var tab by remember { mutableStateOf(initialTab) }

    // The entry the reader is currently sitting on: the last one that starts at or before the page.
    val surahTarget = remember(surahs, currentPage) {
        surahs.indexOfLast { it.firstPage <= currentPage }.coerceAtLeast(0)
    }
    val juzTarget = remember(juzs, currentPage) {
        juzs.indexOfLast { it.firstPage <= currentPage }.coerceAtLeast(0)
    }

    val surahListState = rememberLazyListState()
    val juzListState = rememberLazyListState()

    // On open / data load / tab switch, bring the current surah or juz into view (one row of lead-in).
    LaunchedEffect(tab, surahs.size, juzs.size, currentPage) {
        if (tab == 0) {
            if (surahs.isNotEmpty()) surahListState.scrollToItem((surahTarget - 1).coerceAtLeast(0))
        } else {
            if (juzs.isNotEmpty()) juzListState.scrollToItem((juzTarget - 1).coerceAtLeast(0))
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            MushafTopBar(
                title = "الفهرس",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onAbout, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "حول التطبيق",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            )

            MushafSegmentedTabs(
                labels = listOf("السور", "الأجزاء"),
                selectedIndex = tab,
                onSelected = { tab = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )

            if (surahs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("جارٍ التحميل…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            when (tab) {
                0 -> LazyColumn(state = surahListState, modifier = Modifier.fillMaxSize()) {
                    items(surahs) { s ->
                        IndexRow(
                            title = "${s.number.toArabicDigits()}.  سورة ${s.nameAr} (${s.ayahCount.toArabicDigits()} آية)",
                            page = s.firstPage,
                            isCurrent = surahs.getOrNull(surahTarget) === s,
                            onClick = { onJump(s.firstPage) }
                        )
                    }
                }
                else -> LazyColumn(state = juzListState, modifier = Modifier.fillMaxSize()) {
                    items(juzs) { j ->
                        IndexRow(
                            title = "الجزء ${j.number.toArabicDigits()} (${j.ayahCount.toArabicDigits()} آية)",
                            page = j.firstPage,
                            isCurrent = juzs.getOrNull(juzTarget) === j,
                            onClick = { onJump(j.firstPage) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexRow(title: String, page: Int, isCurrent: Boolean, onClick: () -> Unit) {
    MushafNavigationRow(
        title = title,
        meta = "صفحة ${page.toArabicDigits()}",
        selected = isCurrent,
        onClick = onClick,
    )
}
