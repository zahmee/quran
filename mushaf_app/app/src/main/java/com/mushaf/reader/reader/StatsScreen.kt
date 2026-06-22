package com.mushaf.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.mushaf.reader.data.stats.DayStat
import com.mushaf.reader.data.stats.FullStats
import com.mushaf.reader.data.stats.SessionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    stats: FullStats?,
    sessions: List<SessionEntity>,
    onBack: () -> Unit,
    onOpenKhatmaMap: () -> Unit,
    onDeleteSessions: (List<SessionEntity>) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<PendingStatDelete?>(null) }
    val colors = statsPalette()

    Surface(modifier = Modifier.fillMaxSize(), color = colors.page) {
        Column(modifier = Modifier.fillMaxSize()) {
            StatsTopBar(tab = tab, onTabChange = { tab = it }, onBack = onBack, colors = colors)
            when (tab) {
                0 -> OverviewTab(stats, onOpenKhatmaMap, colors)
                else -> HistoryTab(sessions, colors) { sess, msg ->
                    pendingDelete = PendingStatDelete(msg, sess)
                }
            }
        }
    }

    pendingDelete?.let { pd ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف") },
            text = { Text(pd.message) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSessions(pd.sessions)
                    pendingDelete = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun StatsTopBar(
    tab: Int,
    onTabChange: (Int) -> Unit,
    onBack: () -> Unit,
    colors: StatsColors,
) {
    Surface(color = colors.header, shadowElevation = 4.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = colors.ink)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(
                        "إحصائيات القراءة",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink,
                    )
                    Text(
                        "لوحة هادئة لمتابعة الورد والختمة",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                }
            }
            SegmentedTabs(tab = tab, onTabChange = onTabChange, colors = colors)
        }
    }
}

@Composable
private fun SegmentedTabs(tab: Int, onTabChange: (Int) -> Unit, colors: StatsColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabPill("ملخص", selected = tab == 0, colors = colors, modifier = Modifier.weight(1f)) { onTabChange(0) }
        TabPill("السجل", selected = tab == 1, colors = colors, modifier = Modifier.weight(1f)) { onTabChange(1) }
    }
}

@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    colors: StatsColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.card else Color.Transparent,
        contentColor = if (selected) colors.primary else colors.muted,
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun OverviewTab(stats: FullStats?, onOpenKhatmaMap: () -> Unit, colors: StatsColors) {
    if (stats == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("جار التحميل...", color = colors.muted)
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ReadingHero(stats, onOpenKhatmaMap, colors)
        TodayStrip(stats, colors)
        WeekChartCard(stats.last7Days, colors)
        WirdPlanner(stats, colors)
        RhythmCard(stats, colors)
        RecordsAndTotals(stats, colors)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReadingHero(stats: FullStats, onOpenKhatmaMap: () -> Unit, colors: StatsColors) {
    val remaining = (stats.totalPagesInQuran - stats.currentPage).coerceAtLeast(0)
    val progress = (stats.khatmaPercent / 100f).coerceIn(0f, 1f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        shadowElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(colors.heroStart, colors.heroEnd)))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.Start) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroBadge(icon = Icons.AutoMirrored.Filled.MenuBook, colors = colors)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text("مسار الختمة", style = MaterialTheme.typography.labelLarge, color = colors.heroMuted)
                        Text(
                            "${stats.khatmaPercent.toArabicDigits()}٪",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.heroInk,
                        )
                    }
                }
                ProgressTrack(progress = progress, colors = colors)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    HeroFact("الصفحة الحالية", stats.currentPage.toArabicDigits(), Modifier.weight(1f), colors)
                    HeroFact("المتبقي", "${remaining.toArabicDigits()} صفحة", Modifier.weight(1f), colors)
                }
                stats.bookmarkPage?.let {
                    Text(
                        "آخر علامة: صفحة ${it.toArabicDigits()} (${(stats.bookmarkPercent ?: 0).toArabicDigits()}٪)",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.heroMuted,
                    )
                }
                Surface(
                    onClick = onOpenKhatmaMap,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.heroButton,
                    contentColor = colors.heroInk,
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("افتح خريطة الختمة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBadge(icon: ImageVector, colors: StatsColors) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(colors.heroButton),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.heroInk, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun HeroFact(label: String, value: String, modifier: Modifier, colors: StatsColors) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.heroFact)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.heroFactInk)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.heroFactMuted, maxLines = 1)
    }
}

