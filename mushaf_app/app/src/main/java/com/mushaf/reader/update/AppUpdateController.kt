package com.mushaf.reader.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.google.android.play.core.ktx.requestCompleteUpdate
import com.mushaf.reader.data.ReadingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the reader should be told about an update, if anything. */
sealed interface AppUpdateState {
    /** Nothing to say — the overwhelmingly common case. */
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class Available(val versionCode: Int) : AppUpdateState
    data class Downloading(val percent: Int) : AppUpdateState
    /** Downloaded and waiting; installing restarts the app. */
    data object ReadyToInstall : AppUpdateState
    /** Only ever set by a check the reader asked for, so silence stays silent. */
    data object UpToDate : AppUpdateState
    /** No Play Store to ask — a sideloaded APK, or Play is unavailable on this device. */
    data object Unavailable : AppUpdateState
    /** Play's own update sheet could not be opened, or it came back reporting failure. */
    data object UpdateFailed : AppUpdateState
    /** Nothing on the device can open the store listing — no Play Store and no browser. */
    data object StoreUnreachable : AppUpdateState
}

/**
 * Flexible in-app updates, Play's way.
 *
 * "Flexible" means Play downloads the new version in the background while the reader keeps
 * reading, and only then are they asked to restart. A mushaf must never be taken over by a
 * full-screen "you must update now" — so the immediate flow is deliberately not used here.
 *
 * Nothing in this class touches the network: it asks the installed Play Store app over IPC, which
 * is why the app still needs no INTERNET permission. On a device with no Play Store (a sideloaded
 * APK) every call fails quietly and the reader is left alone.
 */
