package com.mushaf.reader.reader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.mushaf.reader.data.packs.InstalledPack
import com.mushaf.reader.data.packs.PackDownloadWorker
import com.mushaf.reader.data.packs.PackDownloads
import com.mushaf.reader.data.packs.PackInfo
import com.mushaf.reader.data.recitation.formatByteSize
import com.mushaf.reader.ui.components.MushafPanel
import com.mushaf.reader.ui.components.MushafSectionHeader
import com.mushaf.reader.ui.components.MushafTopBar

/**
 * The tafsir library: the books that can be downloaded and read offline in the explanation sheet's
 * sources menu. A dialog for the same reason as the sources screen: it opens from the sheet too.
 */
@Composable
internal fun TafsirLibraryDialog(downloads: PackDownloads, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        StatusBarIconsForBackground()
        TafsirLibraryScreen(downloads = downloads, onBack = onDismiss)
    }
}

/** A row of the library: a pack in the catalog, an installed one, or both. */
private data class LibraryEntry(val id: String, val info: PackInfo?, val installed: InstalledPack?) {
    val title: String get() = info?.title ?: installed?.title.orEmpty()
    val work: String get() = info?.work ?: installed?.work.orEmpty()
    val author: String get() = info?.author ?: installed?.author.orEmpty()
}

@Composable
private fun TafsirLibraryScreen(downloads: PackDownloads, onBack: () -> Unit) {
    LaunchedEffect(Unit) { downloads.loadCatalog() }
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var askMobileFor by remember { mutableStateOf<PackInfo?>(null) }
    var confirmDelete by remember { mutableStateOf<LibraryEntry?>(null) }

    fun start(info: PackInfo) {
        // The progress notification needs the permission on Android 13+; downloading does not.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (downloads.shouldAskAboutMobileData()) askMobileFor = info
        else downloads.download(info, overMobileData = false)
    }

    val catalog = downloads.catalog
    val installed = downloads.installed
    val entries = remember(catalog, installed) {
        val ids = (catalog?.packs?.map { it.id }.orEmpty() + installed.keys).distinct()
            .sortedBy { PACK_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        ids.map { LibraryEntry(it, catalog?.find(it), installed[it]) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            MushafTopBar(title = "مكتبة التفاسير", onBack = onBack)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        text = "كتب تُنزَّل مرة واحدة ثم تُقرأ دون اتصال من قائمة «المصادر» في نافذة التفسير. " +
                            "لا يُنزَّل شيء إلا حين تطلبه.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    entries.isEmpty() && downloads.catalogState == PackDownloads.CatalogState.LOADING -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    entries.isEmpty() -> item {
                        Text(
                            text = "تعذر جلب قائمة الكتب. تحقق من الاتصال ثم افتح المكتبة مرة أخرى.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    else -> {
                        if (downloads.catalogState == PackDownloads.CatalogState.OFFLINE) {
                            item {
                                Text(
                                    text = "لا اتصال الآن، فهذه القائمة من آخر مرة. يحتاج التنزيل إلى اتصال.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(entries, key = { it.id }) { entry ->
                            LibraryEntryPanel(
                                entry = entry,
                                progress = downloads.progress[entry.id],
                                waitsForWifi = downloads.waitsForWifi(),
                                onDownload = { entry.info?.let(::start) },
                                onCancel = { downloads.cancel(entry.id) },
                                onDelete = { confirmDelete = entry },
                            )
                        }
                    }
                }
                item {
                    Text(
                        text = "تُنزَّل الكتب من مستودع التطبيق على GitHub، ونصوصها من قاعدة بيانات " +
                            "مركز تفسير للدراسات القرآنية. لا يرسل التطبيق عنك شيئاً، لكن خادم التنزيل " +
                            "يرى عنوان اتصالك كما يراه أي موقع.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    askMobileFor?.let { info ->
        AlertDialog(
            onDismissRequest = { askMobileFor = null },
            title = { Text("التنزيل ببيانات الجوال؟") },
            text = {
                Text(
                    "أنت متصل ببيانات الجوال، وحجم ${info.title} ${formatByteSize(info.compressedBytes)}. " +
                        "يمكنك تنزيله الآن، أو تركه ينتظر شبكة واي فاي.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    downloads.download(info, overMobileData = true)
                    askMobileFor = null
                }) { Text("نزّل الآن") }
            },
            dismissButton = {
                TextButton(onClick = {
                    downloads.download(info, overMobileData = false)
                    askMobileFor = null
                }) { Text("انتظر الواي فاي") }
            },
        )
    }

    confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("حذف ${entry.title}؟") },
            text = { Text("سيُحذف من الجهاز، ويمكنك تنزيله مرة أخرى متى شئت.") },
            confirmButton = {
                TextButton(onClick = {
                    downloads.delete(entry.id)
                    confirmDelete = null
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun LibraryEntryPanel(
    entry: LibraryEntry,
    progress: PackDownloads.Progress?,
    waitsForWifi: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val info = entry.info
    val installed = entry.installed
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tooNew = info?.needsNewerApp(PackDownloads.appVersionCode) == true
    val updatable = installed != null && info != null && info.version > installed.version && !tooNew
    MushafPanel {
        MushafSectionHeader(
            title = entry.title,
            subtitle = listOf(entry.work, entry.author).filter { it.isNotBlank() && it != entry.title }
                .joinToString(" — ").ifBlank { null },
            icon = Icons.AutoMirrored.Outlined.MenuBook,
        )
        Spacer(Modifier.height(8.dp))
        val sizes = buildList {
            info?.let { add("التنزيل ${formatByteSize(it.compressedBytes)}") }
            add("على الجهاز ${formatByteSize(installed?.bytes ?: info?.installedBytes ?: 0)}")
        }.joinToString(" • ")
        Text(sizes, style = MaterialTheme.typography.bodySmall, color = muted)
        Spacer(Modifier.height(10.dp))

        when (progress?.status) {
            PackDownloads.Status.DOWNLOADING -> {
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${formatByteSize(progress.bytes)} من ${formatByteSize(progress.total)}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                    TextButton(onClick = onCancel) { Text("إلغاء") }
                }
            }
            PackDownloads.Status.VERIFYING -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("يتحقق من الملف ويجهّزه…", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            PackDownloads.Status.QUEUED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (waitsForWifi) "بانتظار شبكة واي فاي" else "في الانتظار…",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
                TextButton(onClick = onCancel) { Text("إلغاء") }
            }
            PackDownloads.Status.FAILED -> Column {
                Text(
                    text = when (progress.error) {
                        PackDownloadWorker.ERROR_SPACE -> "لا توجد مساحة كافية على الجهاز."
                        PackDownloadWorker.ERROR_CORRUPT -> "وصل الملف تالفاً، فلم يُحفظ."
                        else -> "تعذر التنزيل. تحقق من الاتصال ثم أعد المحاولة."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(6.dp))
                if (info != null) {
                    FilledTonalButton(onClick = onDownload) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("إعادة المحاولة")
                    }
                }
            }
            null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    tooNew && installed == null -> Text(
                        text = "يتطلب إصداراً أحدث من التطبيق.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                    installed == null -> FilledTonalButton(onClick = onDownload, enabled = info != null) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تنزيل")
                    }
                    else -> {
                        Text(
                            text = if (updatable) "تتوفر نسخة أحدث منه." else "منزّل على الجهاز.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                            fontWeight = if (updatable) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (updatable) {
                            FilledTonalButton(onClick = onDownload) { Text("تحديث") }
                        }
                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("حذف")
                        }
                    }
                }
            }
        }
    }
}