@Composable
private fun ProgressTrack(progress: Float, colors: StatsColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(colors.heroButton)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .clip(RoundedCornerShape(100.dp))
                .background(colors.heroAccent)
        )
    }
}

@Composable
private fun TodayStrip(stats: FullStats, colors: StatsColors) {
    val diff = stats.todayPages - stats.weekAvgPages
    val compare = when {
        stats.weekAvgPages == 0 && stats.todayPages > 0 -> "بداية طيبة لهذا الأسبوع"
        diff > 0 -> "أعلى من معدلك الأسبوعي بـ ${diff.toArabicDigits()} صفحة"
        diff == 0 && stats.todayPages > 0 -> "على مستوى معدلك الأسبوعي"
        stats.todayPages == 0 -> "لم تسجل قراءة اليوم بعد"
        else -> "تبقى ${(-diff).toArabicDigits()} صفحة لبلوغ المعدل"
    }
    StatsCard(colors = colors) {
        Text("اليوم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.ink)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(Icons.AutoMirrored.Filled.MenuBook, "صفحات", stats.todayPages.toArabicDigits(), Modifier.weight(1f), colors)
            MetricTile(Icons.Filled.AccessTime, "وقت", formatDuration(stats.todayDurationMs), Modifier.weight(1f), colors)
            MetricTile(Icons.AutoMirrored.Filled.TrendingUp, "جلسات", stats.todaySessions.toArabicDigits(), Modifier.weight(1f), colors)
        }
        Spacer(Modifier.height(10.dp))
        Text(compare, style = MaterialTheme.typography.bodyMedium, color = colors.primary, fontWeight = FontWeight.SemiBold)
        Text("أمس: ${stats.yesterdayPages.toArabicDigits()} صفحة", style = MaterialTheme.typography.labelSmall, color = colors.muted)
    }
}

@Composable
private fun MetricTile(icon: ImageVector, label: String, value: String, modifier: Modifier, colors: StatsColors) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.tile)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
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
private fun WeekChartCard(days: List<DayStat>, colors: StatsColors) {
    val dayShort = remember { SimpleDateFormat("EEE", Locale("ar")) }
    val max = (days.maxOfOrNull { it.pages } ?: 0).coerceAtLeast(1)
    StatsCard(colors = colors) {
        SectionHeader("آخر ٧ أيام", "الصفحات المسجلة يومياً", colors)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(154.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEachIndexed { index, day ->
                val isToday = index == days.lastIndex
                val barHeight = (day.pages.toFloat() / max * 96f).dp + if (day.pages > 0) 10.dp else 4.dp
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(day.pages.toArabicDigits(), style = MaterialTheme.typography.labelSmall, color = colors.ink)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(100.dp))
                                .background(colors.track)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (isToday) colors.primary else colors.secondary)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(dayShort.format(Date(day.dayStartMillis)), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                }
            }
        }
    }
}