@Stable
class AppUpdateController(
    context: Context,
    private val store: ReadingStore,
    private val scope: CoroutineScope,
) {
    private companion object {
        /** Nudge about a new version at most once a day. Play itself is still asked on every
         *  foreground — that is the only way a download left running earlier gets noticed. */
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
        /** After the reader says "later", stay quiet about that same version for a week. */
        const val SNOOZE_MS = 7L * 24 * 60 * 60 * 1000
        /** …unless the update has been out this long, at which point ask again anyway. */
        const val NUDGE_ANYWAY_DAYS = 14
    }

    private val appContext = context.applicationContext
    private val manager = AppUpdateManagerFactory.create(appContext)

    var state by mutableStateOf<AppUpdateState>(AppUpdateState.Idle)
        private set

    private val listener = InstallStateUpdatedListener { install ->
        state = when (install.installStatus()) {
            InstallStatus.PENDING -> AppUpdateState.Downloading(0)
            InstallStatus.DOWNLOADING -> AppUpdateState.Downloading(
                percentOf(install.bytesDownloaded(), install.totalBytesToDownload())
            )
            InstallStatus.DOWNLOADED -> AppUpdateState.ReadyToInstall
            InstallStatus.CANCELED, InstallStatus.FAILED -> AppUpdateState.Idle
            else -> state
        }
    }

    init {
        manager.registerListener(listener)
    }

    fun dispose() {
        manager.unregisterListener(listener)
    }

    /**
     * Called whenever the reader comes to the foreground.
     *
     * A download still running — or already finished — from an earlier run is surfaced regardless
     * of the daily throttle; otherwise it would sit there forever and the update would never
     * actually be installed.
     */
    fun onForeground() {
        scope.launch {
            // Deliberately does NOT clear a pending failure message first: this runs on the way
            // back from Play's sheet, just before the flow's own result arrives, so clearing here
            // would race with — and usually swallow — the failure it is meant to report.
            val info = runCatching { manager.requestAppUpdateInfo() }.getOrNull() ?: return@launch
            inProgressState(info)?.let {
                state = it
                return@launch
            }
            if (!isCheckDue()) return@launch
            evaluate(info, manual = false)
        }
    }

    /** The reader pressed "check for updates", so an answer is owed either way. */
    fun checkNow() {
        scope.launch {
            state = AppUpdateState.Checking
            val info = runCatching { manager.requestAppUpdateInfo() }.getOrNull()
            if (info == null) {
                state = AppUpdateState.Unavailable
                return@launch
            }
            inProgressState(info)?.let {
                state = it
                return@launch
            }
            evaluate(info, manual = true)
        }
    }

    private suspend fun evaluate(info: AppUpdateInfo, manual: Boolean) {
        store.setUpdateCheckedAt(System.currentTimeMillis())
        val updatable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
        if (!updatable) {
            state = if (manual) AppUpdateState.UpToDate else AppUpdateState.Idle
            return
        }
        val version = info.availableVersionCode()
        if (!manual && isSnoozed(version, info.clientVersionStalenessDays() ?: 0)) {
            state = AppUpdateState.Idle
            return
        }
        state = AppUpdateState.Available(version)
    }

    /**
     * The state of an update already under way, or null when there is none.
     *
     * Play reports a started flow as DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS and never as
     * UPDATE_AVAILABLE, so a plain availability check reads it as "nothing to do" — which is how a
     * manual check ends up claiming the app is current while a download of it is in flight.
     */
    private fun inProgressState(info: AppUpdateInfo): AppUpdateState? {
        val progress = AppUpdateState.Downloading(
            percentOf(info.bytesDownloaded(), info.totalBytesToDownload())
        )
        return when (info.installStatus()) {
            InstallStatus.DOWNLOADED -> AppUpdateState.ReadyToInstall
            InstallStatus.DOWNLOADING -> progress
            InstallStatus.PENDING -> AppUpdateState.Downloading(0)
            else ->
                if (info.updateAvailability() ==
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                ) progress else null
        }
    }

    private fun percentOf(done: Long, total: Long): Int =
        if (total > 0L) ((done * 100L) / total).toInt().coerceIn(0, 100) else 0

    private suspend fun isCheckDue(): Boolean =
        System.currentTimeMillis() - store.updateNudge().checkedAt >= CHECK_INTERVAL_MS

    private suspend fun isSnoozed(versionCode: Int, stalenessDays: Int): Boolean {
        if (stalenessDays >= NUDGE_ANYWAY_DAYS) return false
        val nudge = store.updateNudge()
        if (nudge.dismissedVersion != versionCode) return false
        return System.currentTimeMillis() - nudge.dismissedAt < SNOOZE_MS
    }

    /**
     * Hands the reader over to Play's own consent sheet; the download runs in the background.
     *
     * The info is asked for again here instead of reusing the one the check produced: an
     * [AppUpdateInfo] launches exactly one flow and then goes stale, and the bar may have been
     * sitting on screen for hours before the tap.
     */
    fun startUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        scope.launch {
            val info = runCatching { manager.requestAppUpdateInfo() }.getOrNull()
            if (info == null) {
                state = AppUpdateState.UpdateFailed
                return@launch
            }
            if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE ||
                !info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                // Play may already be carrying the job, or may have dropped it. Either way say
                // something — letting the bar vanish reads as if the tap never registered.
                state = inProgressState(info) ?: AppUpdateState.UpdateFailed
                return@launch
            }
            runCatching {
                manager.startUpdateFlowForResult(
                    info,
                    launcher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                )
            }.onFailure { state = AppUpdateState.UpdateFailed }
        }
    }

    /**
     * Backing out of Play's sheet counts as "later" — the same snooze as dismissing the bar. A
     * flow that actually failed does not: a week of silence over a crash would bury an update the
     * reader had just asked for.
     */
    fun onUpdateFlowResult(resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_OK -> Unit
            Activity.RESULT_CANCELED -> dismiss()
            else -> state = AppUpdateState.UpdateFailed
        }
    }

    /** Restarts the app into the downloaded version. */
    fun install() {
        scope.launch { runCatching { manager.requestCompleteUpdate() } }
    }

    fun dismiss() {
        val version = (state as? AppUpdateState.Available)?.versionCode
        state = AppUpdateState.Idle
        if (version != null) {
            scope.launch { store.setUpdateDismissed(version, System.currentTimeMillis()) }
        }
    }

    /** Clears the states that are only a message to the reader, not a pending action. */
    fun clearMessage() {
        when (state) {
            AppUpdateState.UpToDate,
            AppUpdateState.Unavailable,
            AppUpdateState.UpdateFailed,
            AppUpdateState.StoreUnreachable -> state = AppUpdateState.Idle
            else -> Unit
        }
    }

    /** The store listing — the only route left for anyone who installed the APK directly. */
    fun openStorePage() {
        val id = appContext.packageName
        val opened = openView("market://details?id=$id") ||
            openView("https://play.google.com/store/apps/details?id=$id")
        // A button that does nothing at all is worse than one that says why it can't.
        if (!opened) state = AppUpdateState.StoreUnreachable
    }

    private fun openView(uri: String): Boolean = runCatching {
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
}

/** The reader's handle on the update flow: what to show, and what the buttons do. */
@Stable
class AppUpdateUi(
    private val controller: AppUpdateController,
    private val onStartUpdate: () -> Unit,
) {
    val state: AppUpdateState get() = controller.state

    fun update() = onStartUpdate()
    fun install() = controller.install()
    fun dismiss() = controller.dismiss()
    fun checkNow() = controller.checkNow()
    fun clearMessage() = controller.clearMessage()
    fun openStorePage() = controller.openStorePage()
}
