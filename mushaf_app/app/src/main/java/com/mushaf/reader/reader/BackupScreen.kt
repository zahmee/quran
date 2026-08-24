package com.mushaf.reader.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.backup.BackupStage
import com.mushaf.reader.data.backup.BackupUiState
import com.mushaf.reader.ui.components.MushafIconBadge
import com.mushaf.reader.ui.components.MushafPanel
import com.mushaf.reader.ui.components.MushafSoftDivider
import com.mushaf.reader.ui.components.MushafTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Written as gzip so the picker keeps the ".json.gz" name instead of appending its own suffix. */
private const val BACKUP_MIME_TYPE = "application/gzip"

@Composable
fun BackupScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.backupUiState
    // Providers report backup files under inconsistent MIME types, so filtering by type would hide
    // the very file the user is looking for on some clouds.
    val openableTypes = remember { arrayOf("*/*") }
    var pendingRestore by rememberSaveable { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
    ) { uri ->
        if (uri == null) viewModel.backupSelectionCancelled() else viewModel.exportBackup(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) viewModel.backupSelectionCancelled() else pendingRestore = uri
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            MushafTopBar(
                title = "النسخ الاحتياطي",
                subtitle = "ملف واحد تحفظه أينما شئت",
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { BackupHero(state = state) }

                item { LastBackupPanel(state = state) }

                item {
                    BackupActions(
                        state = state,
                        onExport = {
                            exportLauncher.launch(viewModel.suggestedBackupFileName())
                        },
                        onImport = { importLauncher.launch(openableTypes) },
                    )
                }

                item {
                    AnimatedVisibility(visible = state.message != null || state.error != null) {
                        FeedbackPanel(
                            message = state.message,
                            error = state.error,
                            onDismiss = viewModel::clearBackupFeedback,
                        )
                    }
                }

                item { PrivacyPanel() }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) pendingRestore = null },
            icon = { Icon(Icons.Filled.Restore, contentDescription = null) },
            title = { Text("استعادة هذه النسخة؟") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ستُستبدل محفوظات القراءة والإشارات والإعدادات الحالية ببيانات الملف المحدد.")
                    Text(
                        text = "يُتحقق من الملف بالكامل قبل تغيير أي بيانات محلية، وإذا فشلت العملية " +
                            "تُعاد بياناتك السابقة كما كانت.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !state.busy,
                    onClick = {
                        pendingRestore = null
                        viewModel.importBackup(uri)
                    },
                ) { Text("استعادة") }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.busy,
                    onClick = {
                        pendingRestore = null
                        viewModel.backupSelectionCancelled()
                    },
                ) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun BackupHero(state: BackupUiState) {
    val colors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors))
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.SaveAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "محفوظاتك، معك أينما قرأت",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "ملف صغير تحفظه في جهازك أو في Google Drive أو أي مكان تختاره؛ " +
                        "لا تُرفع صفحات المصحف أو ملفاته.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Text(
                        text = when {
                            state.busy -> stageLabel(state.stage)
                            state.lastBackup != null -> "آخر نسخة: ${formatDate(state.lastBackup.savedAt)}"
                            else -> "لم تُحفظ نسخة بعد"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LastBackupPanel(state: BackupUiState) {
    MushafPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MushafIconBadge(Icons.Outlined.Devices)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("آخر نسخة حفظتها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = state.lastBackup?.let { formatDate(it.savedAt) }
                        ?: "لم تُحفظ نسخة من هذا الجهاز بعد",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.lastBackup?.let { last ->
            MushafSoftDivider(verticalPadding = 12.dp)
            val facts = listOf(
                Icons.Outlined.FolderOpen to last.fileName,
                Icons.Outlined.Info to formatSize(last.sizeBytes),
            )
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                facts.forEach { (icon, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupActions(
    state: BackupUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    MushafPanel {
        Text("إجراءات النسخة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = "احفظ نسخة قبل الانتقال إلى جهاز آخر، ثم افتح الملف نفسه هناك لاستعادتها.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )

        Button(
            enabled = !state.busy,
            onClick = onExport,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("حفظ نسخة إلى ملف", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(9.dp))
        OutlinedButton(
            enabled = !state.busy,
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("استعادة من ملف", fontWeight = FontWeight.SemiBold)
        }

        if (state.busy) {
            Spacer(Modifier.height(15.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    stageLabel(state.stage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FeedbackPanel(message: String?, error: String?, onDismiss: () -> Unit) {
    val isError = error != null
    val container = if (isError) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.primaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
            )
            Text(
                text = error ?: message.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "إغلاق الرسالة", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PrivacyPanel() {
    MushafPanel(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MushafIconBadge(Icons.Outlined.Security)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("خصوصية واضحة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "التطبيق لا يطلب صلاحية الإنترنت ولا يتصل بأي خادم. الملف يبقى ملكك وحدك " +
                        "في الموقع الذي تختاره. تشمل النسخة موضع القراءة والإشارات والتقدم والجلسات " +
                        "والختمات والإعدادات، ولا تشمل صور المصحف أو قاعدة المحتوى.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun stageLabel(stage: BackupStage?): String = when (stage) {
    BackupStage.Exporting -> "جارٍ ضغط البيانات وحفظها…"
    BackupStage.Importing -> "جارٍ التحقق من الملف واستعادته…"
    null -> "جاهز"
}

private fun formatDate(time: Long): String {
    if (time <= 0L) return "غير معروف"
    return SimpleDateFormat("yyyy/MM/dd — HH:mm", Locale("ar")).format(Date(time))
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "حجم غير معروف"
    bytes < 1024 -> "$bytes بايت"
    bytes < 1024 * 1024 -> "${"%.1f".format(Locale.US, bytes / 1024.0)} كيلوبايت"
    else -> "${"%.2f".format(Locale.US, bytes / (1024.0 * 1024.0))} ميغابايت"
}
