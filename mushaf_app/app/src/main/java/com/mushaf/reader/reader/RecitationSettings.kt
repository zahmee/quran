package com.mushaf.reader.reader

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.mushaf.reader.data.normalizeArabic
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NetworkCell
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mushaf.reader.data.SurahAyahCounts
import com.mushaf.reader.data.recitation.formatByteSize
import com.mushaf.reader.recitation.MobileDataPolicy
import com.mushaf.reader.recitation.RecitationController
import com.mushaf.reader.recitation.RecitationDownloads
import com.mushaf.reader.recitation.RecitationStorage
import com.mushaf.reader.ui.components.MushafPanel
import com.mushaf.reader.ui.components.MushafSectionHeader
import com.mushaf.reader.ui.components.MushafSoftDivider

/**
 * The «التلاوة» settings tab: the reciter, how playback follows the page, downloads for listening
 * offline, what is on the device, and the mobile-data choice — everything recitation, in one place.
 * Emits its panels straight into the tab's scrolling column.
 */
@Composable
internal fun RecitationSettingsTab(
    recitation: RecitationController,
    downloads: RecitationDownloads,
    currentSurah: Int?,
    currentJuz: Int,
    juzSurahs: List<Int>,
    /** All surahs, for choosing which to download. */
    surahs: List<SurahEntry>,
    surahName: (Int) -> String,
    followPage: Boolean,
    onFollowPageChange: (Boolean) -> Unit,
    mobileDataPolicy: MobileDataPolicy,
    onMobileDataPolicyChange: (MobileDataPolicy) -> Unit,
    onPickReciter: () -> Unit,
) {
    LaunchedEffect(recitation) { recitation.prepareReciter() }
    DisposableEffect(downloads) {
        downloads.setVisible(true)
        onDispose { downloads.setVisible(false) }
    }
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // The progress notification needs the permission on Android 13+; downloading does not.
    fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val reciter = recitation.reciter
    fun start(surahs: List<Int>) {
        val r = reciter ?: return
        askForNotifications()
        downloads.download(r, surahs)
    }

    var confirmWhole by remember { mutableStateOf(false) }
    var pickSurahs by remember { mutableStateOf(false) }
    var confirmClearAuto by remember { mutableStateOf(false) }
    var confirmRemoveReciter by remember { mutableStateOf<Int?>(null) }
    val open = remember { mutableStateMapOf<Int, Boolean>() }
    val names = recitation.reciterNames
    val groups = downloads.items.groupBy { it.reciterId }

    // 1. The reciter.
    MushafPanel {
        MushafSectionHeader(
            title = reciter?.name ?: "لم يُختر قارئ بعد",
            subtitle = reciter?.rewaya?.takeIf { it.isNotBlank() } ?: "القارئ",
            icon = Icons.Outlined.RecordVoiceOver,
        )
        TextButton(onClick = onPickReciter) { Text("تغيير القارئ") }
    }

    // 2. Playback.
    MushafPanel {
        MushafSectionHeader(title = "التشغيل", icon = Icons.Outlined.Tune)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "قلب الصفحة مع التلاوة",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Hint("أوقفه لتتصفح صفحات أخرى والتلاوة مستمرة؛ تبقى الآية مظلّلة في صفحتها.")
            }
            Switch(checked = followPage, onCheckedChange = onFollowPageChange)
        }
    }

    // 3. Downloads.
    MushafPanel {
        MushafSectionHeader(
            title = "التنزيل للاستماع دون اتصال",
            subtitle = reciter?.let { "بصوت ${it.name}" },
            icon = Icons.Outlined.Download,
        )
        if (reciter != null) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (currentSurah != null) {
                    DownloadButton("تنزيل سورة ${surahName(currentSurah)}") { start(listOf(currentSurah)) }
                }
                if (juzSurahs.isNotEmpty()) {
                    DownloadButton("تنزيل الجزء ${currentJuz.toArabicDigits()}") { start(juzSurahs) }
                    Hint("يشمل السور التي فيه كاملة، فالتلاوة ملف لكل سورة.")
                }
                DownloadButton("تنزيل المصحف كاملاً") { confirmWhole = true }
                DownloadButton("اختيار سور للتنزيل", icon = Icons.AutoMirrored.Filled.PlaylistAddCheck) {
                    pickSurahs = true
                }
            }
        }
        if (downloads.waitingForNetwork) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    text = if (mobileDataPolicy == MobileDataPolicy.ALLOW) {
                        "التنزيلات تنتظر الاتصال بالإنترنت."
                    } else {
                        "التنزيلات تنتظر الاتصال بشبكة لاسلكية. يمكنك السماح ببيانات الجوال أدناه."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    // 4. What is on the device.
    MushafPanel {
        MushafSectionHeader(
            title = "على الجهاز: ${formatByteSize(downloads.cacheBytes)}",
            subtitle = "منها من الاستماع وحده: ${formatByteSize(downloads.autoBytes)}. " +
                "يُحتفظ بأحدث ما سُمع حتى ${formatByteSize(RecitationStorage.AUTO_LIMIT_BYTES)}.",
            icon = Icons.Outlined.SdStorage,
        )
        if (downloads.autoBytes > 0) {
            TextButton(onClick = { confirmClearAuto = true }) { Text("مسح ما حُفظ من الاستماع") }
        }
        if (groups.isEmpty()) {
            Hint("لا توجد تنزيلات بعد.")
        }
        groups.forEach { (reciterId, list) ->
            MushafSoftDivider(verticalPadding = 6.dp)
            val expanded = open[reciterId] == true
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open[reciterId] = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "إخفاء السور" else "عرض السور",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        text = names[reciterId] ?: "القارئ ${reciterId.toArabicDigits()}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    val done = list.count { it.done }
                    val progress = if (done == list.size) "اكتمل تنزيل ${surahsLabel(list.size)}"
                    else "اكتمل ${done.toArabicDigits()} من ${surahsLabel(list.size)}"
                    Text(
                        text = "$progress • ${formatByteSize(list.sumOf { it.bytes })}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { confirmRemoveReciter = reciterId }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف تنزيلات القارئ")
                }
            }
            if (expanded) {
                list.forEach { entry ->
                    DownloadRow(
                        entry = entry,
                        name = surahName(entry.surah),
                        onRemove = { downloads.remove(entry) },
                        onRetry = {
                            recitation.reciters.firstOrNull { it.id == entry.reciterId }
                                ?.let { downloads.download(it, listOf(entry.surah)) }
                        },
                    )
                }
            }
        }
    }

    // 5. Mobile data.
    MushafPanel {
        MushafSectionHeader(title = "بيانات الجوال", icon = Icons.Outlined.NetworkCell)
        MobileDataChoiceRow(selected = mobileDataPolicy, onSelected = onMobileDataPolicyChange)
    }

    if (pickSurahs && reciter != null) {
        val mine = downloads.items.filter { it.reciterId == reciter.id }.associateBy { it.surah }
        SurahDownloadPicker(
            reciterName = reciter.name,
            surahs = surahs,
            status = mine,
            onDownload = { chosen ->
                pickSurahs = false
                start(chosen)
            },
            onDismiss = { pickSurahs = false },
        )
    }

    if (confirmWhole && reciter != null) {
        AlertDialog(
            onDismissRequest = { confirmWhole = false },
            title = { Text("تنزيل المصحف كاملاً") },
            text = {
                Text(
                    "ستُنزَّل ${SurahAyahCounts.SURAH_COUNT.toArabicDigits()} سورة بصوت ${reciter.name}. " +
                        "قد يتجاوز الحجم غيغابايتاً واحداً، فيُفضّل التنزيل عبر شبكة لاسلكية."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWhole = false
                    start((1..SurahAyahCounts.SURAH_COUNT).toList())
                }) { Text("تنزيل") }
            },
            dismissButton = { TextButton(onClick = { confirmWhole = false }) { Text("إلغاء") } },
        )
    }

    if (confirmClearAuto) {
        AlertDialog(
            onDismissRequest = { confirmClearAuto = false },
            title = { Text("مسح ما حُفظ من الاستماع") },
            text = { Text("يُحذف الصوت المحفوظ من الاستماع فقط، وتبقى التنزيلات. يُعاد تحميله عند الاستماع مرة أخرى.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAuto = false
                    downloads.clearAutoCache()
                }) { Text("مسح") }
            },
            dismissButton = { TextButton(onClick = { confirmClearAuto = false }) { Text("إلغاء") } },
        )
    }

    confirmRemoveReciter?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmRemoveReciter = null },
            title = { Text("حذف التنزيلات") },
            text = { Text("تُحذف كل السور المنزّلة بصوت ${names[id] ?: "هذا القارئ"} من الجهاز.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveReciter = null
                    downloads.removeReciter(id)
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveReciter = null }) { Text("إلغاء") } },
        )
    }
}

