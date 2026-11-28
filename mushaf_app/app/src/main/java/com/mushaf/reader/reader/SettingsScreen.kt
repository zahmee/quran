package com.mushaf.reader.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.WidthFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.mushaf.reader.ui.components.MushafSegmentedTabs
import com.mushaf.reader.ui.components.MushafTopBar

/** App settings for reader controls, header display, app info, and local reading data. */
@Composable
fun SettingsScreen(
    isVisible: (String) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
    paletteId: String,
    onPaletteChange: (String) -> Unit,
    isInBar: (String) -> Boolean,
    onSetInBar: (String, Boolean) -> Unit,
    /** Actions placed on the bar that the reader's current window has no room for. */
    barOverflow: Set<String>,
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
    showReadingPosition: Boolean,
    onShowReadingPositionChange: (Boolean) -> Unit,
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
    edgeMargin: String,
    onEdgeMarginChange: (String) -> Unit,
    showNoteMarks: Boolean,
    onShowNoteMarksChange: (Boolean) -> Unit,
    noteMarkOpacity: Int,
    onNoteMarkOpacityChange: (Int) -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    onTafsirLibrary: () -> Unit,
    onClearAllStats: () -> Unit,
    /** The «التلاوة» tab's content, built by the reader, which owns recitation. */
    recitationTab: @Composable ColumnScope.() -> Unit,
    /** Below the header buttons in the «القائمة» tab: the reader's آناء section. */
    menuTabExtra: @Composable ColumnScope.() -> Unit = {},
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The tab strip is icons now, so the open tab is named here instead — the same
            // quiet subtitle the backup and index screens use, not a second heading.
            MushafTopBar(
                title = "الإعدادات",
                onBack = onBack,
                subtitle = settingsTabTitles.getOrNull(tab),
            )

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
                    title = "علامة الملاحظات",
                    body = "نقطة صغيرة بجانب رقم كل آية كتبتَ عليها ملاحظة.",
                    icon = Icons.AutoMirrored.Outlined.StickyNote2
                ) {
                    ToggleSettingRow(
                        icon = Icons.AutoMirrored.Outlined.StickyNote2,
                        title = "إظهار علامة الملاحظات على الآيات",
                        body = "تُمزج العلامة بالصفحة فلا تحجب الحروف ولا التشكيل تحتها.",
                        checked = showNoteMarks,
                        onCheckedChange = onShowNoteMarksChange
                    )
                    if (showNoteMarks) {
                        NoteMarkStrengthRow(
                            percent = noteMarkOpacity,
                            onPercentChange = onNoteMarkOpacityChange
                        )
                    }
                }

                SettingsPanel(
                    title = "حواف الشاشة",
                    body = "للجوالات ذات الزوايا الدائرية أو الحواف المنحنية.",
                    icon = Icons.Outlined.RoundedCorner
                ) {
                    EdgeMarginChoiceRow(
                        selected = edgeMargin,
                        onSelected = onEdgeMarginChange
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
                    SettingsDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.Tune,
                        title = "تكبير أزرار الرأس",
                        body = "يزيد مساحة الضغط على زر الإعدادات وعرض الصفحة والتوسيع وقائمة المزيد.",
                        checked = bigButtons,
                        onCheckedChange = onBigButtonsChange
                    )
                    SettingsDivider()
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
                    SettingsDivider()
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
                    SettingsDivider()
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
                    SettingsDivider()
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
                    body = "لكل عملية: إظهارها، ومكانها المفضّل، ولونها في الشريط. إذا ضاق الشريط بقي فيه أولاً زرّا عرض الصفحة وتوسيع القراءة ثم العلامة المرجعية، وانتقل الباقي إلى قائمة المزيد.",
                    icon = Icons.Filled.MoreVert
                ) {
                    MenuLocationHint()
                    SettingsDivider()
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
                            if (isInBar(action.id) && action.id in barOverflow) BarOverflowNote()
                            if (isInBar(action.id)) {
                                ColorChoiceRow(
                                    title = "لونه في الشريط",
                                    selected = buttonColor(action.id),
                                    onSelected = { onButtonColorChange(action.id, it) }
                                )
                            }
                        }
                        if (index != HeaderActions.lastIndex) SettingsDivider()
                    }
                }
                menuTabExtra()
                }
                2 -> SettingsTabScroll {
                SettingsPanel(
                    title = "موضع القراءة",
                    body = "بيان مؤقت عند علامة الحزب المطبوعة في المصحف.",
                    icon = Icons.AutoMirrored.Outlined.MenuBook
                ) {
                    ToggleSettingRow(
                        icon = Icons.Outlined.Info,
                        title = "إظهار الجزء والحزب",
                        body = "يظهر البيان أسفل علامة الحزب لمدة ٣ ثوانٍ، أو أسفل الصفحة إذا لم توجد علامة.",
                        checked = showReadingPosition,
                        onCheckedChange = onShowReadingPositionChange
                    )
                }
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
                    SettingsDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.Info,
                        title = "عدد آيات السورة",
                        body = "إظهار عدد الآيات بجانب اسم السورة.",
                        checked = showSurahAyahCount,
                        onCheckedChange = onShowSurahAyahCountChange
                    )
                    SettingsDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.QueryStats,
                        title = "تقدم السورة",
                        body = "إظهار نسبة تقدمك داخل السورة الحالية.",
                        checked = showSurahProgress,
                        onCheckedChange = onShowSurahProgressChange
                    )
                    SettingsDivider()
                    ToggleSettingRow(
                        icon = Icons.Outlined.QueryStats,
                        title = "تقدم الجزء كنسبة",
                        body = "إظهار نسبة التقدم في الجزء الحالي.",
                        checked = showJuzProgressPercent,
                        onCheckedChange = onShowJuzProgressPercentChange
                    )
                    SettingsDivider()
                    ToggleSettingRow(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = "تقدم الجزء بالصفحات",
                        body = "إظهار رقم الصفحة داخل الجزء، مثل ٨/٢١.",
                        checked = showJuzProgressPages,
                        onCheckedChange = onShowJuzProgressPagesChange
                    )
                }
                }
                3 -> SettingsTabScroll { recitationTab() }
                else -> SettingsTabScroll {
                SettingsPanel(
                    title = "المحتوى الإضافي",
                    body = "كتب تُنزَّل عند الطلب وتُقرأ دون اتصال.",
                    icon = Icons.AutoMirrored.Outlined.MenuBook
                ) {
                    ActionRow(
                        icon = Icons.Outlined.Download,
                        title = "مكتبة التفاسير",
                        body = "تفسير ابن كثير والطبري والبغوي وإعراب القرآن، في قائمة المصادر بنافذة التفسير، ويمكن تثبيت أيٍّ منها للتنقّل إليه بالسحب.",
                        onClick = onTafsirLibrary
                    )
                }

                SettingsPanel(
                    title = "معلومات التطبيق",
                    body = "النسخ الاحتياطي، مصدر المصحف، الخصوصية، وطرق التواصل.",
                    icon = Icons.Outlined.Info
                ) {
                    ActionRow(
                        icon = Icons.Outlined.CloudSync,
                        title = "النسخ الاحتياطي",
                        body = "حفظ التقدم والإشارات والجلسات في حسابك السحابي أو في ملف تختار مكانه.",
                        onClick = onBackup
                    )
                    SettingsDivider()
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

/** The tab names, in tab order. One list: the strip uses them as content descriptions and
 *  the top bar shows the open one, so they can never drift apart. */
private val settingsTabTitles = listOf("الواجهة", "القائمة", "المعلومات", "التلاوة", "التطبيق")

/** Top category tabs that split the settings into short, focused sections.
 *
 *  Icons, not words: at four tabs across a phone, "المعلومات" wrapped onto a second line and left
 *  that tab visibly taller than its neighbours. The Arabic name stays as each tab's content
 *  description. */
@Composable
private fun SettingsTabs(tab: Int, onTab: (Int) -> Unit) {
    val icons = listOf(
        Icons.Outlined.Palette,
        Icons.Outlined.Menu,
        Icons.Outlined.Info,
        Icons.Outlined.Headphones,
        Icons.Outlined.PhoneAndroid,
    )
    MushafSegmentedTabs(
        labels = settingsTabTitles,
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
