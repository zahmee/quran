package com.mushaf.reader.reader

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.backup.BackupException
import com.mushaf.reader.data.backup.BackupFileInfo
import com.mushaf.reader.data.backup.BackupSnapshot
import com.mushaf.reader.data.backup.BackupStage
import com.mushaf.reader.data.backup.BackupUiState
import com.mushaf.reader.data.backup.CloudBackupInfo
import com.mushaf.reader.data.backup.CloudBackupRepository
import com.mushaf.reader.data.backup.CloudBackupWorker
import com.mushaf.reader.data.backup.CloudOfflineException
import com.mushaf.reader.data.backup.CloudStage
import com.mushaf.reader.data.backup.CloudUiState
import com.mushaf.reader.data.backup.FileBackupRepository
import com.mushaf.reader.data.sync.CloudFeature
import com.mushaf.reader.data.sync.CloudHealth
import com.mushaf.reader.data.sync.CloudHealthStore
import com.mushaf.reader.data.sync.CloudProblem
import com.mushaf.reader.data.sync.CloudSyncRepository
import com.mushaf.reader.data.sync.CloudSyncStore
import com.mushaf.reader.data.sync.CloudSyncWorker
import com.mushaf.reader.data.sync.ReadingSync
import com.mushaf.reader.data.sync.SyncUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A cloud fault without words of its own from the server. */
private const val GENERIC_CLOUD_ERROR = "تعذر إكمال العملية. أعد المحاولة بعد قليل."

/**
 * What the reader screen needs from the rest of the app for backup and sync: where the reader
 * is, and how to put a restored backup into effect. The view model provides it.
 */
internal interface BackupSyncHost {
    /** The page the reader is on, which sync reports. */
    val currentPage: Int

    /** The gold bookmark's verse key; sync carries that one bookmark between devices. */
    val goldBookmark: String?

    /** Ends the reading session in progress and awaits every pending write, before a backup reads the data. */
    suspend fun checkpointForBackup()

    /** Shows a restored (or merged) backup: settings, bookmarks, progress and statistics. */
    suspend fun applyRestored(snapshot: BackupSnapshot)

    /** Sync brought khatmas completed on another device. */
    suspend fun reloadKhatmas()
}

/**
 * Keeping the reader's data safe and in step: the backup file, the cloud account and its
 * backups, reading sync between devices, and the cloud faults the reader must hear about.
 * Each operation runs behind a busy state the backup screen shows, and ends in a message or an
 * error said in words.
 */
