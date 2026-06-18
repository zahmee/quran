package com.mushaf.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.stats.DayStat
import com.mushaf.reader.data.stats.FullStats
import com.mushaf.reader.data.stats.SessionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
    stats: FullStats?,
    sessions: List<SessionEntity>,
    onBack: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
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
                        text = "إحصائيات القراءة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("ملخّص") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("السجل") })
            }

            when (tab) {
                0 -> OverviewTab(stats)
                else -> HistoryTab(sessions)
            }
        }
    }
}

@Composable
private fun OverviewTab(stats: FullStats?) {
    if (stats == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("جارٍ التحميل…") }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StreakCard(stats.streakDays)
        TodayCard(stats)
        WeekChartCard(stats.last7Days)
        PeriodCard(stats)
        RecordsCard(stats)
        KhatmaCard(stats)
        TotalsCard(stats)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StreakCard(streak: Int) {
    val msg = when {
        streak >= 7 -> "ما شاء الله! استمرارية رائعة، حافظ عليها 🌟"
        streak >= 1 -> "أحسنت! لا تكسر سلسلتك اليوم 🔥"
        else -> "ابدأ سلسلتك اليوم — صفحة واحدة تكفي للبداية"
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Column {
                Text(
                    "${streak.toArabicDigits()} ${if (streak == 1) "يوم" else "أيام"} متتالية",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TodayCard(stats: FullStats) {
    val diff = stats.todayPages - stats.weekAvgPages
    val compare = when {
        stats.weekAvgPages == 0 && stats.todayPages > 0 -> "بداية موفقة لهذا الأسبوع 👏"
        diff > 0 -> "أعلى من معدّلك الأسبوعي بـ ${diff.toArabicDigits()} صفحة 👏"
        diff == 0 && stats.todayPages > 0 -> "على مستوى معدّلك الأسبوعي تماماً"
        stats.todayPages == 0 -> "لم تقرأ بعد اليوم — معدّلك ${stats.weekAvgPages.toArabicDigits()} صفحة/يوم"
        else -> "تبقّى ${(-diff).toArabicDigits()} صفحة لتبلغ معدّلك الأسبوعي"
    }
    Card {
        SectionTitle("اليوم")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("الصفحات", stats.todayPages.toArabicDigits(), Modifier.weight(1f))
            StatTile("الوقت", formatDuration(stats.todayDurationMs), Modifier.weight(1f))
            StatTile("الجلسات", stats.todaySessions.toArabicDigits(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(compare, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "أمس: ${stats.yesterdayPages.toArabicDigits()} صفحة",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeekChartCard(days: List<DayStat>) {
    val dayShort = remember { SimpleDateFormat("EEE", Locale("ar")) }
    val max = (days.maxOfOrNull { it.pages } ?: 0).coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    Card {
        SectionTitle("آخر ٧ أيام")
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEachIndexed { index, d ->
                val isToday = index == days.lastIndex
                val barHeight = (d.pages.toFloat() / max * 90f).dp + (if (d.pages > 0) 6.dp else 2.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(d.pages.toArabicDigits(), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (isToday) primary else primary.copy(alpha = 0.4f))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        dayShort.format(Date(d.dayStartMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodCard(stats: FullStats) {
    Card {
        SectionTitle("المقارنة")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("هذا الأسبوع", "${stats.weekPages.toArabicDigits()} صفحة", Modifier.weight(1f))
            StatTile("هذا الشهر", "${stats.monthPages.toArabicDigits()} صفحة", Modifier.weight(1f))
            StatTile("هذه السنة", "${stats.yearPages.toArabicDigits()} صفحة", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "أيام نشطة هذا الأسبوع: ${stats.weekActiveDays.toArabicDigits()} من ٧",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecordsCard(stats: FullStats) {
    Card {
        SectionTitle("أرقامك القياسية")
        Spacer(Modifier.height(8.dp))
        StatLine("أفضل يوم", "${stats.bestDayPages.toArabicDigits()} صفحة")
        StatLine("أطول جلسة", formatDuration(stats.longestSessionMs))
        StatLine("أيام القراءة النشطة", "${stats.activeDays.toArabicDigits()} يوم")
    }
}

@Composable
private fun KhatmaCard(stats: FullStats) {
    val remaining = (stats.totalPagesInQuran - stats.currentPage).coerceAtLeast(0)
    Card {
        SectionTitle("تقدّم الختمة")
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { stats.khatmaPercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${stats.khatmaPercent.toArabicDigits()}٪  •  صفحة ${stats.currentPage.toArabicDigits()} من ${stats.totalPagesInQuran.toArabicDigits()}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "تبقّى ${remaining.toArabicDigits()} صفحة لإتمام الختمة",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (stats.bookmarkPage != null) {
            Text(
                "آخر علامة: صفحة ${stats.bookmarkPage.toArabicDigits()} (${(stats.bookmarkPercent ?: 0).toArabicDigits()}٪)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TotalsCard(stats: FullStats) {
    Card {
        SectionTitle("الإجمالي")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("الجلسات", stats.totalSessions.toArabicDigits(), Modifier.weight(1f))
            StatTile("الصفحات", stats.totalPages.toArabicDigits(), Modifier.weight(1f))
            StatTile("الوقت", formatDuration(stats.totalDurationMs), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HistoryTab(sessions: List<SessionEntity>) {
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد جلسات مسجّلة بعد")
        }
        return
    }
    val years = remember(sessions) { buildHistory(sessions) }
    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale("ar")) }
    val dayFmt = remember { SimpleDateFormat("EEEE d", Locale("ar")) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale("ar")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        years.forEach { year ->
            Text(
                text = year.year.toArabicDigits(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            year.months.forEach { month ->
                Card {
                    Text(
                        monthFmt.format(Date(month.anchorMillis)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${month.pages.toArabicDigits()} صفحة  •  ${formatDuration(month.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    month.days.forEachIndexed { i, day ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(
                            dayFmt.format(Date(day.anchorMillis)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${day.sessions.size.toArabicDigits()} جلسة  •  ${day.pages.toArabicDigits()} صفحة  •  ${formatDuration(day.durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        day.sessions.forEach { s ->
                            StatLine(
                                "الساعة ${timeFmt.format(Date(s.startedAt))}",
                                "${formatDuration(s.endedAt - s.startedAt)} • ${s.pagesRead.toArabicDigits()} صفحة"
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// --- small UI helpers ---

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

// --- history grouping ---

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
