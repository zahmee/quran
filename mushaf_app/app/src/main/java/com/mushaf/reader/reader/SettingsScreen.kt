package com.mushaf.reader.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.WidthFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mushaf.reader.ui.components.MushafIconBadge
import com.mushaf.reader.ui.components.MushafPanel
import com.mushaf.reader.ui.components.MushafSectionHeader
import com.mushaf.reader.ui.components.MushafSegmentedTabs
import com.mushaf.reader.ui.components.MushafSoftDivider
import com.mushaf.reader.ui.components.MushafTopBar
import com.mushaf.reader.ui.theme.MushafPalette
import com.mushaf.reader.ui.theme.MushafPalettes
import com.mushaf.reader.ui.theme.StatusBlueColor
import com.mushaf.reader.ui.theme.StatusGoldColor
import com.mushaf.reader.ui.theme.StatusGreenColor
import com.mushaf.reader.ui.theme.StatusRedColor

/** App settings for reader controls, header display, app info, and local reading data. */
@Composable
fun SettingsScreen(
    isVisible: (String) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
    paletteId: String,
    onPaletteChange: (String) -> Unit,
    isInBar: (String) -> Boolean,
    onSetInBar: (String, Boolean) -> Unit,
    buttonColor: (String) -> String,
    onButtonColorChange: (String, String) -> Unit,
    bigButtons: Boolean,
    onBigButtonsChange: (Boolean) -> Unit,
    verticalPaging: Boolean,
    onVerticalPagingChange: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
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
    showHeaderButtonOpacity: Int,
    onShowHeaderButtonOpacityChange: (Int) -> Unit,
    showBottomJuzBar: Boolean,
    onShowBottomJuzBarChange: (Boolean) -> Unit,
    bottomJuzBarColor: String,
    onBottomJuzBarColorChange: (String) -> Unit,
    bottomJuzBarThickness: Int,
    onBottomJuzBarThicknessChange: (Int) -> Unit,
    bottomJuzBarOpacity: Int,
    onBottomJuzBarOpacityChange: (Int) -> Unit,
    showTopSurahBar: Boolean,
    onShowTopSurahBarChange: (Boolean) -> Unit,
    topSurahBarColor: String,
    onTopSurahBarColorChange: (String) -> Unit,
    topSurahBarThickness: Int,
    onTopSurahBarThicknessChange: (Int) -> Unit,
    topSurahBarOpacity: Int,
    onTopSurahBarOpacityChange: (Int) -> Unit,
    showPageSideIndicator: Boolean,
    onShowPageSideIndicatorChange: (Boolean) -> Unit,
    pageSideIndicatorColor: String,
    onPageSideIndicatorColorChange: (String) -> Unit,
    pageSideIndicatorThickness: Int,
    onPageSideIndicatorThicknessChange: (Int) -> Unit,
    pageSideIndicatorLength: Int,
    onPageSideIndicatorLengthChange: (Int) -> Unit,
    pageSideIndicatorOpacity: Int,
    onPageSideIndicatorOpacityChange: (Int) -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    onClearAllStats: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            MushafTopBar(title = "الإعدادات", onBack = onBack)

            SettingsTabs(tab = tab, onTab = { tab = it })

            when (tab) {
                0 -> SettingsTabScroll {
                SettingsPanel(
                    title = "المظهر",
                    body = "لون ورق المصحف وحبره، ويتبعه لون واجهة التطبيق.",
                    icon = Icons.Outlined.Palette
                ) {
                    ThemeChoiceGrid(selected = paletteId, onSelected = onPaletteChange)
                }

                SettingsPanel(
                    title = "تصفّح الصفحات",
                    body = "اتجاه تقليب صفحات المصحف.",
                    icon = Icons.Outlined.SwapVert
                ) {
                    ToggleSettingRow(
                        icon = Icons.Outlined.SwapVert,
                        title = "تصفّح رأسي",
                        body = "تقليب الصفحات بالسحب لأعلى وأسفل بدل اليمين واليسار.",
                        checked = verticalPaging,
                        onCheckedChange = onVerticalPagingChange
                    )
                }

                SettingsPanel(
                    title = "أثناء القراءة",
                    body = "سلوك الشاشة ما دام المصحف مفتوحاً أمامك.",
                    icon = Icons.Outlined.LightMode
                ) {
                    ToggleSettingRow(
                        icon = Icons.Outlined.LightMode,
                        title = "إبقاء الشاشة مضاءة",
                        body = "منع إطفاء الشاشة وقفلها أثناء القراءة. إطفاؤه يوفّر البطارية.",
                        checked = keepScreenOn,
                        onCheckedChange = onKeepScreenOnChange
                    )
                }

                SettingsPanel(
                    title = "رأس الصفحة الجديد",
                    body = "الإعدادات، عرض الصفحة، توسيع مساحة القراءة، ومعلومات الجلسة.",
                    icon = Icons.Outlined.Tune
                ) {
                    HeaderLayoutPreview(
                        showClock = showClock,
                        showSessionTimer = showSessionTimer,
                        showFillButton = isVisible("fill"),
                        showHideButton = isVisible("hide")
                    )
                    SoftDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.Tune,
                        title = "تكبير أزرار الرأس",
                        body = "يزيد مساحة الضغط على زر الإعدادات وعرض الصفحة والتوسيع وقائمة المزيد.",
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
                    body = "الزر العائم في وضع كامل الشاشة، وإشارة اتجاه الصفحة.",
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
                    OpacityChoiceRow(
                        title = "شفافية زر إظهار الرأس",
                        selected = showHeaderButtonOpacity,
                        onSelected = onShowHeaderButtonOpacityChange
                    )
                    SoftDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.VerticalSplit,
                        title = "إشارة اتجاه الصفحة",
                        body = "شريط صغير على حافة الشاشة في وضع ملء الشاشة: على اليمين للصفحة اليمنى، وعلى اليسار للصفحة اليسرى.",
                        checked = showPageSideIndicator,
                        onCheckedChange = onShowPageSideIndicatorChange
                    )
                    ColorChoiceRow(
                        title = "لون الإشارة",
                        selected = pageSideIndicatorColor,
                        onSelected = onPageSideIndicatorColorChange
                    )
                    ThicknessChoiceRow(
                        title = "سمك الإشارة",
                        selected = pageSideIndicatorThickness,
                        onSelected = onPageSideIndicatorThicknessChange
                    )
                    LengthChoiceRow(
                        title = "طول الإشارة",
                        selected = pageSideIndicatorLength,
                        onSelected = onPageSideIndicatorLengthChange
                    )
                    OpacityChoiceRow(
                        title = "شفافية الإشارة",
                        selected = pageSideIndicatorOpacity,
                        onSelected = onPageSideIndicatorOpacityChange
                    )
                }

                SettingsPanel(
                    title = "أشرطة التقدم",
                    body = "خطان رفيعان يمتلئان من اليمين: أعلى الصفحة لتقدم السورة، وأسفلها لتقدم الجزء.",
                    icon = Icons.Outlined.QueryStats
                ) {
                    ToggleSettingRow(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = "شريط تقدم السورة أعلى الصفحة",
                        body = "خط رفيع فوق الصفحة يعرض موضعك داخل السورة التي تقرؤها.",
                        checked = showTopSurahBar,
                        onCheckedChange = onShowTopSurahBarChange
                    )
                    ColorChoiceRow(
                        title = "لون الشريط العلوي",
                        selected = topSurahBarColor,
                        onSelected = onTopSurahBarColorChange
                    )
                    ThicknessChoiceRow(
                        title = "سمك الشريط العلوي",
                        selected = topSurahBarThickness,
                        onSelected = onTopSurahBarThicknessChange
                    )
                    OpacityChoiceRow(
                        title = "شفافية الشريط العلوي",
                        selected = topSurahBarOpacity,
                        onSelected = onTopSurahBarOpacityChange
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
                    ThicknessChoiceRow(
                        title = "سمك الشريط السفلي",
                        selected = bottomJuzBarThickness,
                        onSelected = onBottomJuzBarThicknessChange
                    )
                    OpacityChoiceRow(
                        title = "شفافية الشريط السفلي",
                        selected = bottomJuzBarOpacity,
                        onSelected = onBottomJuzBarOpacityChange
                    )
                }
                }
                1 -> SettingsTabScroll {
                SettingsPanel(
                    title = "أزرار رأس الصفحة",
                    body = "لكل عملية: إظهارها، ومكانها المفضّل، ولونها في الشريط. تنتقل الأدوات الزائدة إلى قائمة المزيد على الشاشة الضيقة.",
                    icon = Icons.Filled.MoreVert
                ) {
                    MenuLocationHint()
                    SoftDivider()
                    HeaderActions.forEachIndexed { index, action ->
                        ToggleSettingRow(
                            icon = action.settingsIcon,
                            title = action.title,
                            body = action.body,
                            checked = isVisible(action.id),
                            onCheckedChange = { onToggle(action.id, it) }
                        )
                        // Placement and color only mean something once the action is shown at all,
                        // and the color only once it has an icon of its own on the bar.
                        if (isVisible(action.id)) {
                            PlacementChoiceRow(
                                inBar = isInBar(action.id),
                                onSelected = { onSetInBar(action.id, it) }
                            )
                            if (isInBar(action.id)) {
                                ColorChoiceRow(
                                    title = "لونه في الشريط",
                                    selected = buttonColor(action.id),
                                    onSelected = { onButtonColorChange(action.id, it) }
                                )
                            }
                        }
                        if (index != HeaderActions.lastIndex) SoftDivider()
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
                    body = "النسخ الاحتياطي، مصدر المصحف، الخصوصية، وطرق التواصل.",
                    icon = Icons.Outlined.Info
                ) {
                    ActionRow(
                        icon = Icons.Outlined.CloudSync,
                        title = "النسخ الاحتياطي",
                        body = "حفظ التقدم والإشارات والجلسات في ملف تختار مكانه.",
                        onClick = onBackup
                    )
                    SoftDivider()
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
        val canClear = confirmText.trim() == "مسح"
        AlertDialog(
            onDismissRequest = { confirmClear = false; confirmText = "" },
            title = { Text("مسح سجل القراءة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("سيُحذف سجل الجلسات وتقدّم الختمة الحالية نهائياً. لا يمكن التراجع. سجل الختمات المحفوظة لن يتأثر.")
                    Text(
                        "للتأكيد، اكتب كلمة «مسح» في الحقل:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        singleLine = true,
                        isError = confirmText.isNotEmpty() && !canClear,
                        placeholder = { Text("مسح") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canClear,
                    onClick = {
                        confirmClear = false
                        confirmText = ""
                        onClearAllStats()
                    }
                ) {
                    Text(
                        "مسح نهائي",
                        color = if (canClear) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false; confirmText = "" }) { Text("إلغاء") }
            }
        )
    }
}

/** Top category tabs that split the settings into short, focused sections.
 *
 *  Icons, not words: at four tabs across a phone, "المعلومات" wrapped onto a second line and left
 *  that tab visibly taller than its neighbours. The Arabic name stays as each tab's content
 *  description. */
@Composable
private fun SettingsTabs(tab: Int, onTab: (Int) -> Unit) {
    val titles = listOf("الواجهة", "القائمة", "المعلومات", "التطبيق")
    val icons = listOf(
        Icons.Outlined.Palette,
        Icons.Outlined.Menu,
        Icons.Outlined.Info,
        Icons.Outlined.PhoneAndroid,
    )
    MushafSegmentedTabs(
        labels = titles,
        selectedIndex = tab,
        onSelected = onTab,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        icons = icons,
    )
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

@Composable
private fun HeaderLayoutPreview(
    showClock: Boolean,
    showSessionTimer: Boolean,
    showFillButton: Boolean,
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
                if (showFillButton) SmallIconBadge(Icons.Outlined.WidthFull)
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
            text = "كل عملية إمّا مخفية، أو زرّاً ملوّناً في الشريط عند توفر المساحة، أو سطراً داخل قائمة النقاط الثلاث.",
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
        MushafSectionHeader(title = title, subtitle = body, icon = icon)
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

/** The reading themes as a grid of page thumbnails: each swatch is painted in that theme's own
 *  paper and ink, so the choice is made by looking rather than by reading a color name. */
@Composable
private fun ThemeChoiceGrid(
    selected: String,
    onSelected: (String) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MushafPalettes.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { palette ->
                    ThemeChoiceTile(
                        palette = palette,
                        selected = palette.id == selected,
                        accent = accent,
                        onClick = { onSelected(palette.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeChoiceTile(
    palette: MushafPalette,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = palette.paper,
            border = BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) accent else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clickable(onClick = onClick)
        ) {
            // Three ruled lines standing in for the page's text, in that theme's ink.
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf(1f, 1f, 0.55f).forEach { width ->
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = palette.ink.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth(width)
                            .height(4.dp)
                    ) {}
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = palette.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Where one header action lives: a button on the bar itself, or a line in the More menu. */
@Composable
private fun PlacementChoiceRow(
    inBar: Boolean,
    onSelected: (Boolean) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val choices = listOf(
        Triple(true, "الشريط العلوي", Icons.Outlined.Tune),
        Triple(false, "قائمة المزيد", Icons.Filled.MoreVert),
    )
    val current = choices.first { it.first == inBar }
    val color = if (inBar) accent else muted
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "المكان",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = current.third,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = current.second,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "اختيار المكان",
                        tint = color
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = choice.third,
                                    contentDescription = null,
                                    tint = if (choice.first == inBar) color
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = choice.second,
                                    color = if (choice.first == inBar) color
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (choice.first == inBar) FontWeight.SemiBold
                                    else FontWeight.Normal
                                )
                            }
                        },
                        trailingIcon = if (choice.first == inBar) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = color) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelected(choice.first)
                        }
                    )
                }
            }
        }
    }
}

/** Color picker rendered as a compact dropdown menu (label on one side, an anchored pill that
 *  opens the full list of colors). Same signature as before, so every color setting in the screen
 *  becomes a dropdown at once. */
@Composable
private fun ColorChoiceRow(
    title: String,
    selected: String,
    onSelected: (String) -> Unit,
) {
    val choices = listOf(
        HeaderColorChoice("muted", "هادئ", MaterialTheme.colorScheme.onSurfaceVariant),
        HeaderColorChoice("green", "أخضر", StatusGreenColor),
        HeaderColorChoice("red", "أحمر", StatusRedColor),
        HeaderColorChoice("gold", "ذهبي", StatusGoldColor),
        HeaderColorChoice("blue", "أزرق", StatusBlueColor),
    )
    val current = choices.firstOrNull { it.id == selected } ?: choices.first()
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = current.color.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, current.color.copy(alpha = 0.6f)),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(modifier = Modifier.size(12.dp), shape = CircleShape, color = current.color) {}
                    Text(
                        text = current.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = current.color
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "اختيار اللون",
                        tint = current.color
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(modifier = Modifier.size(14.dp), shape = CircleShape, color = choice.color) {}
                                Text(
                                    text = choice.label,
                                    color = if (choice.id == selected) choice.color
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (choice.id == selected) FontWeight.SemiBold
                                    else FontWeight.Normal
                                )
                            }
                        },
                        trailingIcon = if (choice.id == selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = choice.color) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelected(choice.id)
                        }
                    )
                }
            }
        }
    }
}