class BackupSyncController internal constructor(
    private val app: Application,
    private val scope: CoroutineScope,
    private val store: ReadingStore,
    private val host: BackupSyncHost,
    initialLastBackup: ReadingStore.LastBackup?,
) {
    private val backupRepo = FileBackupRepository(app)
    private val cloudRepo = CloudBackupRepository(app)
    private val syncRepo = CloudSyncRepository(app)
    private val syncStore = CloudSyncStore(app)
    private val healthStore = CloudHealthStore(app)

    var backupUiState by mutableStateOf(
        BackupUiState(
            lastBackup = initialLastBackup?.let {
                BackupFileInfo(fileName = it.fileName, savedAt = it.savedAt, sizeBytes = it.sizeBytes)
            }
        )
    )
        private set

    var cloudUiState by mutableStateOf(CloudUiState(available = cloudRepo.isAvailable))
        private set

    // ── The backup file ─────────────────────────────────────────────────────────

    fun suggestedBackupFileName(): String = backupRepo.suggestedFileName()

    fun exportBackup(target: Uri) {
        scope.launch {
            startBackupOperation(BackupStage.Exporting)
            try {
                host.checkpointForBackup()
                val saved = backupRepo.exportTo(target)
                store.setLastBackup(
                    ReadingStore.LastBackup(saved.savedAt, saved.fileName, saved.sizeBytes)
                )
                backupUiState = backupUiState.copy(
                    lastBackup = saved,
                    busy = false,
                    stage = null,
                    error = null,
                    message = "حُفظت النسخة في «${saved.fileName}».",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                finishBackupWithError(error)
            }
        }
    }

    fun importBackup(source: Uri, takeDisplaySettings: Boolean) {
        scope.launch {
            startBackupOperation(BackupStage.Importing)
            try {
                // Finish and await old writes before folding the backup into the database and preferences.
                host.checkpointForBackup()
                val restored = backupRepo.importFrom(source, takeDisplaySettings)
                host.applyRestored(restored.snapshot)
                backupUiState = backupUiState.copy(
                    busy = false,
                    stage = null,
                    error = null,
                    message = "اكتملت الاستعادة من نسخة ${formatBackupDate(restored.snapshot.createdAt)} " +
                        "(${restored.snapshot.deviceName}).",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                finishBackupWithError(error)
            }
        }
    }

    /** The user closed the system picker without choosing a file. */
    fun backupSelectionCancelled() {
        backupUiState = backupUiState.copy(busy = false, stage = null)
    }

    fun clearBackupFeedback() {
        backupUiState = backupUiState.copy(message = null, error = null)
    }

    private fun startBackupOperation(stage: BackupStage) {
        backupUiState = backupUiState.copy(
            busy = true,
            stage = stage,
            message = null,
            error = null,
        )
    }

    private fun finishBackupWithError(error: Exception) {
        val message = if (error is BackupException) error.message else null
        backupUiState = backupUiState.copy(
            busy = false,
            stage = null,
            message = null,
            error = message ?: "تعذر إكمال العملية. أعد المحاولة باختيار الملف مرة أخرى.",
        )
    }

    private fun formatBackupDate(time: Long): String =
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar")).format(Date(time))

    // ── Cloud backup ────────────────────────────────────────────────────────────

    private suspend fun loadCloudAccount() {
        val username = cloudRepo.reconcileAccount()
        val prefs = store.cloudPrefs()
        cloudUiState = cloudUiState.copy(
            username = username,
            autoBackup = username != null && prefs.autoBackup,
            lastUploadAt = prefs.lastUploadAt,
        )
    }

    /** Called when the backup screen opens, so the list reflects the other device's uploads. */
    fun refreshCloudBackups() {
        if (cloudUiState.username == null || cloudUiState.busy) return
        runCloud(CloudStage.Loading) {
            cloudUiState = cloudUiState.copy(backups = cloudRepo.list(), listLoaded = true)
            null
        }
    }

    fun cloudSignUp(name: String, password: String) = runCloud(CloudStage.SigningUp) {
        val username = cloudRepo.signUp(name, password)
        CloudBackupWorker.schedule(app, enabled = true)
        cloudUiState = cloudUiState.copy(username = username, autoBackup = true)
        // The first backup right away: an account with nothing in it protects nothing.
        host.checkpointForBackup()
        cloudRepo.upload(auto = false)
        healthStore.recordSuccess(CloudFeature.BACKUP)
        loadCloudAccount()
        cloudUiState = cloudUiState.copy(backups = cloudRepo.list(), listLoaded = true)
        syncReading(userAsked = false)
        "أُنشئ حسابك «$username» وحُفظت أول نسخة. احفظ كلمة المرور جيداً."
    }

    fun cloudSignIn(name: String, password: String) = runCloud(CloudStage.SigningIn) {
        val username = cloudRepo.signIn(name, password)
        CloudBackupWorker.schedule(app, enabled = true)
        loadCloudAccount()
        val backups = cloudRepo.list()
        cloudUiState = cloudUiState.copy(backups = backups, listLoaded = true)
        syncReading(userAsked = false)
        if (backups.isEmpty()) "مرحباً $username. لا توجد نسخ في حسابك بعد."
        else "مرحباً $username. اختر نسخة لاستعادتها على هذا الجهاز."
    }

    fun cloudSignOut() {
        if (cloudUiState.busy) return
        scope.launch {
            CloudBackupWorker.schedule(app, enabled = false)
            cloudRepo.signOut()
            forgetSyncAccount()
            forgetCloudProblems()
            cloudUiState = CloudUiState(available = cloudRepo.isAvailable)
        }
    }

    fun cloudBackupNow() = runCloud(CloudStage.Uploading) {
        host.checkpointForBackup()
        try {
            cloudRepo.upload(auto = false)
            healthStore.recordSuccess(CloudFeature.BACKUP)
        } catch (offline: CloudOfflineException) {
            throw offline
        } catch (refused: BackupException) {
            healthStore.recordRefused(CloudFeature.BACKUP, refused.message ?: GENERIC_CLOUD_ERROR)
            throw refused
        } finally {
            refreshCloudProblems()
        }
        loadCloudAccount()
        cloudUiState = cloudUiState.copy(backups = cloudRepo.list(), listLoaded = true)
        "حُفظت نسخة جديدة في حسابك."
    }

    fun cloudRestore(backup: CloudBackupInfo, takeDisplaySettings: Boolean) = runCloud(CloudStage.Restoring) {
        // Finish and await old writes before folding the backup into the database and preferences.
        host.checkpointForBackup()
        host.applyRestored(cloudRepo.restore(backup, takeDisplaySettings))
        "اكتملت الاستعادة من نسخة ${formatBackupDate(backup.createdAt)} (${backup.deviceName})."
    }

    fun cloudDeleteBackup(backup: CloudBackupInfo) = runCloud(CloudStage.DeletingBackup) {
        cloudRepo.delete(backup)
        cloudUiState = cloudUiState.copy(backups = cloudRepo.list(), listLoaded = true)
        "حُذفت النسخة من حسابك."
    }

    fun setCloudAutoBackup(enabled: Boolean) {
        if (cloudUiState.username == null || enabled == cloudUiState.autoBackup) return
        cloudUiState = cloudUiState.copy(autoBackup = enabled)
        scope.launch {
            store.setCloudAutoBackup(enabled)
            CloudBackupWorker.schedule(app, enabled)
        }
    }

    fun cloudDeleteAccount(password: String) = runCloud(CloudStage.Deleting) {
        cloudRepo.deleteAccount(password)
        CloudBackupWorker.schedule(app, enabled = false)
        forgetSyncAccount()
        forgetCloudProblems()
        cloudUiState = CloudUiState(available = cloudRepo.isAvailable)
        "حُذف حسابك وجميع نسخه من الخادم. بياناتك على هذا الجهاز باقية كما هي."
    }

    fun clearCloudFeedback() {
        cloudUiState = cloudUiState.copy(message = null, error = null)
    }

    // ── Reading sync between the reader's devices ──────────────────────────────

    var syncUiState by mutableStateOf(SyncUiState())
        private set

    /** The app came to the front: see whether another device has read further since. */
    fun onAppForeground() {
        // Hidden with "Later" only until the reader next comes back: a fault is not forgotten.
        cloudProblemsHidden = false
        scope.launch {
            if (syncRepo.isSignedIn) syncReading(userAsked = false)
            refreshCloudProblems()
        }
    }

    /**
     * The app went to the background: report this page as where the reader stopped. Its time
     * stays the moment the reader turned to it — stamping "now" here would make merely glancing
     * at the app look like reading, and hide the newer place another device has to offer.
     * The upload is left to [CloudSyncWorker] because the process may not live long enough.
     */
    fun onAppBackground() {
        if (!syncRepo.isSignedIn) return
        CloudSyncWorker.enqueue(app)
    }

    /** "Sync now" in the history tab, which also reloads the history. */
    fun syncNow() {
        if (syncUiState.loading) return
        scope.launch {
            syncReading(userAsked = true)
            if (syncUiState.error == null) loadSyncHistory()
        }
    }

    /** Called when the history tab opens. */
    fun refreshSyncHistory() {
        if (!syncRepo.isSignedIn || syncUiState.loading) return
        scope.launch {
            syncReading(userAsked = false)
            loadSyncHistory()
        }
    }

    private suspend fun syncReading(userAsked: Boolean) {
        syncUiState = syncUiState.copy(loading = true, error = null)
        try {
            val result = syncRepo.sync()
            val local = syncStore.state()
            val elsewhere = ReadingSync.elsewhere(
                thisDeviceId = result.thisDeviceId,
                localLatestAt = local.latestAt,
                currentPage = host.currentPage,
                localBookmarkKey = host.goldBookmark,
                devices = result.devices,
                dismissedKey = local.dismissedKey,
            )
            if (result.khatmasAdded > 0) host.reloadKhatmas()
            healthStore.recordSuccess(CloudFeature.SYNC)
            syncUiState = syncUiState.copy(
                loading = false,
                elsewhere = elsewhere,
                lastSyncAt = result.syncedAt,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Offline passes by itself; anything else is a fault the reader must hear about,
            // whether or not they asked for this sync.
            if (error !is CloudOfflineException) {
                healthStore.recordRefused(CloudFeature.SYNC, (error as? BackupException)?.message ?: GENERIC_CLOUD_ERROR)
            }
            syncUiState = syncUiState.copy(
                loading = false,
                lastSyncAt = syncStore.state().lastSyncAt,
                error = if (userAsked) {
                    (error as? BackupException)?.message ?: "تعذرت المزامنة. أعد المحاولة بعد قليل."
                } else {
                    null
                },
            )
        }
        refreshCloudProblems()
    }

    private suspend fun loadSyncHistory() {
        try {
            syncUiState = syncUiState.copy(history = syncRepo.history())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The tab keeps what it showed last; the sync line already says whether it is online.
        }
    }

    /** "Not now": this offer stays away until that device moves on. */
    fun dismissElsewhere() {
        val key = syncUiState.elsewhere?.key ?: return
        syncUiState = syncUiState.copy(elsewhere = null)
        scope.launch { syncStore.setDismissed(key) }
    }

    // ── Cloud faults the reader must hear about ────────────────────────────────

    /** What is broken or stuck in cloud backup and reading sync, newest knowledge first. */
    var cloudProblems by mutableStateOf<List<CloudProblem>>(emptyList())
        private set

    /** "Later" on the reader's alert: hidden until the app next comes to the front. */
    var cloudProblemsHidden by mutableStateOf(false)
        private set

    fun hideCloudProblems() { cloudProblemsHidden = true }

    /** "Try again" from an alert: the same operation the reader would start by hand. */
    fun retryCloudProblem(problem: CloudProblem) {
        when (problem.feature) {
            CloudFeature.SYNC -> syncNow()
            CloudFeature.BACKUP -> cloudBackupNow()
        }
    }

    private suspend fun refreshCloudProblems() {
        if (!cloudRepo.isAvailable) return
        val health = healthStore.state()
        val prefs = store.cloudPrefs()
        cloudProblems = CloudHealth.problems(
            CloudHealth.Input(
                signedIn = cloudRepo.isSignedIn && prefs.username != null,
                syncRefusedAt = health.syncRefusedAt,
                syncRefusedMessage = health.syncRefusedMessage,
                backupRefusedAt = health.backupRefusedAt,
                backupRefusedMessage = health.backupRefusedMessage,
                outbox = syncStore.state().outbox.map { it.at },
                autoBackup = prefs.autoBackup,
                backupLastOkAt = health.backupLastOkAt,
                lastUploadAt = prefs.lastUploadAt,
            ),
            now = System.currentTimeMillis(),
        )
    }

    private suspend fun forgetCloudProblems() {
        healthStore.clear()
        cloudProblems = emptyList()
    }

    private suspend fun forgetSyncAccount() {
        CloudSyncWorker.cancel(app)
        syncStore.forgetAccount()
        syncUiState = SyncUiState()
    }

    /** Runs one cloud operation behind the busy state; [block] returns the success message. */
    private fun runCloud(stage: CloudStage, block: suspend () -> String?) {
        if (cloudUiState.busy || backupUiState.busy) return
        cloudUiState = cloudUiState.copy(busy = true, stage = stage, message = null, error = null)
        scope.launch {
            try {
                val message = block()
                cloudUiState = cloudUiState.copy(busy = false, stage = null, message = message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                cloudUiState = cloudUiState.copy(
                    busy = false,
                    stage = null,
                    error = (error as? BackupException)?.message ?: "تعذر إكمال العملية. أعد المحاولة بعد قليل.",
                )
            }
        }
    }

    // ── What the rest of the reader tells sync ─────────────────────────────────

    /** Loads the account and its faults; called once the view model is ready. */
    internal fun start() {
        if (cloudRepo.isAvailable) scope.launch {
            loadCloudAccount()
            refreshCloudProblems()
        }
    }

    /** The reader turned to a new page at [at]: the place sync will offer elsewhere. */
    internal fun recordPageTurn(at: Long) {
        scope.launch { syncStore.setPageAt(at) }
    }

    /** The gold bookmark moved to [verseKey] on [page] (or came off, when null). */
    internal suspend fun recordBookmark(verseKey: String?, page: Int?) {
        syncStore.recordBookmark(verseKey, page, System.currentTimeMillis())
        if (syncRepo.isSignedIn) CloudSyncWorker.enqueue(app)
    }

    /** Something sync carries changed here; send it when the network allows. */
    internal fun queueUpload() {
        if (syncRepo.isSignedIn) CloudSyncWorker.enqueue(app)
    }
}
