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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.backup.BackupStage
import com.mushaf.reader.data.backup.BackupUiState
import com.mushaf.reader.data.backup.CloudUiState
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
    val cloud = viewModel.cloudUiState
    // Providers report backup files under inconsistent MIME types, so filtering by type would hide
    // the very file the user is looking for on some clouds.
    val openableTypes = remember { arrayOf("*/*") }
    var pendingRestore by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingCloudRestore by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDeleteAccount by rememberSaveable { mutableStateOf(false) }

    // Another device may have uploaded since this screen was last open.
    LaunchedEffect(cloud.username) { viewModel.refreshCloudBackups() }

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
                subtitle = if (cloud.available) "في حسابك السحابي أو في ملف تختاره"
                else "ملف واحد تحفظه أينما شئت",
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { BackupHero(state = state, cloud = cloud) }

                if (cloud.available) {
                    item {
                        CloudBackupSection(
                            state = cloud,
                            onSignIn = viewModel::cloudSignIn,
                            onSignUp = viewModel::cloudSignUp,
                            onBackupNow = viewModel::cloudBackupNow,
                            onAutoBackupChange = viewModel::setCloudAutoBackup,
                            onRefresh = viewModel::refreshCloudBackups,
                            onRestore = { pendingCloudRestore = it.id },
                            onSignOut = viewModel::cloudSignOut,
                            onDeleteAccount = { confirmDeleteAccount = true },
                        )
                    }
                    item {
                        AnimatedVisibility(visible = cloud.message != null || cloud.error != null) {
                            FeedbackPanel(
                                message = cloud.message,
                                error = cloud.error,
                                onDismiss = viewModel::clearCloudFeedback,
                            )
                        }
                    }
                }

                item { LastBackupPanel(state = state) }

                item {
                    BackupActions(
                        state = state.copy(busy = state.busy || cloud.busy),
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

                item { PrivacyPanel(cloudAvailable = cloud.available) }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    pendingRestore?.let { uri ->
        RestoreConfirmDialog(
            source = null,
            takeDisplayByDefault = true,
            busy = state.busy,
            onConfirm = { takeDisplaySettings ->
                pendingRestore = null
                viewModel.importBackup(uri, takeDisplaySettings)
            },
            onDismiss = {
                pendingRestore = null
                viewModel.backupSelectionCancelled()
            },
        )
    }

    pendingCloudRestore?.let { id ->
        val backup = cloud.backups.firstOrNull { it.id == id }
        if (backup == null) {
            pendingCloudRestore = null
        } else {
            RestoreConfirmDialog(
                source = "نسخة ${backup.deviceName} — ${formatDate(backup.createdAt)}",
                // The same device's look is what the reader had; another device's rarely fits.
                takeDisplayByDefault = backup.fromThisDevice,
                busy = cloud.busy,
                onConfirm = { takeDisplaySettings ->
                    pendingCloudRestore = null
                    viewModel.cloudRestore(backup, takeDisplaySettings)
                },
                onDismiss = { pendingCloudRestore = null },
            )
        }
    }

    if (confirmDeleteAccount) {
        DeleteCloudAccountDialog(
            busy = cloud.busy,
            onConfirm = { password ->
                confirmDeleteAccount = false
                viewModel.cloudDeleteAccount(password)
            },
            onDismiss = { confirmDeleteAccount = false },
        )
    }
}

/**
 * Asks before folding a backup into this device. Display settings are a separate choice because
 * a tablet and a phone rarely want the same layout; history is never at stake, since a restore
 * only ever adds to it.
 */
@Composable
internal fun RestoreConfirmDialog(
    source: String?,
    takeDisplayByDefault: Boolean,
    busy: Boolean,
    onConfirm: (takeDisplaySettings: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var takeDisplay by rememberSaveable { mutableStateOf(takeDisplayByDefault) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Filled.Restore, contentDescription = null) },
        title = { Text("استعادة هذه النسخة؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (source != null) {
                    Text(source, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "تُضاف جلسات القراءة والختمات إلى ما على هذا الجهاز دون حذف شيء، " +
                        "وتنتقل إلى موضع القراءة والعلامات المحفوظة في النسخة."
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .toggleable(value = takeDisplay, role = Role.Checkbox, onValueChange = { takeDisplay = it })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = takeDisplay, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("استعادة إعدادات العرض أيضاً", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "الألوان وأزرار الشريط وطريقة عرض الصفحة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "يُتحقق من النسخة بالكامل قبل تغيير أي بيانات محلية، وإذا فشلت العملية " +
                        "تُعاد بياناتك السابقة كما كانت.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = { onConfirm(takeDisplay) }) { Text("استعادة") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

@Composable
private fun BackupHero(state: BackupUiState, cloud: CloudUiState) {
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
                    text = if (cloud.available) {
                        "في حسابك السحابي لتنتقل بين جوالك وجهازك اللوحي، أو في ملف تختار مكانه؛ " +
                            "لا تُرفع صفحات المصحف أو ملفاته."
                    } else {
                        "ملف صغير تحفظه في جهازك أو في Google Drive أو أي مكان تختاره؛ " +
                            "لا تُرفع صفحات المصحف أو ملفاته."
                    },
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
                            cloud.username != null && cloud.lastUploadAt > 0L ->
                                "آخر نسخة سحابية: ${formatDate(cloud.lastUploadAt)}"
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
        Text("النسخ في ملف", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
internal fun FeedbackPanel(message: String?, error: String?, onDismiss: () -> Unit) {
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
private fun PrivacyPanel(cloudAvailable: Boolean) {
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
                    text = if (cloudAvailable) {
                        "لا يتصل التطبيق بالإنترنت إلا للنسخ السحابي، وبعد أن تنشئ حساباً أو تسجّل الدخول " +
                            "فقط. لا نطلب بريداً ولا رقم جوال؛ يُحفظ في حسابك الاسم الذي اخترته ونسخك، " +
                            "ويمكنك حذفها كلها من هنا. "
                    } else {
                        "التطبيق لا يتصل بأي خادم. "
                    } + "الملف يبقى ملكك وحدك في الموقع الذي تختاره. تشمل النسخة موضع القراءة " +
                        "والإشارات والتقدم والجلسات والختمات والإعدادات، ولا تشمل صور المصحف أو قاعدة المحتوى.",
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

internal fun formatDate(time: Long): String {
    if (time <= 0L) return "غير معروف"
    return SimpleDateFormat("yyyy/MM/dd — HH:mm", Locale.forLanguageTag("ar")).format(Date(time))
}

internal fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "حجم غير معروف"
    bytes < 1024 -> "$bytes بايت"
    bytes < 1024 * 1024 -> "${"%.1f".format(Locale.US, bytes / 1024.0)} كيلوبايت"
    else -> "${"%.2f".format(Locale.US, bytes / (1024.0 * 1024.0))} ميغابايت"
}
