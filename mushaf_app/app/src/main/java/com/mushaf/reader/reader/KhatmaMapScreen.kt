package com.mushaf.reader.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mushaf.reader.data.JuzLayout
import com.mushaf.reader.ui.components.MushafPanel
import com.mushaf.reader.ui.components.MushafSegmentedTabs
import com.mushaf.reader.ui.components.MushafTopBar

@Composable
fun KhatmaMapScreen(
    totalPages: Int,
    readPages: Set<Int>,
    visitedPages: Set<Int>,
    bookmarkPage: Int?,
    currentPage: Int,
    onToggleRead: (Int) -> Unit,
    onJump: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val colors = khatmaPalette()
    var mode by remember { mutableStateOf(KhatmaMapMode.Juz) }
    val expandedJuzOverrides = remember { mutableStateMapOf<Int, Boolean>() }
    val sections = remember(totalPages) { JuzLayout.sections(totalPages) }
    val validRange = 1..totalPages.coerceAtLeast(1)
    val readCount = readPages.count { it in validRange }
    val visitedCount = visitedPages.count { it in validRange }
    val visitedOnly = (visitedCount - readCount).coerceAtLeast(0)
    val remaining = (totalPages - visitedCount).coerceAtLeast(0)
    val readPercent = if (totalPages > 0) readCount * 100 / totalPages else 0
    val visitedPercent = if (totalPages > 0) visitedCount * 100 / totalPages else 0
    val nextUnread = remember(totalPages, visitedPages) {
        (1..totalPages).firstOrNull { it !in visitedPages }
    }
    val currentInfo = remember(currentPage, totalPages) { JuzLayout.positionOf(currentPage, totalPages) }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.page) {
        Column(modifier = Modifier.fillMaxSize()) {
            KhatmaTopBar(onBack = onBack)
            MapModeSwitch(
                mode = mode,
                onModeChange = { mode = it },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            )
            if (mode == KhatmaMapMode.Juz) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    item {
                        KhatmaHero(
                            readCount = readCount,
                            visitedOnly = visitedOnly,
                            remaining = remaining,
                            totalPages = totalPages,
                            readPercent = readPercent,
                            visitedPercent = visitedPercent,
                            colors = colors,
                        )
                    }
                    item {
                        CurrentPositionCard(
                            currentPage = currentPage,
                            bookmarkPage = bookmarkPage,
                            nextUnread = nextUnread,
                            currentInfo = currentInfo,
                            colors = colors,
                        )
                    }
                    item { MapLegend(colors) }
                    items(sections, key = { it.number }) { section ->
                        val isComplete = section.pages.all { it in readPages }
                        val expanded = expandedJuzOverrides[section.number] ?: !isComplete
                        JuzCard(
                            section = section,
                            readPages = readPages,
                            visitedPages = visitedPages,
                            bookmarkPage = bookmarkPage,
                            currentPage = currentPage,
                            expanded = expanded,
                            colors = colors,
                            onExpandedChange = { expandedJuzOverrides[section.number] = it },
                            onToggleRead = onToggleRead,
                            onJump = onJump,
                        )
                    }
                }
            } else {
                PagesOnlyContent(
                    totalPages = totalPages,
                    readCount = readCount,
                    visitedOnly = visitedOnly,
                    remaining = remaining,
                    readPercent = readPercent,
                    visitedPercent = visitedPercent,
                    readPages = readPages,
                    visitedPages = visitedPages,
                    bookmarkPage = bookmarkPage,
                    currentPage = currentPage,
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onToggleRead = onToggleRead,
                    onJump = onJump,
                )
            }
        }
    }
}

