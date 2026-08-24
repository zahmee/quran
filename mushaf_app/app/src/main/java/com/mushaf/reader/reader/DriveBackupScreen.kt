package com.mushaf.reader.reader

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.mushaf.reader.data.backup.DriveBackupStage
import com.mushaf.reader.data.backup.DriveBackupUiState
import com.mushaf.reader.data.backup.RemoteBackupInfo
import com.mushaf.reader.ui.components.MushafIconBadge
import com.mushaf.reader.ui.components.MushafPanel
import com.mushaf.reader.ui.components.MushafSoftDivider
import com.mushaf.reader.ui.components.MushafTopBar
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

private enum class DriveScreenAction { Refresh, Backup, Restore }

@Composable
fun DriveBackupScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.driveBackupUiState
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val authorizationClient = remember(activity) {
        activity?.let(Identity::getAuthorizationClient)
    }
    var pendingAction by remember { mutableStateOf<DriveScreenAction?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val action = pendingAction
        pendingAction = null
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null || action == null) {
            viewModel.driveAuthorizationFailed("أُلغي الاتصال بحساب Google قبل إكمال العملية.")
            return@rememberLauncherForActivityResult
        }
        val client = authorizationClient
        if (client == null) {
            viewModel.driveAuthorizationFailed("خدمات Google غير متاحة على هذا الجهاز.")
            return@rememberLauncherForActivityResult
        }
        try {
            dispatchAuthorizedAction(
                client.getAuthorizationResultFromIntent(data),
                action,
                viewModel,
            )
        } catch (_: Exception) {
            viewModel.driveAuthorizationFailed("تعذر إكمال تفويض Google Drive. حاول مرة أخرى.")
        }
    }

    val authorize: (DriveScreenAction, Boolean) -> Unit = { action, chooseAccount ->
        val client = authorizationClient
        if (activity == null || client == null) {
            viewModel.driveAuthorizationFailed("يتطلب النسخ الاحتياطي جهازاً يدعم خدمات Google Play.")
        } else {
            viewModel.beginDriveAuthorization()
            pendingAction = action
            val requestBuilder = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            if (chooseAccount) {
                requestBuilder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            } else {
                state.accountEmail?.let { requestBuilder.setAccount(Account(it, "com.google")) }
            }
            client.authorize(requestBuilder.build())
                .addOnSuccessListener { authorization ->
                    if (authorization.hasResolution()) {
                        val pendingIntent = authorization.pendingIntent
                        if (pendingIntent == null) {
                            pendingAction = null
                            viewModel.driveAuthorizationFailed("تعذر فتح نافذة اختيار حساب Google.")
                        } else {
                            authorizationLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                            )
                        }
                    } else {
                        pendingAction = null
                        dispatchAuthorizedAction(authorization, action, viewModel)
                    }
                }
                .addOnFailureListener {
                    pendingAction = null
                    viewModel.driveAuthorizationFailed(
                        "تعذر الاتصال بخدمات Google. تحقق من تحديث Google Play ثم أعد المحاولة."
                    )
                }
        }
    }

    val connected = state.accountEmail != null || state.remoteChecked

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            MushafTopBar(
                title = "النسخ الاحتياطي",
                subtitle = "مساحتك الخاصة في Google Drive",
                onBack = onBack,
                actions = {
                    if (connected) {
                        IconButton(
                            enabled = !state.busy,
                            onClick = { authorize(DriveScreenAction.Refresh, false) },
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "تحديث حالة النسخة")
                        }
                    }
                },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { BackupHero(state = state, connected = connected) }

                item {
                    AccountPanel(
                        state = state,
                        connected = connected,
                        onConnect = { authorize(DriveScreenAction.Refresh, true) },
                        onChangeAccount = { authorize(DriveScreenAction.Refresh, true) },
                        onForgetAccount = viewModel::forgetDriveAccount,
                    )
                }

                item {
                    LatestBackupPanel(
                        state = state,
                        onRefresh = { authorize(DriveScreenAction.Refresh, false) },
                    )
                }

                item {
                    BackupActions(
                        state = state,
                        connected = connected,
                        onBackup = { authorize(DriveScreenAction.Backup, false) },
                        onRestore = { confirmRestore = true },
                    )
                }

                item {
                    AnimatedVisibility(visible = state.message != null || state.error != null) {
                        FeedbackPanel(
                            message = state.message,
                            error = state.error,
                            onDismiss = viewModel::clearDriveBackupFeedback,
                        )
                    }
                }

                item { PrivacyPanel() }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) confirmRestore = false },
            icon = { Icon(Icons.Filled.Restore, contentDescription = null) },
            title = { Text("استعادة أحدث نسخة؟") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ستُستبدل محفوظات القراءة والإشارات والإعدادات الحالية بالنسخة الموجودة على Google Drive.")
                    state.latestBackup?.let {
                        Text(
                            text = "النسخة: ${formatDate(it.modifiedAt)} — ${it.deviceName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "يُتحقق من سلامة الملف بالكامل قبل تغيير أي بيانات محلية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !state.busy,
                    onClick = {
                        confirmRestore = false
                        authorize(DriveScreenAction.Restore, false)
                    },
                ) { Text("استعادة") }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.busy,
                    onClick = { confirmRestore = false },
                ) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun BackupHero(state: DriveBackupUiState, connected: Boolean) {
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
                    imageVector = if (connected) Icons.Outlined.CloudDone else Icons.Filled.Cloud,
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
                    text = "نسخة صغيرة خاصة بالتطبيق؛ لا تُرفع صفحات المصحف أو ملفاته.",
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
                            connected -> "الحساب متصل"
                            else -> "بانتظار ربط Google Drive"
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
private fun AccountPanel(
    state: DriveBackupUiState,
    connected: Boolean,
    onConnect: () -> Unit,
    onChangeAccount: () -> Unit,
    onForgetAccount: () -> Unit,
) {
    MushafPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MushafIconBadge(Icons.Outlined.AccountCircle)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("حساب Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = state.accountEmail ?: if (connected) "حساب Google المتصل" else "لم يتم ربط حساب بعد",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MushafSoftDivider(verticalPadding = 12.dp)
        if (!connected) {
            Button(
                enabled = !state.busy,
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("ربط Google Drive")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    enabled = !state.busy,
                    onClick = onChangeAccount,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("تغيير الحساب") }
                TextButton(
                    enabled = !state.busy,
                    onClick = onForgetAccount,
                    modifier = Modifier.weight(1f),
                ) { Text("نسيان الحساب") }
            }
        }
    }
}

@Composable
private fun LatestBackupPanel(state: DriveBackupUiState, onRefresh: () -> Unit) {
    MushafPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MushafIconBadge(Icons.Outlined.CloudDone)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("أحدث نسخة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when {
                        state.latestBackup != null -> formatDate(state.latestBackup.modifiedAt)
                        state.remoteChecked -> "لا توجد نسخة محفوظة"
                        else -> "افحص Google Drive لمعرفة آخر نسخة"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!state.busy && state.accountEmail != null) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "فحص أحدث نسخة")
                }
            }
        }

        state.latestBackup?.let { latest ->
            MushafSoftDivider(verticalPadding = 12.dp)
            val facts = listOf(
                Icons.Outlined.Devices to latest.deviceName,
                Icons.Outlined.Info to "الإصدار ${latest.appVersion}",
                Icons.Outlined.Security to formatSize(latest.sizeBytes),
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
                        Text(text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupActions(
    state: DriveBackupUiState,
    connected: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    MushafPanel {
        Text("إجراءات النسخة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = "أنشئ نسخة قبل الانتقال إلى جهاز آخر، ثم استعدها من الحساب نفسه.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )

        Button(
            enabled = connected && !state.busy,
            onClick = onBackup,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("إنشاء نسخة الآن", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(9.dp))
        OutlinedButton(
            enabled = state.latestBackup != null && !state.busy,
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("استعادة أحدث نسخة", fontWeight = FontWeight.SemiBold)
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
                    text = "يصل التطبيق فقط إلى مجلده المخفي في Google Drive. تشمل النسخة موضع القراءة والإشارات والتقدم والجلسات والختمات والإعدادات، ولا تشمل صور المصحف أو قاعدة المحتوى.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun dispatchAuthorizedAction(
    authorization: AuthorizationResult,
    action: DriveScreenAction,
    viewModel: ReaderViewModel,
) {
    val token = authorization.accessToken
    if (token.isNullOrBlank()) {
        viewModel.driveAuthorizationFailed("لم يمنح Google رمز وصول صالحاً إلى مجلد التطبيق.")
        return
    }
    val accountEmail = authorization.toGoogleSignInAccount()?.account?.name
    when (action) {
        DriveScreenAction.Refresh -> viewModel.refreshDriveBackup(token, accountEmail)
        DriveScreenAction.Backup -> viewModel.createDriveBackup(token, accountEmail)
        DriveScreenAction.Restore -> viewModel.restoreLatestDriveBackup(token, accountEmail)
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun stageLabel(stage: DriveBackupStage?): String = when (stage) {
    DriveBackupStage.Authorizing -> "جارٍ الاتصال بحساب Google…"
    DriveBackupStage.Loading -> "جارٍ فحص أحدث نسخة…"
    DriveBackupStage.Uploading -> "جارٍ ضغط البيانات ورفعها…"
    DriveBackupStage.Restoring -> "جارٍ التحقق من النسخة واستعادتها…"
    null -> "جاهز"
}

private fun formatDate(time: Long): String {
    if (time <= 0L) return "وقت غير معروف"
    return DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
        Locale.forLanguageTag("ar"),
    ).format(Date(time))
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes بايت"
    bytes < 1024L * 1024L -> String.format(Locale.forLanguageTag("ar"), "%.1f كيلوبايت", bytes / 1024.0)
    else -> String.format(Locale.forLanguageTag("ar"), "%.1f ميجابايت", bytes / (1024.0 * 1024.0))
}
