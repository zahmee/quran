package com.mushaf.reader.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.stats.FullStats
import com.mushaf.reader.data.stats.KhatmaEntity
import com.mushaf.reader.data.stats.SessionEntity
import com.mushaf.reader.ui.components.MushafSegmentedTabs
import com.mushaf.reader.ui.components.MushafTopBar
import java.util.Locale

internal val ArabicLocale: Locale = Locale.forLanguageTag("ar")

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
                        "ثم تبدأ ختمة جديدة تلقائياً من الصفحة ١. سجل الجلسات لا يتأثر."
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
                    "سيُمسح تقدّمك في الصفحات والأجزاء للختمة الحالية لتبدأ من جديد من الصفحة ١. " +
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
