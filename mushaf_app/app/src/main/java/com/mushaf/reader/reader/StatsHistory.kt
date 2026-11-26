package com.mushaf.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.stats.SessionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

@Composable
internal fun HistoryTab(
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
                valueLabel = "${formatDuration(s.durationMs)} • ${s.pagesRead.toArabicDigits()} صفحة",
                colors = colors,
            )
        }
    }
}

@Composable
internal fun SessionRow(timeLabel: String, valueLabel: String, colors: StatsColors) {
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

internal class DayG(val anchorMillis: Long) {
    val sessions = mutableListOf<SessionEntity>()
    val pages: Int get() = sessions.sumOf { it.pagesRead }
    val durationMs: Long get() = sessions.sumOf { it.durationMs }
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