/** Whether recitation audio may use mobile data. */
@Composable
private fun MobileDataChoiceRow(
    selected: MobileDataPolicy,
    onSelected: (MobileDataPolicy) -> Unit,
) {
    val choices = listOf(
        MobileDataPolicy.ASK to "اسأل أول مرة",
        MobileDataPolicy.ALLOW to "مسموح",
        MobileDataPolicy.WIFI_ONLY to "الشبكة اللاسلكية فقط",
    )
    var expanded by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Hint(
            "التلاوة نحو ميغابايت في الدقيقة. ما سُمع أو نُزِّل لا يُحمَّل مرة أخرى.",
            modifier = Modifier.weight(1f),
        )
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                modifier = Modifier.clickable { expanded = true },
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = choices.first { it.first == selected }.second,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "اختيار", tint = accent)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { (policy, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = if (policy == selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelected(policy)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadButton(
    text: String,
    icon: ImageVector = Icons.Filled.Download,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun DownloadRow(
    entry: RecitationDownloads.Item,
    name: String,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (entry.done) Icons.Filled.DownloadDone else Icons.Filled.Download,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (entry.done) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(text = "سورة $name", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = statusText(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (entry.failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.failed) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = "إعادة التنزيل")
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف السورة")
            }
        }
        if (entry.status == RecitationDownloads.Status.DOWNLOADING && entry.percent >= 0f) {
            LinearProgressIndicator(
                progress = { entry.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 8.dp, bottom = 4.dp),
            )
        }
    }
}

private fun statusText(entry: RecitationDownloads.Item): String = when (entry.status) {
    RecitationDownloads.Status.DONE -> "منزّلة • ${formatByteSize(entry.bytes)}"
    RecitationDownloads.Status.DOWNLOADING ->
        if (entry.percent >= 0f) "جارٍ التنزيل ${Math.round(entry.percent).toArabicDigits()}٪"
        else "جارٍ التنزيل"
    RecitationDownloads.Status.QUEUED -> "في الانتظار"
    RecitationDownloads.Status.RESTARTING -> "يُعاد التنزيل"
    RecitationDownloads.Status.STOPPED -> "متوقفة"
    RecitationDownloads.Status.REMOVING -> "تُحذف"
    RecitationDownloads.Status.FAILED -> "تعذّر التنزيل"
}

/** Arabic counted noun for surahs, after "من" or "تنزيل" (genitive: سورتين). */
private fun surahsLabel(n: Int): String = when {
    n == 1 -> "سورة واحدة"
    n == 2 -> "سورتين"
    n in 3..10 -> "${n.toArabicDigits()} سور"
    else -> "${n.toArabicDigits()} سورة"
}

/**
 * Choosing surahs to download for the current reciter: all 114 with a checkbox, searchable by
 * name or number, with select-all and clear for what the search shows. Surahs already on the
 * device, or on their way, are marked instead of offered again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurahDownloadPicker(
    reciterName: String,
    surahs: List<SurahEntry>,
    /** This reciter's downloads by surah. */
    status: Map<Int, RecitationDownloads.Item>,
    onDownload: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<Int>() }
    fun available(number: Int) = status[number]?.let { !it.done && !it.pending } ?: true
    val shown = remember(surahs, query) {
        val q = normalizeArabic(query.trim())
        val number = query.trim().toIntOrNull() ?: query.trim().fromArabicDigits()
        when {
            q.isEmpty() -> surahs
            number != null -> surahs.filter { it.number == number }
            else -> surahs.filter { normalizeArabic(it.nameAr).contains(q) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "اختيار سور للتنزيل",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Hint("بصوت $reciterName. ما نُزِّل من قبل مُعلَّم ولا يُنزَّل مرة أخرى.")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("ابحث باسم السورة أو رقمها") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    shown.forEach { if (available(it.number) && it.number !in chosen) chosen += it.number }
                }) { Text(if (query.isBlank()) "تحديد الكل" else "تحديد النتائج") }
                TextButton(
                    onClick = { chosen.clear() },
                    enabled = chosen.isNotEmpty(),
                ) { Text("إلغاء التحديد") }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(shown, key = { it.number }) { surah ->
                val entry = status[surah.number]
                val free = available(surah.number)
                val checked = surah.number in chosen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = free) {
                            if (checked) chosen -= surah.number else chosen += surah.number
                        }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (free) {
                        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    } else {
                        Icon(
                            imageVector = if (entry?.done == true) Icons.Filled.DownloadDone else Icons.Filled.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp).size(24.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${surah.number.toArabicDigits()}. سورة ${surah.nameAr}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = when {
                                entry?.done == true -> "منزّلة • ${formatByteSize(entry.bytes)}"
                                entry?.pending == true -> statusText(entry)
                                else -> "${surah.ayahCount.toArabicDigits()} آية"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Button(
            onClick = { onDownload(chosen.sorted()) },
            enabled = chosen.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = if (chosen.isEmpty()) "اختر سوراً للتنزيل" else "تنزيل ${surahsLabel(chosen.size)}",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** "١١٤" -> 114, for searching by a number typed with Arabic-Indic digits. */
private fun String.fromArabicDigits(): Int? {
    if (isEmpty() || any { it !in '\u0660'..'\u0669' }) return null
    return map { it - '\u0660' }.fold(0) { acc, d -> acc * 10 + d }
}
