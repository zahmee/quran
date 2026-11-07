package com.mushaf.reader.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mushaf.reader.data.backup.CloudBackupInfo
import com.mushaf.reader.data.backup.CloudBackupRetention
import com.mushaf.reader.data.backup.CloudStage
import com.mushaf.reader.data.backup.CloudUiState
import com.mushaf.reader.data.backup.CloudUsername
import com.mushaf.reader.ui.components.MushafIconBadge
import com.mushaf.reader.ui.components.MushafPanel
import com.mushaf.reader.ui.components.MushafSoftDivider

/** The cloud half of the backup screen: a sign-in form, or the account and its backups. */
@Composable
internal fun CloudBackupSection(
    state: CloudUiState,
    onSignIn: (name: String, password: String) -> Unit,
    onSignUp: (name: String, password: String) -> Unit,
    onBackupNow: () -> Unit,
    onAutoBackupChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRestore: (CloudBackupInfo) -> Unit,
    onDeleteBackup: (CloudBackupInfo) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    MushafPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MushafIconBadge(Icons.Outlined.Cloud)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("النسخ السحابي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = state.username?.let { "مسجّل باسم «$it»" }
                        ?: "انتقل بين جوالك وجهازك اللوحي باسم وكلمة مرور.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.username != null) {
                TextButton(enabled = !state.busy, onClick = onSignOut) { Text("خروج") }
            }
        }
        Spacer(Modifier.height(14.dp))

        if (state.username == null) {
            SignInForm(state = state, onSignIn = onSignIn, onSignUp = onSignUp)
        } else {
            AccountContent(
                state = state,
                onBackupNow = onBackupNow,
                onAutoBackupChange = onAutoBackupChange,
                onRefresh = onRefresh,
                onRestore = onRestore,
                onDeleteBackup = onDeleteBackup,
                onDeleteAccount = onDeleteAccount,
            )
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
                    cloudStageLabel(state.stage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SignInForm(
    state: CloudUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    // Passwords are deliberately not saveable: they should not outlive the screen.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val nameProblem = if (creating && name.isNotBlank()) CloudUsername.problemWith(name) else null
    val mismatch = creating && confirm.isNotEmpty() && confirm != password
    val ready = name.isNotBlank() && password.isNotEmpty() && !state.busy &&
        (!creating || (nameProblem == null && confirm == password))

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(false to "لدي حساب", true to "حساب جديد").forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = creating == value,
                onClick = { creating = value },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
            ) { Text(label) }
        }
    }
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(CloudUsername.MAX_LENGTH + 4) },
        label = { Text("الاسم") },
        singleLine = true,
        isError = nameProblem != null,
        supportingText = nameProblem?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = if (creating) ContentType.NewUsername else ContentType.Username },
    )
    Spacer(Modifier.height(8.dp))
    PasswordField(
        value = password,
        onValueChange = { password = it },
        label = "كلمة المرور",
        visible = showPassword,
        onToggleVisible = { showPassword = !showPassword },
        contentType = if (creating) ContentType.NewPassword else ContentType.Password,
        imeAction = if (creating) ImeAction.Next else ImeAction.Done,
    )
    if (creating) {
        Spacer(Modifier.height(8.dp))
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "تأكيد كلمة المرور",
            visible = showPassword,
            onToggleVisible = { showPassword = !showPassword },
            contentType = ContentType.NewPassword,
            imeAction = ImeAction.Done,
            error = if (mismatch) "الكلمتان غير متطابقتين." else null,
        )
        Text(
            text = "لا نطلب بريداً ولا رقم جوال، لذلك لا يمكن استعادة كلمة المرور إن نُسيت. " +
                "اكتبها في مكان آمن.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Spacer(Modifier.height(14.dp))
    Button(
        enabled = ready,
        onClick = { if (creating) onSignUp(name, password) else onSignIn(name, password) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(if (creating) "إنشاء الحساب" else "دخول", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    contentType: ContentType,
    imeAction: ImeAction,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(CloudUsername.MAX_PASSWORD)) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentType = contentType },
    )
}