@Composable
private fun MapModeSwitch(
    mode: KhatmaMapMode,
    onModeChange: (KhatmaMapMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    MushafSegmentedTabs(
        labels = listOf("الأجزاء", "الصفحات فقط"),
        selectedIndex = mode.ordinal,
        onSelected = { onModeChange(KhatmaMapMode.entries[it]) },
        modifier = modifier,
    )
}

@Composable
private fun KhatmaTopBar(onBack: () -> Unit) {
    MushafTopBar(
        title = "خريطة الختمة",
        subtitle = "كل صفحة في موضعها، وكل لون له معنى",
        onBack = onBack,
    )
}

@Composable
private fun KhatmaHero(
    readCount: Int,
    visitedOnly: Int,
    remaining: Int,
    totalPages: Int,
    readPercent: Int,
    visitedPercent: Int,
    colors: KhatmaColors,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(colors.heroStart, colors.heroEnd)))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.heroBubble),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.GridView, contentDescription = null, tint = colors.heroInk, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text("تقدم القراءة", style = MaterialTheme.typography.labelSmall, color = colors.heroMuted)
                        Text(
                            "${readPercent.toArabicDigits()}٪",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.heroInk,
                        )
                    }
                }
                DualProgressTrack(readPercent = readPercent, visitedPercent = visitedPercent, colors = colors)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    HeroStat("مقروءة", "${readCount.toArabicDigits()} صفحة", Modifier.weight(1f), colors)
                    HeroStat("زرتها فقط", "${visitedOnly.toArabicDigits()} صفحة", Modifier.weight(1f), colors)
                    HeroStat("المتبقي", "${remaining.toArabicDigits()} صفحة", Modifier.weight(1f), colors)
                }
                Text(
                    "من أصل ${totalPages.toArabicDigits()} صفحة في المصحف",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.heroMuted,
                )
            }
        }
    }
}

@Composable
private fun DualProgressTrack(readPercent: Int, visitedPercent: Int, colors: KhatmaColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(colors.heroTrack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((visitedPercent / 100f).coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(100.dp))
                .background(colors.visited)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth((readPercent / 100f).coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(100.dp))
                .background(colors.read)
        )
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier, colors: KhatmaColors) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.heroBubble)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = colors.heroInk,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.heroMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CurrentPositionCard(
    currentPage: Int,
    bookmarkPage: Int?,
    nextUnread: Int?,
    currentInfo: JuzLayout.Position,
    colors: KhatmaColors,
) {
    MapCard(colors) {
        SectionHeader("موضعك الآن", "اضغط أي صفحة في الخريطة للانتقال إليها", colors)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            InfoTile(Icons.AutoMirrored.Filled.MenuBook, "الصفحة", currentPage.toArabicDigits(), Modifier.weight(1f), colors)
            InfoTile(Icons.Filled.Bookmark, "العلامة", bookmarkPage?.toArabicDigits() ?: "-", Modifier.weight(1f), colors)
            InfoTile(Icons.Filled.RadioButtonUnchecked, "التالي", nextUnread?.toArabicDigits() ?: "اكتملت", Modifier.weight(1f), colors)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "أنت في الجزء ${currentInfo.juz.toArabicDigits()}، الصفحة ${currentInfo.pageInJuz.toArabicDigits()} من ${currentInfo.pagesInJuz.toArabicDigits()}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InfoTile(icon: ImageVector, label: String, value: String, modifier: Modifier, colors: KhatmaColors) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.tile)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.ink, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MapLegend(colors: KhatmaColors) {
    MapCard(colors) {
        SectionHeader("مفتاح الخريطة", "الألوان تساعدك على قراءة التقدم بسرعة", colors)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            LegendPill(colors.read, "مقروءة", Icons.Filled.CheckCircle, Modifier.weight(1f), colors)
            LegendPill(colors.visited, "زرتها", Icons.Filled.Visibility, Modifier.weight(1f), colors)
            LegendPill(colors.bookmark, "علامة", Icons.Filled.Bookmark, Modifier.weight(1f), colors)
            LegendPill(colors.empty, "باقية", Icons.Filled.RadioButtonUnchecked, Modifier.weight(1f), colors)
        }
    }
}

@Composable
private fun PagesOnlyContent(
    totalPages: Int,
    readCount: Int,
    visitedOnly: Int,
    remaining: Int,
    readPercent: Int,
    visitedPercent: Int,
    readPages: Set<Int>,
    visitedPages: Set<Int>,
    bookmarkPage: Int?,
    currentPage: Int,
    colors: KhatmaColors,
    modifier: Modifier = Modifier,
    onToggleRead: (Int) -> Unit,
    onJump: (Int) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PagesOnlySummary(
                totalPages = totalPages,
                readCount = readCount,
                visitedOnly = visitedOnly,
                remaining = remaining,
                readPercent = readPercent,
                visitedPercent = visitedPercent,
                colors = colors,
            )
            CompactLegend(colors)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 34.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            gridItems((1..totalPages).toList()) { page ->
                CompactPageCell(
                    page = page,
                    state = pageState(page, readPages, visitedPages, bookmarkPage),
                    isCurrent = page == currentPage,
                    colors = colors,
                    onToggleRead = { onToggleRead(page) },
                    onJump = { onJump(page) },
                )
            }
        }
    }
}