@Composable
private fun WirdPlanner(stats: FullStats, colors: StatsColors) {
    var minutes by remember { mutableStateOf(10) }
    val pages = (stats.pagesPerMinute * minutes).roundToInt().coerceAtLeast(1)
    val from = stats.currentPage.coerceIn(1, stats.totalPagesInQuran)
    val to = (from + pages - 1).coerceAtMost(stats.totalPagesInQuran)
    StatsCard(colors = colors) {
        SectionHeader("ورد مناسب لوقتك", "اختر مدة قصيرة وسيحسب التطبيق الصفحات", colors)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(5, 10, 15, 30).forEach { m ->
                ChoiceChip(
                    label = "${m.toArabicDigits()} د",
                    selected = minutes == m,
                    colors = colors,
                    modifier = Modifier.weight(1f),
                ) { minutes = m }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBubble(Icons.Filled.Speed, colors)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text("اقرأ ${pages.toArabicDigits()} صفحة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = colors.primary)
                Text("من صفحة ${from.toArabicDigits()} إلى صفحة ${to.toArabicDigits()}", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                Text(
                    if (stats.paceFromData) "حسب سرعتك الحالية: ${formatPace(stats.pagesPerMinute)}" else "تقدير مبدئي يتحسن مع القراءة",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, colors: StatsColors, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primary else colors.tile,
        contentColor = if (selected) colors.onPrimary else colors.ink,
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RhythmCard(stats: FullStats, colors: StatsColors) {
    StatsCard(colors = colors) {
        SectionHeader("إيقاع القراءة", "مقارنة سريعة للفترات الحالية", colors)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(Icons.Filled.CalendarMonth, "الأسبوع", "${stats.weekPages.toArabicDigits()} صفحة", Modifier.weight(1f), colors)
            MetricTile(Icons.AutoMirrored.Filled.MenuBook, "الشهر", "${stats.monthPages.toArabicDigits()} صفحة", Modifier.weight(1f), colors)
            MetricTile(Icons.AutoMirrored.Filled.TrendingUp, "السنة", "${stats.yearPages.toArabicDigits()} صفحة", Modifier.weight(1f), colors)
        }
        Spacer(Modifier.height(10.dp))
        StreakLine(stats.streakDays, stats.weekActiveDays, colors)
    }
}

@Composable
private fun StreakLine(streak: Int, activeDays: Int, colors: StatsColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.tile)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = colors.warm, modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text("${streak.toArabicDigits()} ${if (streak == 1) "يوم" else "أيام"} متتالية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.ink)
            Text("أيام نشطة هذا الأسبوع: ${activeDays.toArabicDigits()} من ٧", style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
    }
}

@Composable
private fun RecordsAndTotals(stats: FullStats, colors: StatsColors) {
    StatsCard(colors = colors) {
        SectionHeader("الأرقام المهمة", "ملخص طويل المدى", colors)
        Spacer(Modifier.height(12.dp))
        CompactLine("أفضل يوم", "${stats.bestDayPages.toArabicDigits()} صفحة", colors)
        CompactLine("أطول جلسة", formatDuration(stats.longestSessionMs), colors)
        CompactLine("أيام القراءة النشطة", "${stats.activeDays.toArabicDigits()} يوم", colors)
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = colors.divider)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MiniTotal("الجلسات", stats.totalSessions.toArabicDigits(), Modifier.weight(1f), colors)
            MiniTotal("الصفحات", stats.totalPages.toArabicDigits(), Modifier.weight(1f), colors)
            MiniTotal("الوقت", formatDuration(stats.totalDurationMs), Modifier.weight(1f), colors)
        }
    }
}

@Composable
private fun MiniTotal(label: String, value: String, modifier: Modifier, colors: StatsColors) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.ink, textAlign = TextAlign.Center, maxLines = 2)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HistoryTab(
    sessions: List<SessionEntity>,
    colors: StatsColors,
    onRequestDelete: (List<SessionEntity>, String) -> Unit,
) {
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            StatsCard(colors = colors) {
                IconBubble(Icons.AutoMirrored.Filled.MenuBook, colors)
                Spacer(Modifier.height(12.dp))
                Text("لا توجد جلسات مسجلة بعد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.ink)
                Text("ابدأ القراءة ثم عد إلى هنا لترى سجل الجلسات.", style = MaterialTheme.typography.bodyMedium, color = colors.muted, textAlign = TextAlign.Center)
            }
        }
        return
    }
    val years = remember(sessions) { buildHistory(sessions) }
    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale("ar")) }
    val dayFmt = remember { SimpleDateFormat("EEEE d", Locale("ar")) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale("ar")) }
    val todayStart = remember {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.timeInMillis
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        years.forEach { year ->
            Text(
                text = year.year.toArabicDigits(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = colors.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
            year.months.forEach { month ->
                StatsCard(colors = colors) {
                    SectionHeader(monthFmt.format(Date(month.anchorMillis)), "${month.pages.toArabicDigits()} صفحة • ${formatDuration(month.durationMs)}", colors)
                    Spacer(Modifier.height(10.dp))
                    month.days.forEachIndexed { i, day ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.divider)
                        DaySection(
                            day = day,
                            isToday = day.anchorMillis >= todayStart,
                            dayFmt = dayFmt,
                            timeFmt = timeFmt,
                            colors = colors,
                            onRequestDelete = onRequestDelete
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DaySection(
    day: DayG,
    isToday: Boolean,
    dayFmt: SimpleDateFormat,
    timeFmt: SimpleDateFormat,
    colors: StatsColors,
    onRequestDelete: (List<SessionEntity>, String) -> Unit,
) {
    var expanded by remember(day.anchorMillis) { mutableStateOf(isToday) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(
            onClick = { onRequestDelete(day.sessions.toList(), "حذف كل جلسات هذا اليوم؟") },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "حذف جلسات اليوم", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = !expanded }
                .background(colors.tile)
                .padding(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(dayFmt.format(Date(day.anchorMillis)), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = colors.ink)
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "طي الجلسات" else "توسيع الجلسات",
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                "${day.sessions.size.toArabicDigits()} جلسة • ${day.pages.toArabicDigits()} صفحة • ${formatDuration(day.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
            )
        }
    }
    if (expanded) {
        Spacer(Modifier.height(8.dp))
        day.sessions.forEach { s ->
            SessionRow(
                timeLabel = "الساعة ${timeFmt.format(Date(s.startedAt))}",
                valueLabel = "${formatDuration(s.endedAt - s.startedAt)} • ${s.pagesRead.toArabicDigits()} صفحة",
                colors = colors,
                onDelete = { onRequestDelete(listOf(s), "حذف هذه الجلسة؟") }
            )
        }
    }
}

@Composable
private fun SessionRow(timeLabel: String, valueLabel: String, colors: StatsColors, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "حذف الجلسة", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.subtle)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.ink)
            Text(timeLabel, style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
    }
}

@Composable
private fun StatsCard(colors: StatsColors, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.card,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start, content = content)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, colors: StatsColors) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.ink)
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.muted)
    }
}

