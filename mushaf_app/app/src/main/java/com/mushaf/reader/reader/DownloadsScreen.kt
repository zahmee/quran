package com.mushaf.reader.reader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.mushaf.reader.data.recitation.Reciter
import com.mushaf.reader.data.recitation.formatByteSize
import com.mushaf.reader.recitation.MobileDataPolicy
import com.mushaf.reader.recitation.RecitationDownloads
import com.mushaf.reader.recitation.RecitationStorage
import com.mushaf.reader.ui.components.MushafPanel
import com.mushaf.reader.ui.components.MushafSectionHeader
import com.mushaf.reader.ui.components.MushafSoftDivider
import com.mushaf.reader.ui.components.MushafTopBar

/**
 * Recitation on the device: download the current surah, the current juz or the whole mushaf for
 * the chosen reciter; see and delete what is downloaded, per reciter and surah; and clear the
 * audio that listening kept.
 */
@Composable
internal fun DownloadsScreen(
    downloads: RecitationDownloads,
    reciter: Reciter?,
    reciters: List<Reciter>,
    /** Names of all recitations, hidden ones too, for downloads made before they were hidden. */
    reciterNames: Map<Int, String>,
    currentSurah: Int?,
    currentJuz: Int,
    juzSurahs: List<Int>,
    surahName: (Int) -> String,
    mobileDataPolicy: MobileDataPolicy,
    onPickReciter: () -> Unit,
    onBack: () -> Unit,
) {
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
    fun start(surahs: List<Int>) {
        val r = reciter ?: return
        askForNotifications()
        downloads.download(r, surahs)
    }

    var confirmWhole by remember { mutableStateOf(false) }
    var confirmClearAuto by remember { mutableStateOf(false) }
    var confirmRemoveReciter by remember { mutableStateOf<Int?>(null) }
    val names = reciterNames
    val groups = downloads.items.groupBy { it.reciterId }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            MushafTopBar(title = "تنزيل التلاوة", onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MushafPanel {
                        MushafSectionHeader(
                            title = reciter?.name ?: "لم يُختر قارئ بعد",
                            subtitle = reciter?.rewaya?.takeIf { it.isNotBlank() },
                            icon = Icons.Outlined.RecordVoiceOver,
                        )
                        TextButton(onClick = onPickReciter) { Text("تغيير القارئ") }
                        if (reciter != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (currentSurah != null) {
                                    DownloadButton("تنزيل سورة ${surahName(currentSurah)}") {
                                        start(listOf(currentSurah))
                                    }
                                }
                                if (juzSurahs.isNotEmpty()) {
                                    DownloadButton("تنزيل الجزء ${currentJuz.toArabicDigits()}") {
                                        start(juzSurahs)
                                    }
                                    Hint("يشمل السور التي فيه كاملة، فالتلاوة ملف لكل سورة.")
                                }
                                DownloadButton("تنزيل المصحف كاملاً") { confirmWhole = true }
                            }
                        }
                        if (downloads.waitingForNetwork) {
                            Row(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = if (mobileDataPolicy == MobileDataPolicy.ALLOW) {
                                        "التنزيلات تنتظر الاتصال بالإنترنت."
                                    } else {
                                        "التنزيلات تنتظر الاتصال بشبكة لاسلكية. يمكنك السماح ببيانات الجوال من الإعدادات."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                item {
                    MushafPanel {
                        MushafSectionHeader(
                            title = "على الجهاز: ${formatByteSize(downloads.cacheBytes)}",
                            subtitle = "منها من الاستماع وحده: ${formatByteSize(downloads.autoBytes)}. " +
                                "يُحتفظ بأحدث ما سُمع حتى ${formatByteSize(RecitationStorage.AUTO_LIMIT_BYTES)}.",
                            icon = Icons.Outlined.SdStorage,
                        )
                        if (downloads.autoBytes > 0) {
                            TextButton(onClick = { confirmClearAuto = true }) {
                                Text("مسح ما حُفظ من الاستماع")
                            }
                        }
                    }
                }

                if (groups.isEmpty()) {
                    item { Hint("لا توجد تنزيلات بعد.") }
                }

                groups.forEach { (reciterId, list) ->
                    item(key = "r$reciterId") {
                        MushafPanel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = names[reciterId] ?: "القارئ ${reciterId.toArabicDigits()}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    val done = list.filter { it.done }
                                    val progress = if (done.size == list.size) {
                                        "اكتمل تنزيل ${surahsLabel(list.size)}"
                                    } else {
                                        "اكتمل ${done.size.toArabicDigits()} من ${surahsLabel(list.size)}"
                                    }
                                    Text(
                                        text = "$progress • " + formatByteSize(list.sumOf { it.bytes }),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { confirmRemoveReciter = reciterId }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف تنزيلات القارئ")
                                }
                            }
                            MushafSoftDivider(verticalPadding = 6.dp)
                            list.forEach { entry ->
                                DownloadRow(
                                    entry = entry,
                                    name = surahName(entry.surah),
                                    onRemove = { downloads.remove(entry) },
                                    onRetry = {
                                        reciters.firstOrNull { it.id == entry.reciterId }
                                            ?.let { downloads.download(it, listOf(entry.surah)) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
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

@Composable
private fun DownloadButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** Arabic counted noun for surahs, after "من" (genitive: سورتين). */
private fun surahsLabel(n: Int): String = when {
    n == 1 -> "سورة واحدة"
    n == 2 -> "سورتين"
    n in 3..10 -> "${n.toArabicDigits()} سور"
    else -> "${n.toArabicDigits()} سورة"
}