private data class HeaderColorChoice(
    val id: String,
    val label: String,
    val color: Color,
)

/** Bar-thickness picker, same dropdown shape as [ColorChoiceRow]. Each choice carries a sample line
 *  drawn at its own thickness, so the pick is visible before it is applied to the page. */
@Composable
private fun ThicknessChoiceRow(
    title: String,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val choices = listOf(
        BarThicknessChoice(2, "رفيع جداً"),
        BarThicknessChoice(4, "رفيع"),
        BarThicknessChoice(6, "متوسط"),
        BarThicknessChoice(9, "سميك"),
        BarThicknessChoice(12, "عريض"),
    )
    val accent = MaterialTheme.colorScheme.primary
    // Only this list ever writes the setting, so an unknown value can't normally show up; fall back
    // to the default thickness (4.dp) if it somehow does.
    val current = choices.firstOrNull { it.thickness == selected } ?: choices[1]
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThicknessSample(current.thickness, accent)
                    Text(
                        text = current.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "اختيار السمك",
                        tint = accent
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ThicknessSample(
                                    thickness = choice.thickness,
                                    color = if (choice.thickness == selected) accent
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = choice.label,
                                    color = if (choice.thickness == selected) accent
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (choice.thickness == selected) FontWeight.SemiBold
                                    else FontWeight.Normal
                                )
                            }
                        },
                        trailingIcon = if (choice.thickness == selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = accent) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelected(choice.thickness)
                        }
                    )
                }
            }
        }
    }
}

