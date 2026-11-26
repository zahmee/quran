package com.mushaf.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.sp
import com.mushaf.reader.data.stats.SessionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

@Composable
internal fun CalendarTab(sessions: List<SessionEntity>, colors: StatsColors) {
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
                            valueLabel = "${formatDuration(s.durationMs)} • ${s.pagesRead.toArabicDigits()} صفحة",
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
