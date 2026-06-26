package com.mushaf.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                    Text(
                        text = "الفهرس",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onAbout) {
                        Icon(Icons.Outlined.Info, contentDescription = "حول التطبيق")
                    }
                }
            }

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("السور") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("الأجزاء") })
            }

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
    val rowColor =
        if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.background
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(rowColor)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "صفحة ${page.toArabicDigits()}",
            style = MaterialTheme.typography.labelMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider()
}
