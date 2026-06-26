package com.mushaf.reader.reader

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.WidthFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** App settings for reader controls, header display, app info, and local reading data. */
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
    showSurahNumber: Boolean,
    onShowSurahNumberChange: (Boolean) -> Unit,
    showSurahAyahCount: Boolean,
    onShowSurahAyahCountChange: (Boolean) -> Unit,
    showSurahProgress: Boolean,
    onShowSurahProgressChange: (Boolean) -> Unit,
    showJuzProgressPercent: Boolean,
    onShowJuzProgressPercentChange: (Boolean) -> Unit,
    showJuzProgressPages: Boolean,
    onShowJuzProgressPagesChange: (Boolean) -> Unit,
    clockColor: String,
    onClockColorChange: (String) -> Unit,
    sessionTimerColor: String,
    onSessionTimerColorChange: (String) -> Unit,
    showButtonPage: Boolean,
    onShowButtonPageChange: (Boolean) -> Unit,
    buttonPageColor: String,
    onButtonPageColorChange: (String) -> Unit,
    showButtonJuzBar: Boolean,
    onShowButtonJuzBarChange: (Boolean) -> Unit,
    buttonJuzBarColor: String,
    onButtonJuzBarColorChange: (String) -> Unit,
    showBottomJuzBar: Boolean,
    onShowBottomJuzBarChange: (Boolean) -> Unit,
    bottomJuzBarColor: String,
    onBottomJuzBarColorChange: (String) -> Unit,
    onAbout: () -> Unit,
    onClearAllStats: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }

    val moreMenuControls = listOf(
        HeaderControl("search", "البحث", "أول خيار داخل قائمة المزيد.", Icons.Outlined.Search),
        HeaderControl("bookmark", "العلامة المرجعية", "الانتقال إلى الفاصل الذهبي المحفوظ.", Icons.Outlined.BookmarkBorder),
        HeaderControl("bookmark2", "العلامة المرجعية الثانية", "الانتقال إلى الفاصل البنفسجي المحفوظ.", Icons.Outlined.BookmarkBorder),
        HeaderControl("stats", "إحصائيات القراءة", "متابعة القراءة والختمة والجلسات.", Icons.Outlined.QueryStats),
        HeaderControl("index", "الفهرس", "فتح السور والأجزاء من القائمة.", Icons.AutoMirrored.Outlined.MenuBook),
        HeaderControl("fill", "ملء الصفحة", "تكبير صفحة المصحف من قائمة المزيد.", Icons.Outlined.WidthFull),
        HeaderControl("theme", "الوضع الليلي", "تبديل المظهر من قائمة المزيد.", Icons.Outlined.DarkMode),
    )

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
                        text = "الإعدادات",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            SettingsTabs(tab = tab, onTab = { tab = it })

            when (tab) {
                0 -> SettingsTabScroll {
                SettingsPanel(
                    title = "رأس الصفحة الجديد",
                    body = "الزر الثابت للإعدادات، زر الإخفاء، الوقت، ومدة الجلسة.",
                    icon = Icons.Outlined.Tune
                ) {
                    HeaderLayoutPreview(
                        showClock = showClock,
                        showSessionTimer = showSessionTimer,
                        showHideButton = isVisible("hide")
                    )
                    SoftDivider()
                    ToggleSettingRow(
                        icon = Icons.Filled.KeyboardArrowUp,
                        title = "زر إخفاء الشريط العلوي",
                        body = "يعرض سهماً في رأس الصفحة لإخفاء الشريط والقراءة بلا مشتتات.",
                        checked = isVisible("hide"),
                        onCheckedChange = { onToggle("hide", it) }
                    )
                    SoftDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.Tune,
                        title = "تكبير أزرار الرأس",
                        body = "يزيد مساحة الضغط على زر الإعدادات والإخفاء وقائمة المزيد.",
                        checked = bigButtons,
                        onCheckedChange = onBigButtonsChange
                    )
                    SoftDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.WatchLater,
                        title = "عرض الساعة",
                        body = "إظهار الوقت الحالي بجانب زر الإعدادات.",
                        checked = showClock,
                        onCheckedChange = onShowClockChange
                    )
                    ColorChoiceRow(
                        title = "لون الساعة",
                        selected = clockColor,
                        onSelected = onClockColorChange
                    )
                    SoftDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.Timer,
                        title = "مدة الجلسة",
                        body = "إظهار مدة جلسة القراءة بجانب زر الإخفاء.",
                        checked = showSessionTimer,
                        onCheckedChange = onShowSessionTimerChange
                    )
                    ColorChoiceRow(
                        title = "لون مدة الجلسة",
                        selected = sessionTimerColor,
                        onSelected = onSessionTimerColorChange
                    )
                }

                SettingsPanel(
                    title = "زر إظهار رأس الصفحة",
                    body = "الزر العائم في وضع كامل الشاشة، وشريط تقدم الجزء أسفل الصفحة.",
                    icon = Icons.Outlined.WidthFull
                ) {
                    ToggleSettingRow(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = "عرض رقم الصفحة في الزر",
                        body = "إظهار رقم الصفحة الحالية داخل زر إظهار الرأس.",
                        checked = showButtonPage,
                        onCheckedChange = onShowButtonPageChange
                    )
                    ColorChoiceRow(
                        title = "لون رقم الصفحة",
                        selected = buttonPageColor,
                        onSelected = onButtonPageColorChange
                    )
                    SoftDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.QueryStats,
                        title = "عرض تقدم الجزء في الزر",
                        body = "شريط صغير أسفل رقم الصفحة يعرض موضعك في الجزء.",
                        checked = showButtonJuzBar,
                        onCheckedChange = onShowButtonJuzBarChange
                    )
                    ColorChoiceRow(
                        title = "لون شريط الجزء في الزر",
                        selected = buttonJuzBarColor,
                        onSelected = onButtonJuzBarColorChange
                    )
                    SoftDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.WidthFull,
                        title = "شريط تقدم الجزء أسفل الصفحة",
                        body = "شريط رفيع ثابت في أسفل الصفحة يعرض تقدمك في الجزء الحالي.",
                        checked = showBottomJuzBar,
                        onCheckedChange = onShowBottomJuzBarChange
                    )
                    ColorChoiceRow(
                        title = "لون الشريط السفلي",
                        selected = bottomJuzBarColor,
                        onSelected = onBottomJuzBarColorChange
                    )
                }
                }
                1 -> SettingsTabScroll {
                SettingsPanel(
                    title = "قائمة المزيد",
                    body = "هذه العناصر تظهر داخل زر النقاط الثلاث في رأس الصفحة.",
                    icon = Icons.Filled.MoreVert
                ) {
                    MenuLocationHint()
                    SoftDivider()
                    moreMenuControls.forEachIndexed { index, item ->
                        ToggleSettingRow(
                            icon = item.icon,
                            title = item.title,
                            body = item.body,
                            checked = isVisible(item.id),
                            onCheckedChange = { onToggle(item.id, it) }
                        )
                        if (index != moreMenuControls.lastIndex) SoftDivider()
                    }
                }
                }
                2 -> SettingsTabScroll {
                SettingsPanel(
                    title = "معلومات رأس الصفحة",
                    body = "تفاصيل السورة والجزء في السطر الهادئ أسفل الأزرار.",
                    icon = Icons.AutoMirrored.Outlined.MenuBook
                ) {
                    ToggleSettingRow(
                        icon = Icons.Outlined.Info,
                        title = "رقم السورة",
                        body = "إظهار الرقم قبل اسم السورة.",
                        checked = showSurahNumber,
                        onCheckedChange = onShowSurahNumberChange
                    )
                    SoftDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.Info,
                        title = "عدد آيات السورة",
                        body = "إظهار عدد الآيات بجانب اسم السورة.",
                        checked = showSurahAyahCount,
                        onCheckedChange = onShowSurahAyahCountChange
                    )
                    SoftDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.QueryStats,
                        title = "تقدم السورة",
                        body = "إظهار نسبة تقدمك داخل السورة الحالية.",
                        checked = showSurahProgress,
                        onCheckedChange = onShowSurahProgressChange
                    )
                    SoftDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.QueryStats,
                        title = "تقدم الجزء كنسبة",
                        body = "إظهار نسبة التقدم في الجزء الحالي.",
                        checked = showJuzProgressPercent,
                        onCheckedChange = onShowJuzProgressPercentChange
                    )
                    SoftDivider()
                    ToggleSettingRow(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = "تقدم الجزء بالصفحات",
                        body = "إظهار رقم الصفحة داخل الجزء، مثل ٨/٢١.",
                        checked = showJuzProgressPages,
                        onCheckedChange = onShowJuzProgressPagesChange
                    )
                }
                }
                else -> SettingsTabScroll {
                SettingsPanel(
                    title = "معلومات التطبيق",
                    body = "مصدر المصحف، الخصوصية، الجهة المطوّرة، وطرق التواصل.",
                    icon = Icons.Outlined.Info
                ) {
                    ActionRow(
                        icon = Icons.Outlined.Info,
                        title = "حول التطبيق",
                        body = "معلومات الثقة والتواصل والإصدار.",
                        onClick = onAbout
                    )
                }

                DangerPanel(onClear = { confirmClear = true })
                Spacer(Modifier.height(8.dp))
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

/** Top category tabs that split the settings into short, focused sections. */
@Composable
private fun SettingsTabs(tab: Int, onTab: (Int) -> Unit) {
    val titles = listOf("الواجهة", "القائمة", "المعلومات", "التطبيق")
    TabRow(selectedTabIndex = tab) {
        titles.forEachIndexed { i, title ->
            Tab(
                selected = tab == i,
                onClick = { onTab(i) },
                text = {
                    Text(
                        text = title,
                        maxLines = 1,
                        fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

/** Scroll container shared by every tab so each section scrolls on its own. */
@Composable
private fun SettingsTabScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

private data class HeaderControl(
    val id: String,
    val title: String,
    val body: String,
    val icon: ImageVector,
)

@Composable
private fun HeaderLayoutPreview(
    showClock: Boolean,
    showSessionTimer: Boolean,
    showHideButton: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallIconBadge(Icons.Filled.Settings)
                if (showClock) StatusPill("الساعة")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showSessionTimer) StatusPill("الجلسة")
                if (showHideButton) SmallIconBadge(Icons.Filled.KeyboardArrowUp)
                SmallIconBadge(Icons.Filled.MoreVert)
            }
        }
        Text(
            text = "الإعدادات ثابتة دائماً، أما البحث والفهرس وباقي الأدوات ففي قائمة المزيد.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MenuLocationHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallIconBadge(Icons.Filled.MoreVert)
        Text(
            text = "إخفاء أي خيار هنا يزيله من القائمة الجانبية في رأس الصفحة.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun SettingsPanel(
    title: String,
    body: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Panel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(icon)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun ToggleSettingRow(
    icon: ImageVector,
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallIconBadge(icon)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallIconBadge(icon)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ColorChoiceRow(
    title: String,
    selected: String,
    onSelected: (String) -> Unit,
) {
    val choices = listOf(
        HeaderColorChoice("muted", "هادئ", MaterialTheme.colorScheme.onSurfaceVariant),
        HeaderColorChoice("green", "أخضر", Color(0xFF2E9E45)),
        HeaderColorChoice("red", "أحمر", Color(0xFFE53935)),
        HeaderColorChoice("gold", "ذهبي", Color(0xFFC28A16)),
        HeaderColorChoice("blue", "أزرق", Color(0xFF2F6FE4)),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        listOf(choices.take(3), choices.drop(3)).forEach { rowChoices ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowChoices.forEach { choice ->
                    ColorChoiceChip(choice, selected == choice.id) { onSelected(choice.id) }
                }
            }
        }
    }
}

@Composable
private fun ColorChoiceChip(choice: HeaderColorChoice, checked: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (checked) choice.color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) choice.color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier.size(12.dp),
                shape = CircleShape,
                color = choice.color
            ) {}
            Text(
                text = choice.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (checked) choice.color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class HeaderColorChoice(
    val id: String,
    val label: String,
    val color: Color,
)

@Composable
private fun DangerPanel(onClear: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "بيانات القراءة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "إدارة جلسات القراءة وتقدّم الختمة المحفوظة على جهازك.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            FilledTonalButton(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("مسح كل الإحصائيات", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(9.dp)
        )
    }
}

@Composable
private fun SmallIconBadge(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun SoftDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}