/** A short line rendered at [thickness] dp — the visual half of a thickness choice. */
@Composable
private fun ThicknessSample(thickness: Int, color: Color) {
    Surface(
        modifier = Modifier
            .width(24.dp)
            .height(thickness.dp),
        shape = RoundedCornerShape(50),
        color = color
    ) {}
}

private data class BarThicknessChoice(
    val thickness: Int,
    val label: String,
)

/** Length picker for the page-side marker — same dropdown shape as [ThicknessChoiceRow], but the
 *  sample stands upright like the marker itself. */
@Composable
private fun LengthChoiceRow(
    title: String,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val choices = listOf(
        BarLengthChoice(24, "قصير جداً"),
        BarLengthChoice(40, "قصير"),
        BarLengthChoice(64, "متوسط"),
        BarLengthChoice(96, "طويل"),
        BarLengthChoice(140, "ممتد"),
    )
    val accent = MaterialTheme.colorScheme.primary
    // Only this list ever writes the setting; fall back to the default length (40.dp) otherwise.
    val current = choices.firstOrNull { it.length == selected } ?: choices[1]
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LengthSample(current.length, accent)
                    Text(
                        text = current.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "اختيار الطول",
                        tint = accent
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LengthSample(
                                    length = choice.length,
                                    color = if (choice.length == selected) accent
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = choice.label,
                                    color = if (choice.length == selected) accent
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (choice.length == selected) FontWeight.SemiBold
                                    else FontWeight.Normal
                                )
                            }
                        },
                        trailingIcon = if (choice.length == selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = accent) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelected(choice.length)
                        }
                    )
                }
            }
        }
    }
}

