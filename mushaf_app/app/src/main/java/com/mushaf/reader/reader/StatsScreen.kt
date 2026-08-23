package com.mushaf.reader.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.sp
import com.mushaf.reader.data.stats.DayStat
import com.mushaf.reader.data.stats.FullStats
import com.mushaf.reader.data.stats.KhatmaEntity
import com.mushaf.reader.data.stats.SessionEntity
import com.mushaf.reader.ui.components.MushafPanel
import com.mushaf.reader.ui.components.MushafSegmentedTabs
import com.mushaf.reader.ui.components.MushafTopBar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val ArabicLocale: Locale = Locale.forLanguageTag("ar")

@Composable
fun StatsScreen(
    stats: FullStats?,
    sessions: List<SessionEntity>,
    khatmas: List<KhatmaEntity>,
    khatmaStartedAt: Long,
    onCompleteKhatma: () -> Unit,
    onResetKhatma: () -> Unit,
    onBack: () -> Unit,
    onOpenKhatmaMap: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var pendingComplete by remember { mutableStateOf(false) }
    var pendingReset by remember { mutableStateOf(false) }
    val colors = statsPalette()

    Surface(modifier = Modifier.fillMaxSize(), color = colors.page) {
        Column(modifier = Modifier.fillMaxSize()) {
            StatsTopBar(tab = tab, onTabChange = { tab = it }, onBack = onBack)
            when (tab) {
                0 -> OverviewTab(
                    stats = stats,
                    khatmas = khatmas,
                    khatmaStartedAt = khatmaStartedAt,
                    onOpenKhatmaMap = onOpenKhatmaMap,
                    onCompleteKhatma = { pendingComplete = true },
                    onResetKhatma = { pendingReset = true },
                    colors = colors,
                )
                1 -> CalendarTab(sessions, colors)
                else -> HistoryTab(sessions, colors)
            }
        }
    }

    if (pendingComplete) {
        AlertDialog(
            onDismissRequest = { pendingComplete = false },
            title = { Text("تسجيل ختمة") },
            text = {
                val now = System.currentTimeMillis()
                Text(
                    "سيُسجَّل إتمام الختمة بتاريخ اليوم:\n" +
                        "${HijriDate.hijri(now)}\n${HijriDate.gregorian(now)}\n\n" +
                        "ثم تبدأ ختمة جديدة تلقائياً. سجل الجلسات لا يتأثر."
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingComplete = false; onCompleteKhatma() }) {
                    Text("حفظ الختمة", color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingComplete = false }) { Text("إلغاء") }
            }
        )
    }

    if (pendingReset) {
        AlertDialog(
            onDismissRequest = { pendingReset = false },
            title = { Text("بدء ختمة جديدة") },
            text = {
                Text(
                    "سيُمسح تقدّمك في الصفحات والأجزاء للختمة الحالية لتبدأ من جديد. " +
                        "سجل الختمات المحفوظة وسجل الجلسات لن يتأثرا."
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingReset = false; onResetKhatma() }) {
                    Text("بدء جديدة", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReset = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun StatsTopBar(
    tab: Int,
    onTabChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Column {
        MushafTopBar(
            title = "إحصائيات القراءة",
            subtitle = "متابعة الورد والختمة والجلسات",
            onBack = onBack,
        )
        MushafSegmentedTabs(
            labels = listOf("ملخص", "التقويم", "السجل"),
            selectedIndex = tab,
            onSelected = onTabChange,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun OverviewTab(
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
private fun MiniTotal(label: String, value: String, modifier: Modifier, colors: StatsColors) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.ink, textAlign = TextAlign.Center, maxLines = 2)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CalendarTab(sessions: List<SessionEntity>, colors: StatsColors) {
    val byDay = remember(sessions) {
        val m = HashMap<Long, DayG>()
        val c = Calendar.getInstance()
        for (s in sessions) {
            c.timeInMillis = s.startedAt
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            m.getOrPut(c.timeInMillis) { DayG(c.timeInMillis) }.sessions.add(s)
        }
        m
    }
    var monthOffset by remember { mutableStateOf(0) }
    var selectedDay by remember { mutableStateOf<Long?>(null) }
    val dayFmt = remember { SimpleDateFormat("EEEE d", ArabicLocale) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", ArabicLocale) }
    val todayAnchor = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val base = remember(monthOffset) {
        Calendar.getInstance().apply {
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
    }
    val firstMillis = base.timeInMillis
    val daysInMonth = base.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstCol = base.get(Calendar.DAY_OF_WEEK) % 7 // Saturday = 0
    val dayAnchors = remember(monthOffset) {
        val c = base.clone() as Calendar
        (1..daysInMonth).map { d -> c.set(Calendar.DAY_OF_MONTH, d); c.timeInMillis }
    }
    val monthMax = (dayAnchors.maxOfOrNull { byDay[it]?.pages ?: 0 } ?: 0).coerceAtLeast(1)
    val monthTotal = dayAnchors.sumOf { byDay[it]?.pages ?: 0 }
    val activeDays = dayAnchors.count { (byDay[it]?.pages ?: 0) > 0 }
    val bestDay = dayAnchors.maxOfOrNull { byDay[it]?.pages ?: 0 } ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatsCard(colors) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { monthOffset -= 1; selectedDay = null }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "الشهر السابق", tint = colors.ink)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(HijriDate.gregorianMonthYear(firstMillis), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.ink)
                    Text(HijriDate.hijriMonthYear(firstMillis + 14L * 86_400_000L), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                }
                val canNext = monthOffset < 0
                IconButton(onClick = { if (canNext) { monthOffset += 1; selectedDay = null } }, enabled = canNext) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "الشهر التالي", tint = if (canNext) colors.ink else colors.muted)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("س", "ح", "ن", "ث", "ر", "خ", "ج").forEach {
                    Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = colors.muted)
                }
            }
            Spacer(Modifier.height(4.dp))
            val totalCells = firstCol + daysInMonth
            val rows = (totalCells + 6) / 7
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayNum = r * 7 + col - firstCol + 1
                        if (dayNum in 1..daysInMonth) {
                            val anchor = dayAnchors[dayNum - 1]
                            CalendarCell(
                                modifier = Modifier.weight(1f),
                                dayNum = dayNum,
                                pages = byDay[anchor]?.pages ?: 0,
                                max = monthMax,
                                isToday = anchor == todayAnchor,
                                selected = selectedDay == anchor,
                                colors = colors,
                                onClick = { selectedDay = if (selectedDay == anchor) null else anchor },
                            )
                        } else {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            CalendarLegend(colors)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniTotal("صفحات الشهر", monthTotal.toArabicDigits(), Modifier.weight(1f), colors)
                MiniTotal("أيام القراءة", activeDays.toArabicDigits(), Modifier.weight(1f), colors)
                MiniTotal("أفضل يوم", bestDay.toArabicDigits(), Modifier.weight(1f), colors)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "النقر على يوم يعرض جلساته",
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        selectedDay?.let { sel ->
            val day = byDay[sel]
            StatsCard(colors) {
                Text(dayFmt.format(Date(sel)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.ink)
                Spacer(Modifier.height(8.dp))
                if (day == null || day.sessions.isEmpty()) {
                    Text("لا جلسات في هذا اليوم.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                } else {
                    Text(
                        "${day.sessions.size.toArabicDigits()} جلسة • ${day.pages.toArabicDigits()} صفحة • ${formatDuration(day.durationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                    Spacer(Modifier.height(8.dp))
                    day.sessions.sortedBy { it.startedAt }.forEach { s ->
                        SessionRow(
                            timeLabel = "الساعة ${timeFmt.format(Date(s.startedAt))}",
                            valueLabel = "${formatDuration(s.endedAt - s.startedAt)} • ${s.pagesRead.toArabicDigits()} صفحة",
                            colors = colors,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CalendarCell(
    modifier: Modifier,
    dayNum: Int,
    pages: Int,
    max: Int,
    isToday: Boolean,
    selected: Boolean,
    colors: StatsColors,
    onClick: () -> Unit,
) {
    val ratio = if (max > 0) pages.toFloat() / max else 0f
    val bg = when {
        pages <= 0 -> colors.track.copy(alpha = 0.5f)
        ratio <= 0.33f -> colors.primary.copy(alpha = 0.28f)
        ratio <= 0.66f -> colors.primary.copy(alpha = 0.55f)
        ratio <= 0.85f -> colors.primary.copy(alpha = 0.8f)
        else -> colors.primary
    }
    val fg = when {
        pages <= 0 -> colors.muted
        ratio > 0.55f -> colors.onPrimary
        else -> colors.ink
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(
                when {
                    isToday -> Modifier.border(1.5.dp, colors.warm, RoundedCornerShape(8.dp))
                    selected -> Modifier.border(1.5.dp, colors.primary, RoundedCornerShape(8.dp))
                    else -> Modifier
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(dayNum.toArabicDigits(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = fg)
            if (pages > 0) Text(pages.toArabicDigits(), fontSize = 9.sp, lineHeight = 10.sp, color = fg)
        }
    }
}

@Composable
private fun CalendarLegend(colors: StatsColors) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.weight(1f))
        Text("أقل", style = MaterialTheme.typography.labelSmall, color = colors.muted)
        listOf(
            colors.track.copy(alpha = 0.5f),
            colors.primary.copy(alpha = 0.28f),
            colors.primary.copy(alpha = 0.55f),
            colors.primary.copy(alpha = 0.8f),
            colors.primary,
        ).forEach {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(it))
        }
        Text("أكثر", style = MaterialTheme.typography.labelSmall, color = colors.muted)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun HistoryTab(
    sessions: List<SessionEntity>,
    colors: StatsColors,
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
    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", ArabicLocale) }
    val dayFmt = remember { SimpleDateFormat("EEEE d", ArabicLocale) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", ArabicLocale) }
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
) {
    var expanded by remember(day.anchorMillis) { mutableStateOf(isToday) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
    if (expanded) {
        Spacer(Modifier.height(8.dp))
        day.sessions.forEach { s ->
            SessionRow(
                timeLabel = "الساعة ${timeFmt.format(Date(s.startedAt))}",
                valueLabel = "${formatDuration(s.endedAt - s.startedAt)} • ${s.pagesRead.toArabicDigits()} صفحة",
                colors = colors,
            )
        }
    }
}

@Composable
private fun SessionRow(timeLabel: String, valueLabel: String, colors: StatsColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.subtle)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(valueLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.ink)
        Text(timeLabel, style = MaterialTheme.typography.labelSmall, color = colors.muted)
    }
}

@Composable
private fun StatsCard(colors: StatsColors, content: @Composable ColumnScope.() -> Unit) {
    MushafPanel(containerColor = colors.card, content = content)
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
    val scheme = MaterialTheme.colorScheme
    return StatsColors(
        page = scheme.background,
        card = scheme.surface,
        tile = scheme.surfaceContainerHigh,
        subtle = scheme.surfaceContainerLow,
        track = scheme.outlineVariant,
        divider = scheme.outlineVariant,
        ink = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        primary = scheme.primary,
        secondary = scheme.tertiary,
        warm = scheme.secondary,
        onPrimary = scheme.onPrimary,
        heroStart = scheme.primaryContainer,
        heroEnd = scheme.primaryContainer,
        heroInk = scheme.onPrimaryContainer,
        heroMuted = scheme.onPrimaryContainer.copy(alpha = 0.72f),
        heroAccent = scheme.secondary,
        heroButton = scheme.surfaceVariant,
        heroFact = scheme.surfaceVariant,
        heroFactInk = scheme.onSurface,
        heroFactMuted = scheme.onSurfaceVariant,
    )
}

private data class StatsColors(
    val page: Color,
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

private fun formatPace(pagesPerMinute: Double): String {
    if (pagesPerMinute <= 0.0) return "-"
    if (pagesPerMinute >= 1.0) return "${pagesPerMinute.roundToInt().toArabicDigits()} صفحة في الدقيقة"
    val minutesPerPage = (1.0 / pagesPerMinute).roundToInt().coerceAtLeast(1)
    return "صفحة كل ${minutesPerPage.toArabicDigits()} دقيقة"
}

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
