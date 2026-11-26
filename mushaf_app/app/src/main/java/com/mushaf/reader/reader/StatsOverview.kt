package com.mushaf.reader.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.mushaf.reader.data.stats.FullStats
import com.mushaf.reader.data.stats.KhatmaEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

@Composable
internal fun OverviewTab(
    stats: FullStats?,
    khatmas: List<KhatmaEntity>,
    khatmaStartedAt: Long,
    onOpenKhatmaMap: () -> Unit,
    onCompleteKhatma: () -> Unit,
    onResetKhatma: () -> Unit,
    colors: StatsColors,
) {
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
        KhatmaJournalCard(khatmas, khatmaStartedAt, onCompleteKhatma, onResetKhatma, colors)
        TodayStrip(stats, colors)
        PeriodChartCard(stats, colors)
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
private fun KhatmaJournalCard(
    khatmas: List<KhatmaEntity>,
    khatmaStartedAt: Long,
    onCompleteKhatma: () -> Unit,
    onResetKhatma: () -> Unit,
    colors: StatsColors,
) {
    val elapsedDays = ((System.currentTimeMillis() - khatmaStartedAt).coerceAtLeast(0L) / 86_400_000L).toInt()
    StatsCard(colors) {
        SectionHeader("سجل الختمات", "احفظ ختمتك، أو ابدأ من جديد", colors)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.tile)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBubble(Icons.AutoMirrored.Filled.MenuBook, colors)
            Column(Modifier.weight(1f)) {
                Text("الختمة الحالية", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                Text(
                    "بدأت ${HijriDate.hijri(khatmaStartedAt)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                )
                Text(
                    "منذ ${khatmaElapsedLabel(elapsedDays)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            JournalButton("أتممت الختمة", Icons.Filled.CheckCircle, filled = true, colors, Modifier.weight(1f), onCompleteKhatma)
            JournalButton("بدء ختمة جديدة", Icons.Filled.Refresh, filled = false, colors, Modifier.weight(1f), onResetKhatma)
        }
        if (khatmas.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.divider)
            Spacer(Modifier.height(10.dp))
            Text(
                "الختمات المحفوظة (${khatmas.size.toArabicDigits()})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
            )
            Spacer(Modifier.height(8.dp))
            khatmas.forEachIndexed { i, k ->
                KhatmaLogRow(ordinal = khatmas.size - i, khatma = k, colors = colors)
                if (i != khatmas.lastIndex) Spacer(Modifier.height(8.dp))
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Text(
                "لم تُسجَّل ختمات بعد — أول ختمة تنتظرك.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun JournalButton(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    colors: StatsColors,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (filled) colors.primary else Color.Transparent,
        contentColor = if (filled) colors.onPrimary else colors.primary,
        border = if (filled) null else BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 11.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun KhatmaLogRow(ordinal: Int, khatma: KhatmaEntity, colors: StatsColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.subtle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(colors.tile),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                ordinal.toArabicDigits(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                HijriDate.hijri(khatma.completedAt),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
            )
            Text(
                HijriDate.gregorian(khatma.completedAt),
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
            )
        }
        Text(
            khatmaElapsedLabel(khatma.durationDays),
            style = MaterialTheme.typography.labelSmall,
            color = colors.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.tile)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Arabic-friendly day count: "يوم واحد", "يومان", "٥ أيام", "٤٢ يوماً". */
private fun khatmaElapsedLabel(days: Int): String = when {
    days <= 0 -> "أقل من يوم"
    days == 1 -> "يوم واحد"
    days == 2 -> "يومان"
    days <= 10 -> "${days.toArabicDigits()} أيام"
    else -> "${days.toArabicDigits()} يوماً"
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
private fun PeriodChartCard(stats: FullStats, colors: StatsColors) {
    var period by remember { mutableStateOf(0) } // 0 = week, 1 = month, 2 = year
    val dayShort = remember { SimpleDateFormat("EEE", ArabicLocale) }
    val monthShort = remember {
        arrayOf("ينا", "فبر", "مار", "أبر", "ماي", "يون", "يول", "أغس", "سبت", "أكت", "نوف", "ديس")
    }

    val bars: List<BarItem>
    val total: Int
    val avgLabel: String
    val avgValue: String
    when (period) {
        0 -> {
            bars = stats.last7Days.mapIndexed { i, d ->
                BarItem(dayShort.format(Date(d.dayStartMillis)), d.pages, i == stats.last7Days.lastIndex, true)
            }
            total = stats.last7Days.sumOf { it.pages }
            avgLabel = "متوسط يومي"
            avgValue = (total / 7).toArabicDigits()
        }
        1 -> {
            val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            val lastDay = stats.monthDays.size
            bars = stats.monthDays.mapIndexed { i, d ->
                val dayNum = i + 1
                val show = dayNum == 1 || dayNum % 5 == 0 || dayNum == lastDay
                BarItem(dayNum.toArabicDigits(), d.pages, dayNum == today, show)
            }
            total = stats.monthDays.sumOf { it.pages }
            val activeDays = stats.monthDays.count { it.pages > 0 }.coerceAtLeast(1)
            avgLabel = "متوسط يوم القراءة"
            avgValue = (total / activeDays).toArabicDigits()
        }
        else -> {
            val curMonth = Calendar.getInstance().get(Calendar.MONTH)
            bars = stats.yearMonthPages.mapIndexed { i, p -> BarItem(monthShort[i], p, i == curMonth, true) }
            total = stats.yearMonthPages.sum()
            val activeMonths = stats.yearMonthPages.count { it > 0 }.coerceAtLeast(1)
            avgLabel = "متوسط الشهر"
            avgValue = (total / activeMonths).toArabicDigits()
        }
    }
    val best = bars.maxOfOrNull { it.value } ?: 0

    StatsCard(colors = colors) {
        SectionHeader("مقدار القراءة", "الصفحات المقروءة حسب المدة", colors)
        Spacer(Modifier.height(12.dp))
        PeriodSwitch(period, { period = it }, colors)
        Spacer(Modifier.height(14.dp))
        PeriodBars(bars, colors)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MiniTotal("المجموع", total.toArabicDigits(), Modifier.weight(1f), colors)
            MiniTotal(avgLabel, avgValue, Modifier.weight(1f), colors)
            MiniTotal("الأفضل", best.toArabicDigits(), Modifier.weight(1f), colors)
        }
    }
}

private data class BarItem(val label: String, val value: Int, val highlighted: Boolean, val showLabel: Boolean)

@Composable
private fun PeriodSwitch(period: Int, onChange: (Int) -> Unit, colors: StatsColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.track)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf("أسبوع", "شهر", "سنة").forEachIndexed { i, label ->
            val selected = period == i
            Surface(
                onClick = { onChange(i) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(11.dp),
                color = if (selected) colors.card else Color.Transparent,
                contentColor = if (selected) colors.primary else colors.muted,
                shadowElevation = if (selected) 1.dp else 0.dp,
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PeriodBars(bars: List<BarItem>, colors: StatsColors) {
    val max = (bars.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    val many = bars.size > 12
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(if (many) 2.dp else 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { b ->
            val barHeight = (b.value.toFloat() / max * 92f).dp + if (b.value > 0) 8.dp else 3.dp
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (!many) {
                    Text(b.value.toArabicDigits(), style = MaterialTheme.typography.labelSmall, color = colors.ink, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(108.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(100.dp)).background(colors.track))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (b.highlighted) colors.primary else colors.secondary)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(if (b.showLabel) b.label else "", style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 1)
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
internal fun MiniTotal(label: String, value: String, modifier: Modifier, colors: StatsColors) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.ink, textAlign = TextAlign.Center, maxLines = 2)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.muted, textAlign = TextAlign.Center)
    }
}