@Composable
private fun PagesOnlySummary(
    totalPages: Int,
    readCount: Int,
    visitedOnly: Int,
    remaining: Int,
    readPercent: Int,
    visitedPercent: Int,
    colors: KhatmaColors,
) {
    MapCard(colors) {
        SectionHeader("الصفحات فقط", "شبكة واحدة متتابعة لكل صفحات المصحف", colors)
        Spacer(Modifier.height(12.dp))
        SectionProgress(read = readCount, visited = readCount + visitedOnly, total = totalPages, colors = colors)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MiniMetric("مقروءة", "${readCount.toArabicDigits()} صفحة", Modifier.weight(1f), colors)
            MiniMetric("زرتها", "${visitedOnly.toArabicDigits()} صفحة", Modifier.weight(1f), colors)
            MiniMetric("باقية", "${remaining.toArabicDigits()} صفحة", Modifier.weight(1f), colors)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "المقروء ${readPercent.toArabicDigits()}٪، والمفتوح ${visitedPercent.toArabicDigits()}٪",
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier, colors: KhatmaColors) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.tile)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CompactLegend(colors: KhatmaColors) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.card,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendPill(colors.read, "مقروءة", Icons.Filled.CheckCircle, Modifier.weight(1f), colors)
            LegendPill(colors.visited, "زرتها", Icons.Filled.Visibility, Modifier.weight(1f), colors)
            LegendPill(colors.bookmark, "علامة", Icons.Filled.Bookmark, Modifier.weight(1f), colors)
            LegendPill(colors.empty, "باقية", Icons.Filled.RadioButtonUnchecked, Modifier.weight(1f), colors)
        }
    }
}

@Composable
private fun LegendPill(color: Color, label: String, icon: ImageVector, modifier: Modifier, colors: KhatmaColors) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.tile)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.legendIcon, modifier = Modifier.size(8.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.ink, maxLines = 1)
    }
}