/** An upright bar standing for a marker [length]. Drawn at a quarter scale so even the longest
 *  choice still fits on one settings row while the choices stay visibly different from each other. */
@Composable
private fun LengthSample(length: Int, color: Color) {
    Surface(
        modifier = Modifier
            .width(4.dp)
            .height((length / 4).dp),
        shape = RoundedCornerShape(50),
        color = color
    ) {}
}

private data class BarLengthChoice(
    val length: Int,
    val label: String,
)

/** Opacity picker (percent), same dropdown shape as the others. The sample swatch is drawn at the
 *  chosen opacity over the row's own background, so «شفاف» reads as barely-there and «معتم» as solid. */
@Composable
private fun OpacityChoiceRow(
    title: String,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val choices = listOf(
        BarOpacityChoice(25, "شفاف جداً"),
        BarOpacityChoice(50, "شفاف"),
        BarOpacityChoice(70, "متوسط"),
        BarOpacityChoice(85, "خفيف"),
        BarOpacityChoice(100, "معتم"),
    )
    val accent = MaterialTheme.colorScheme.primary
    // Only this list ever writes the setting; fall back to fully opaque if an odd value appears.
    val current = choices.firstOrNull { it.opacity == selected } ?: choices.last()
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OpacitySample(current.opacity, accent)
                    Text(
                        text = current.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "اختيار الشفافية",
                        tint = accent
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OpacitySample(
                                    opacity = choice.opacity,
                                    color = if (choice.opacity == selected) accent
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${choice.label} (${choice.opacity.toArabicDigits()}٪)",
                                    color = if (choice.opacity == selected) accent
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (choice.opacity == selected) FontWeight.SemiBold
                                    else FontWeight.Normal
                                )
                            }
                        },
                        trailingIcon = if (choice.opacity == selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = accent) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelected(choice.opacity)
                        }
                    )
                }
            }
        }
    }
}

/** A small filled dot drawn at [opacity] percent — the visual half of an opacity choice. */
@Composable
private fun OpacitySample(opacity: Int, color: Color) {
    Surface(
        modifier = Modifier.size(16.dp),
        shape = CircleShape,
        color = color.copy(alpha = opacity / 100f)
    ) {}
}

private data class BarOpacityChoice(
    val opacity: Int,
    val label: String,
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
                        text = "سجل القراءة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "سجل الجلسات محفوظ دائماً. المسح نهائي ويتطلب تأكيداً بكتابة كلمة «مسح». سجل الختمات لا يتأثر.",
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
                Text("مسح سجل القراءة", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    MushafIconBadge(icon = icon)
}

@Composable
private fun SmallIconBadge(icon: ImageVector) {
    MushafIconBadge(icon = icon, size = 32.dp, iconSize = 16.dp)
}

@Composable
private fun SoftDivider() {
    MushafSoftDivider(verticalPadding = 4.dp)
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    MushafPanel(content = content)
}
