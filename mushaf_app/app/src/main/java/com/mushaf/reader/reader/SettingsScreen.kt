package com.mushaf.reader.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** App settings. For now: show/hide each of the header buttons.
 *  Each id matches the ones checked in [ReaderHeader] / [ReaderViewModel.isButtonVisible]. */
@Composable
fun SettingsScreen(
    isVisible: (String) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
    bigButtons: Boolean,
    onBigButtonsChange: (Boolean) -> Unit,
    showClock: Boolean,
    onShowClockChange: (Boolean) -> Unit,
    showSessionTimer: Boolean,
    onShowSessionTimerChange: (Boolean) -> Unit,
    onClearAllStats: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    // Listed in the header's reading order; the settings button itself is intentionally absent
    // (it must always stay reachable).
    val buttons = listOf(
        "index" to "الفهرس",
        "theme" to "الوضع الليلي",
        "fill" to "توسيع الصفحة",
        "stats" to "الإحصائيات",
        "search" to "البحث",
        "hide" to "إخفاء الشريط العلوي",
        "bookmark" to "العلامة المرجعية (الفاصل)",
    )

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
                        text = "الإعدادات",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card {
                    Text(
                        text = "أزرار رأس الصفحة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "اختر الأزرار التي تظهر في رأس الصفحة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    buttons.forEach { (id, label) ->
                        ToggleRow(
                            label = label,
                            checked = isVisible(id),
                            onCheckedChange = { onToggle(id, it) }
                        )
                    }
                }

                Card {
                    Text(
                        text = "حجم الأزرار",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "تكبير أزرار رأس الصفحة قليلاً لتسهيل الضغط عليها.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        label = "تكبير الأزرار",
                        checked = bigButtons,
                        onCheckedChange = onBigButtonsChange
                    )
                }

                Card {
                    Text(
                        text = "عناصر إضافية في الرأس",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "إظهار الساعة (بالأحمر) ومدة الجلسة الحالية (بالأخضر) في رأس الصفحة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        label = "عرض الساعة",
                        checked = showClock,
                        onCheckedChange = onShowClockChange
                    )
                    ToggleRow(
                        label = "عرض مدة الجلسة الحالية",
                        checked = showSessionTimer,
                        onCheckedChange = onShowSessionTimerChange
                    )
                }

                Card {
                    Text(
                        text = "الإحصائيات",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "حذف كل جلسات القراءة وتقدّم الختمة. يمكنك أيضاً حذف عناصر مفردة من شاشة الإحصائيات.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { confirmClear = true }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "مسح كل الإحصائيات",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح كل الإحصائيات") },
            text = { Text("سيتم حذف جميع جلسات القراءة وتقدّم الختمة نهائياً. لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAllStats()
                }) {
                    Text("مسح", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
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