@Composable
private fun CompactLine(label: String, value: String, colors: StatsColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = colors.ink, textAlign = TextAlign.Start)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.muted, textAlign = TextAlign.Start)
    }
}

@Composable
private fun IconBubble(icon: ImageVector, colors: StatsColors) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(colors.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun statsPalette(): StatsColors {
    val dark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.35f
    return if (dark) {
        StatsColors(
            page = Color(0xFF101211),
            header = Color(0xFF151917),
            card = Color(0xFF1A201D),
            tile = Color(0xFF242C27),
            subtle = Color(0xFF202622),
            track = Color(0xFF2B342F),
            divider = Color(0xFF303932),
            ink = Color(0xFFEFF4EE),
            muted = Color(0xFFAEB9B1),
            primary = Color(0xFF70D59D),
            secondary = Color(0xFF568D74),
            warm = Color(0xFFE0AA59),
            onPrimary = Color(0xFF0B1A12),
            heroStart = Color(0xFF173928),
            heroEnd = Color(0xFF12211B),
            heroInk = Color(0xFFF2F8F1),
            heroMuted = Color(0xFFC6D8CB),
            heroAccent = Color(0xFFE2C56D),
            heroButton = Color(0x3325372F),
            heroFact = Color(0xFF253C31),
            heroFactInk = Color(0xFFF2F8F1),
            heroFactMuted = Color(0xFFC6D8CB),
        )
    } else {
        StatsColors(
            page = Color(0xFFF6F0E5),
            header = Color(0xFFFBF7EF),
            card = Color(0xFFFFFCF6),
            tile = Color(0xFFF0E7D8),
            subtle = Color(0xFFF5EDE0),
            track = Color(0xFFE6DBC9),
            divider = Color(0xFFE7DCCB),
            ink = Color(0xFF1E211C),
            muted = Color(0xFF777064),
            primary = Color(0xFF1F7A5A),
            secondary = Color(0xFF97B7A2),
            warm = Color(0xFFC4832E),
            onPrimary = Color.White,
            heroStart = Color(0xFF174D37),
            heroEnd = Color(0xFF1F7A5A),
            heroInk = Color.White,
            heroMuted = Color(0xFFDDECE2),
            heroAccent = Color(0xFFE5C86C),
            heroButton = Color(0x26FFFFFF),
            heroFact = Color(0xFFE9F2EA),
            heroFactInk = Color(0xFF104D37),
            heroFactMuted = Color(0xFF4D765F),
        )
    }
}

private data class StatsColors(
    val page: Color,
    val header: Color,
    val card: Color,
    val tile: Color,
    val subtle: Color,
    val track: Color,
    val divider: Color,
    val ink: Color,
    val muted: Color,
    val primary: Color,
    val secondary: Color,
    val warm: Color,
    val onPrimary: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val heroInk: Color,
    val heroMuted: Color,
    val heroAccent: Color,
    val heroButton: Color,
    val heroFact: Color,
    val heroFactInk: Color,
    val heroFactMuted: Color,
)

private fun Color.luminanceApprox(): Float =
    (red * 0.299f + green * 0.587f + blue * 0.114f)

private fun formatPace(pagesPerMinute: Double): String {
    if (pagesPerMinute <= 0.0) return "-"
    if (pagesPerMinute >= 1.0) return "${pagesPerMinute.roundToInt().toArabicDigits()} صفحة في الدقيقة"
    val minutesPerPage = (1.0 / pagesPerMinute).roundToInt().coerceAtLeast(1)
    return "صفحة كل ${minutesPerPage.toArabicDigits()} دقيقة"
}

private data class PendingStatDelete(val message: String, val sessions: List<SessionEntity>)

private class DayG(val anchorMillis: Long) {
    val sessions = mutableListOf<SessionEntity>()
    val pages: Int get() = sessions.sumOf { it.pagesRead }
    val durationMs: Long get() = sessions.sumOf { (it.endedAt - it.startedAt).coerceAtLeast(0) }
}

private class MonthG(val anchorMillis: Long) {
    val days = mutableListOf<DayG>()
    val pages: Int get() = days.sumOf { it.pages }
    val durationMs: Long get() = days.sumOf { it.durationMs }
}

private class YearG(val year: Int, val anchorMillis: Long) {
    val months = mutableListOf<MonthG>()
}

private fun buildHistory(sessions: List<SessionEntity>): List<YearG> {
    val cal = Calendar.getInstance()
    val years = mutableListOf<YearG>()
    var yKey = Int.MIN_VALUE
    var mKey = ""
    var dKey = ""
    for (s in sessions) {
        cal.timeInMillis = s.startedAt
        val y = cal.get(Calendar.YEAR)
        val mk = "$y-${cal.get(Calendar.MONTH)}"
        val dk = "$mk-${cal.get(Calendar.DAY_OF_MONTH)}"
        if (y != yKey) { years.add(YearG(y, s.startedAt)); yKey = y; mKey = ""; dKey = "" }
        if (mk != mKey) { years.last().months.add(MonthG(s.startedAt)); mKey = mk; dKey = "" }
        if (dk != dKey) { years.last().months.last().days.add(DayG(s.startedAt)); dKey = dk }
        years.last().months.last().days.last().sessions.add(s)
    }
    return years
}

internal fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60000L).toInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        totalMin <= 0 -> "أقل من دقيقة"
        h == 0 -> "${m.toArabicDigits()} دقيقة"
        m == 0 -> "${h.toArabicDigits()} ساعة"
        else -> "${h.toArabicDigits()} ساعة و ${m.toArabicDigits()} دقيقة"
    }
}
