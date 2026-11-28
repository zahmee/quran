package com.mushaf.reader.reader

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mushaf.reader.BuildConfig
import com.mushaf.reader.data.ReadingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The contract آناء (com.sakinah.tasbih) publishes for other apps to open it on a given screen.
 * Ana'a owns these names and promises never to change or drop one, only to add; see its
 * docs/NATLU_LINK_AR.md. Nothing is exchanged beyond the destination.
 */
internal object AnaaContract {
    const val PACKAGE = "com.sakinah.tasbih"
    /** Ana'a's debug build, so a debug Natlu can be tried against it. */
    const val PREVIEW_PACKAGE = "com.sakinah.tasbih.preview"
    const val ACTION_OPEN = "com.sakinah.tasbih.action.OPEN"
    const val EXTRA_DESTINATION = "com.sakinah.tasbih.extra.DESTINATION"
}

/** A screen of Ana'a the More menu can open directly. [id] is the contract's destination value. */
internal enum class AnaaShortcut(val id: String, val title: String, val body: String, val icon: ImageVector) {
    Tasbih("tasbih", "المسبحة", "يفتح آناء على المسبحة.", Icons.Outlined.TouchApp),
    Prayer("prayer", "مواقيت الصلاة", "يفتح آناء على مواقيت الصلاة.", Icons.Outlined.AccessTime),
    Adhkar("adhkar", "الأذكار", "يفتح آناء على مكتبة الأذكار.", Icons.Outlined.AutoStories),
    Calendar("calendar", "التقويم", "يفتح آناء على تقويم الشهر الحالي.", Icons.Outlined.CalendarMonth),
    Qibla("qibla", "القبلة", "يفتح آناء على اتجاه القبلة.", Icons.Outlined.Explore),
    Today("today", "اليوم", "يفتح آناء على صفحة اليوم.", Icons.Outlined.WbSunny);

    companion object {
        /** On before the reader has chosen: the two a reader most often reaches for mid-reading. */
        val DEFAULT_IDS: Set<String> = setOf(Tasbih.id, Prayer.id)
    }
}

/** What this device has of Ana'a. */
internal enum class AnaaStatus {
    /** Not installed. */
    Missing,
    /** Installed, but a version from before the contract: it can only be opened on its home screen. */
    Old,
    /** Installed and answers the contract, so it can be opened on any [AnaaShortcut]. */
    Ready,
}

/** What the More menu shows for Ana'a. */
internal sealed interface AnaaMenu {
    data object None : AnaaMenu
    /** A single «آناء» item that opens it on its home screen. */
    data object OpenOnly : AnaaMenu
    data class Shortcuts(val items: List<AnaaShortcut>) : AnaaMenu
}

/**
 * Nothing when Ana'a is missing or the reader turned it off; one plain item for an old Ana'a, or
 * when every shortcut is off; otherwise the chosen shortcuts, in [AnaaShortcut]'s order.
 */
internal fun anaaMenu(status: AnaaStatus, showInMenu: Boolean, shortcutIds: Set<String>): AnaaMenu {
    if (status == AnaaStatus.Missing || !showInMenu) return AnaaMenu.None
    if (status == AnaaStatus.Old) return AnaaMenu.OpenOnly
    val chosen = AnaaShortcut.entries.filter { it.id in shortcutIds }
    return if (chosen.isEmpty()) AnaaMenu.OpenOnly else AnaaMenu.Shortcuts(chosen)
}

/** The packages Ana'a may be installed under, in the order they are tried. */
private val anaaPackages: List<String> =
    if (BuildConfig.DEBUG) listOf(AnaaContract.PACKAGE, AnaaContract.PREVIEW_PACKAGE)
    else listOf(AnaaContract.PACKAGE)

private fun openIntent(pkg: String, destination: String?): Intent =
    Intent(AnaaContract.ACTION_OPEN)
        .setPackage(pkg)
        .apply { if (destination != null) putExtra(AnaaContract.EXTRA_DESTINATION, destination) }
        // Ana'a is singleTask from the contract's first version; these keep an older one, opened
        // by the launcher intent below, from stacking a second copy inside Natlu's task.
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

private fun answersContract(pm: PackageManager, pkg: String): Boolean =
    pm.queryIntentActivities(openIntent(pkg, null), 0).isNotEmpty()

private fun isInstalled(pm: PackageManager, pkg: String): Boolean =
    pm.getLaunchIntentForPackage(pkg) != null

/** Asks the system, through the `<queries>` entry in the manifest; cheap enough to call on resume. */
internal fun anaaStatus(context: Context): AnaaStatus {
    val pm = context.packageManager
    return when {
        anaaPackages.any { answersContract(pm, it) } -> AnaaStatus.Ready
        anaaPackages.any { isInstalled(pm, it) } -> AnaaStatus.Old
        else -> AnaaStatus.Missing
    }
}

/**
 * Opens Ana'a on [shortcut], or on its home screen when [shortcut] is null or this Ana'a predates
 * the contract. False when it is not installed (or the system refused), so the caller can say so.
 */
internal fun openAnaa(context: Context, shortcut: AnaaShortcut?): Boolean {
    val pm = context.packageManager
    val intent = anaaPackages.firstOrNull { answersContract(pm, it) }
        ?.let { openIntent(it, shortcut?.id ?: AnaaShortcut.Today.id) }
        ?: anaaPackages.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ?: return false
    return runCatching { context.startActivity(intent) }.isSuccess
}

/** Ana'a's page in the store: Play first, then the web page. False when neither would open. */
internal fun openAnaaInStore(context: Context): Boolean =
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, "market://details?id=${AnaaContract.PACKAGE}".toUri())
                .setPackage("com.android.vending")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess || runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=${AnaaContract.PACKAGE}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

/**
 * [anaaStatus], asked again each time the screen resumes: a reader who installs, updates or
 * removes Ana'a and comes back finds the menu and the settings already matching.
 */
@Composable
internal fun rememberAnaaStatus(): AnaaStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(anaaStatus(context)) }
    LifecycleResumeEffect(context) {
        status = anaaStatus(context)
        onPauseOrDispose { }
    }
    return status
}

/**
 * The reader's Ana'a choices: whether it appears in the More menu, and which shortcuts. Kept on
 * this device only, like the recitation choices, since Ana'a is installed per device.
 */
@Stable
internal class AnaaSettings(private val store: ReadingStore, private val scope: CoroutineScope) {
    var showInMenu by mutableStateOf(true)
        private set
    var shortcuts by mutableStateOf(AnaaShortcut.DEFAULT_IDS)
        private set

    init {
        scope.launch {
            val saved = store.anaaPrefs()
            showInMenu = saved.first
            shortcuts = saved.second
        }
    }

    fun updateShowInMenu(value: Boolean) {
        if (value == showInMenu) return
        showInMenu = value
        scope.launch { store.setAnaaInMenu(value) }
    }

    fun setShortcut(shortcut: AnaaShortcut, on: Boolean) {
        val next = if (on) shortcuts + shortcut.id else shortcuts - shortcut.id
        if (next == shortcuts) return
        shortcuts = next
        scope.launch { store.setAnaaShortcuts(next) }
    }
}
