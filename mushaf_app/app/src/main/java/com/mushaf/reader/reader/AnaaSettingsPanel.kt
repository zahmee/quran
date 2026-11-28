package com.mushaf.reader.reader

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * The «آناء» section of the «القائمة» settings tab. What it offers follows what this device has:
 * a way to install Ana'a when it is missing, a single switch and a nudge to update for an Ana'a
 * from before the shortcuts, and the switch plus one per shortcut otherwise.
 */
@Composable
internal fun AnaaSettingsPanel(settings: AnaaSettings) {
    val context = LocalContext.current
    val status = rememberAnaaStatus()
    val openStore = {
        if (!openAnaaInStore(context)) {
            Toast.makeText(
                context,
                "تعذّر فتح المتجر. تأكد من وجود متجر أو متصفح على الجهاز.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    SettingsPanel(
        title = "آناء",
        body = "تطبيق الأذكار والصلاة من الناشر نفسه. افتحه من قائمة المزيد على الشاشة التي تختارها.",
        icon = Icons.Outlined.Apps
    ) {
        if (status == AnaaStatus.Missing) {
            Note("آناء غير مثبّت على هذا الجهاز، فلا يظهر في القائمة. وإذا ثبّته ظهر فيها وحده.")
            ActionRow(
                icon = Icons.Outlined.Download,
                title = "ثبّت آناء من المتجر",
                body = "مجاني، للأذكار والمسبحة ومواقيت الصلاة والتقويم.",
                onClick = openStore
            )
            return@SettingsPanel
        }

        ToggleSettingRow(
            icon = Icons.Outlined.Apps,
            title = "إظهار آناء في قائمة المزيد",
            body = if (status == AnaaStatus.Old) "سطر واحد يفتح آناء." else "الاختصارات التي تختارها أدناه.",
            checked = settings.showInMenu,
            onCheckedChange = settings::updateShowInMenu
        )

        if (status == AnaaStatus.Old) {
            SettingsDivider()
            Note("هذا الإصدار من آناء يُفتح على شاشته الرئيسية فقط. حدّثه لتفتحه مباشرة على المسبحة أو الصلاة أو غيرهما.")
            ActionRow(
                icon = Icons.Outlined.Update,
                title = "حدّث آناء من المتجر",
                body = "بعد التحديث تظهر الاختصارات هنا.",
                onClick = openStore
            )
            return@SettingsPanel
        }

        if (settings.showInMenu) {
            AnaaShortcut.entries.forEach { shortcut ->
                SettingsDivider()
                ToggleSettingRow(
                    icon = shortcut.icon,
                    title = shortcut.title,
                    body = shortcut.body,
                    checked = shortcut.id in settings.shortcuts,
                    onCheckedChange = { settings.setShortcut(shortcut, it) }
                )
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    )
}