@Composable
private fun AccountContent(
    state: CloudUiState,
    onBackupNow: () -> Unit,
    onAutoBackupChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRestore: (CloudBackupInfo) -> Unit,
    onDeleteBackup: (CloudBackupInfo) -> Unit,
    onDeleteAccount: () -> Unit,
) {
    Button(
        enabled = !state.busy,
        onClick = onBackupNow,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text("نسخ إلى السحابة الآن", fontWeight = FontWeight.SemiBold)
    }
    if (state.lastUploadAt > 0L) {
        Text(
            text = "آخر نسخة من هذا الجهاز: ${formatDate(state.lastUploadAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    MushafSoftDivider(verticalPadding = 12.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("نسخ تلقائي يومي", style = MaterialTheme.typography.bodyLarge)
            Text(
                "مرة في اليوم عند توفر الإنترنت، ويُتخطى اليوم الذي لا جديد فيه.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = state.autoBackup, onCheckedChange = onAutoBackupChange, enabled = !state.busy)
    }

    MushafSoftDivider(verticalPadding = 12.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "النسخ في حسابك",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(enabled = !state.busy, onClick = onRefresh) {
            Icon(Icons.Outlined.Refresh, contentDescription = "تحديث القائمة")
        }
    }
    Text(
        "تُحفظ نسخ آخر ٧ أيام وتُحذف الأقدم تلقائياً، وتبقى أحدث نسخة من كل جهاز. " +
            "ولا يمكن حذف أحدث نسخة في حسابك.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )
    when {
        !state.listLoaded -> Text(
            if (state.busy) "جارٍ تحميل القائمة…" else "اضغط التحديث لعرض النسخ.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.backups.isEmpty() -> Text(
            "لا توجد نسخ في حسابك بعد.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val newest = CloudBackupRetention.newest(state.backups)
            state.backups.forEach { backup ->
                CloudBackupRow(
                    backup = backup,
                    isNewest = backup.id == newest?.id,
                    enabled = !state.busy,
                    onRestore = { onRestore(backup) },
                    onDelete = { onDeleteBackup(backup) },
                )
            }
        }
    }

    MushafSoftDivider(verticalPadding = 12.dp)
    TextButton(
        enabled = !state.busy,
        onClick = onDeleteAccount,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("حذف الحساب ونسخه")
    }
}

@Composable
private fun CloudBackupRow(
    backup: CloudBackupInfo,
    isNewest: Boolean,
    enabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (backup.fromThisDevice) "${backup.deviceName} (هذا الجهاز)" else backup.deviceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatDate(backup.createdAt)} • ${formatSize(backup.sizeBytes)} • " +
                        if (backup.auto) "تلقائية" else "يدوية",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isNewest) {
                    Text(
                        text = "أحدث نسخة",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            OutlinedButton(
                enabled = enabled,
                onClick = onRestore,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { Text("استعادة") }
            if (isNewest) {
                // Keeps this row's restore button in line with the others.
                Spacer(Modifier.size(DeleteButtonSize))
            } else {
                IconButton(enabled = enabled, onClick = onDelete, modifier = Modifier.size(DeleteButtonSize)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "حذف هذه النسخة",
                        tint = if (enabled) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private val DeleteButtonSize = 40.dp

/** Deleting one copy is final, though far less than the account, so a plain question will do. */
@Composable
internal fun DeleteCloudBackupDialog(
    source: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        title = { Text("حذف هذه النسخة؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(source, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("تُحذف من حسابك نهائياً ولا يمكن استرجاعها. بياناتك على هذا الجهاز لا تتأثر.")
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("حذف") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

/** Deleting the account is final, so it asks for the password once more. */
@Composable
internal fun DeleteCloudAccountDialog(
    busy: Boolean,
    onConfirm: (password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null) },
        title = { Text("حذف الحساب نهائياً؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "يُحذف حسابك وكل نسخه من الخادم ولا يمكن التراجع. " +
                        "بياناتك على هذا الجهاز لا تتأثر."
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "كلمة المرور للتأكيد",
                    visible = visible,
                    onToggleVisible = { visible = !visible },
                    contentType = ContentType.Password,
                    imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = password.isNotEmpty() && !busy,
                onClick = { onConfirm(password) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("حذف نهائي") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

private fun cloudStageLabel(stage: CloudStage?): String = when (stage) {
    CloudStage.SigningIn -> "جارٍ تسجيل الدخول…"
    CloudStage.SigningUp -> "جارٍ إنشاء الحساب وحفظ أول نسخة…"
    CloudStage.Loading -> "جارٍ تحميل النسخ…"
    CloudStage.Uploading -> "جارٍ رفع النسخة…"
    CloudStage.Restoring -> "جارٍ تنزيل النسخة ودمجها…"
    CloudStage.DeletingBackup -> "جارٍ حذف النسخة…"
    CloudStage.Deleting -> "جارٍ حذف الحساب…"
    null -> "جاهز"
}