@Composable
private fun JuzCard(
    section: JuzLayout.Section,
    readPages: Set<Int>,
    visitedPages: Set<Int>,
    bookmarkPage: Int?,
    currentPage: Int,
    expanded: Boolean,
    colors: KhatmaColors,
    onExpandedChange: (Boolean) -> Unit,
    onToggleRead: (Int) -> Unit,
    onJump: (Int) -> Unit,
) {
    val readInSection = section.pages.count { it in readPages }
    val visitedInSection = section.pages.count { it in visitedPages }
    val percent = if (section.pages.isNotEmpty()) readInSection * 100 / section.pages.size else 0
    val isComplete = section.pages.isNotEmpty() && readInSection == section.pages.size
    MapCard(colors) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 2.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(
                    "الجزء ${section.number.toArabicDigits()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.ink,
                )
                Text(
                    "صفحات ${section.start.toArabicDigits()}-${section.end.toArabicDigits()} | ${section.pages.size.toArabicDigits()} صفحة",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${percent.toArabicDigits()}٪",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colors.primary,
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "طي الجزء" else "فتح الجزء",
                tint = colors.muted,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        SectionProgress(read = readInSection, visited = visitedInSection, total = section.pages.size, colors = colors)
        if (expanded) {
            Spacer(Modifier.height(7.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                section.pages.chunked(JuzPageColumns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { page ->
                            PageCell(
                                page = page,
                                state = pageState(page, readPages, visitedPages, bookmarkPage),
                                isCurrent = page == currentPage,
                                colors = colors,
                                modifier = Modifier.weight(1f),
                                onToggleRead = { onToggleRead(page) },
                                onJump = { onJump(page) },
                            )
                        }
                        repeat(JuzPageColumns - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.tile)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (isComplete) Icons.Filled.CheckCircle else Icons.Filled.GridView,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = if (isComplete) "مكتمل ومطوي - اضغط لعرض الصفحات" else "مطوي - اضغط لعرض الصفحات",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SectionProgress(read: Int, visited: Int, total: Int, colors: KhatmaColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(colors.track)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (total > 0) (visited.toFloat() / total).coerceIn(0f, 1f) else 0f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(100.dp))
                .background(colors.visited)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(100.dp))
                .background(colors.read)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageCell(
    page: Int,
    state: PageState,
    isCurrent: Boolean,
    colors: KhatmaColors,
    modifier: Modifier = Modifier,
    onToggleRead: () -> Unit,
    onJump: () -> Unit,
) {
    val background = when (state) {
        PageState.Read -> colors.read
        PageState.Visited -> colors.visited
        PageState.Bookmark -> colors.bookmark
        PageState.Empty -> colors.empty
    }
    val textColor = when (state) {
        PageState.Read -> colors.readInk
        PageState.Visited -> colors.visitedInk
        PageState.Bookmark -> colors.bookmarkInk
        PageState.Empty -> colors.emptyInk
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .then(
                if (isCurrent) Modifier.border(2.dp, colors.currentBorder, RoundedCornerShape(7.dp))
                else Modifier.border(1.dp, colors.cellBorder, RoundedCornerShape(7.dp))
            )
            .combinedClickable(onClick = onToggleRead, onLongClick = onJump),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = page.toArabicDigits(),
            color = textColor,
            fontSize = 8.sp,
            lineHeight = 8.sp,
            fontWeight = if (isCurrent || state == PageState.Bookmark) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactPageCell(
    page: Int,
    state: PageState,
    isCurrent: Boolean,
    colors: KhatmaColors,
    onToggleRead: () -> Unit,
    onJump: () -> Unit,
) {
    val background = when (state) {
        PageState.Read -> colors.read
        PageState.Visited -> colors.visited
        PageState.Bookmark -> colors.bookmark
        PageState.Empty -> colors.empty
    }
    val textColor = when (state) {
        PageState.Read -> colors.readInk
        PageState.Visited -> colors.visitedInk
        PageState.Bookmark -> colors.bookmarkInk
        PageState.Empty -> colors.emptyInk
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(5.dp))
            .background(background)
            .then(
                if (isCurrent) Modifier.border(2.dp, colors.currentBorder, RoundedCornerShape(5.dp))
                else Modifier.border(1.dp, colors.cellBorder, RoundedCornerShape(5.dp))
            )
            .combinedClickable(onClick = onToggleRead, onLongClick = onJump),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = page.toArabicDigits(),
            color = textColor,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            fontWeight = if (isCurrent || state == PageState.Bookmark) FontWeight.ExtraBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun MapCard(colors: KhatmaColors, content: @Composable ColumnScope.() -> Unit) {
    MushafPanel(
        contentPadding = PaddingValues(12.dp),
        containerColor = colors.card,
        content = content,
    )
}

@Composable
private fun SectionHeader(title: String, subtitle: String, colors: KhatmaColors) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.ink)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.muted)
    }
}

@Composable
private fun khatmaPalette(): KhatmaColors {
    val scheme = MaterialTheme.colorScheme
    return KhatmaColors(
        page = scheme.background,
        card = scheme.surface,
        tile = scheme.surfaceContainerHigh,
        track = scheme.outlineVariant,
        ink = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        primary = scheme.primary,
        heroStart = scheme.primaryContainer,
        heroEnd = scheme.primaryContainer,
        heroInk = scheme.onPrimaryContainer,
        heroMuted = scheme.onPrimaryContainer.copy(alpha = 0.72f),
        heroBubble = scheme.surfaceVariant,
        heroTrack = scheme.outlineVariant,
        read = scheme.primary,
        readInk = scheme.onPrimary,
        visited = scheme.tertiaryContainer,
        visitedInk = scheme.onTertiaryContainer,
        bookmark = scheme.secondaryContainer,
        bookmarkInk = scheme.onSecondaryContainer,
        empty = scheme.surfaceContainerHigh,
        emptyInk = scheme.onSurfaceVariant,
        currentBorder = scheme.primary,
        cellBorder = scheme.outlineVariant,
        legendIcon = scheme.onPrimary,
    )
}

private data class KhatmaColors(
    val page: Color,
    val card: Color,
    val tile: Color,
    val track: Color,
    val ink: Color,
    val muted: Color,
    val primary: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val heroInk: Color,
    val heroMuted: Color,
    val heroBubble: Color,
    val heroTrack: Color,
    val read: Color,
    val readInk: Color,
    val visited: Color,
    val visitedInk: Color,
    val bookmark: Color,
    val bookmarkInk: Color,
    val empty: Color,
    val emptyInk: Color,
    val currentBorder: Color,
    val cellBorder: Color,
    val legendIcon: Color,
)

private const val JuzPageColumns = 10

private enum class KhatmaMapMode { Juz, Pages }

private enum class PageState { Read, Visited, Bookmark, Empty }

private fun pageState(page: Int, readPages: Set<Int>, visitedPages: Set<Int>, bookmarkPage: Int?): PageState =
    when {
        page == bookmarkPage -> PageState.Bookmark
        page in readPages -> PageState.Read
        page in visitedPages -> PageState.Visited
        else -> PageState.Empty
    }

