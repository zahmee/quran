package com.mushaf.reader.data.packs

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mushaf.reader.BuildConfig
import com.mushaf.reader.recitation.MobileData
import com.mushaf.reader.recitation.MobileDataPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * The library's view of the downloadable packs, as Compose state: the catalog, what is installed,
 * and what is downloading. The downloading itself is [PackDownloadWorker]'s, run by WorkManager.
 */
class PackDownloads(
    private val app: Application,
    private val scope: CoroutineScope,
) {
    enum class Status { QUEUED, DOWNLOADING, VERIFYING, FAILED }

    data class Progress(
        val status: Status,
        val bytes: Long,
        val total: Long,
        val error: String? = null,
        /** For a queued download: how many tries have already failed, and are waiting to be retried. */
        val failedAttempts: Int = 0,
    ) {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    enum class CatalogState { IDLE, LOADING, READY, OFFLINE }

    var catalog by mutableStateOf<PackCatalog?>(null)
        private set
    var catalogState by mutableStateOf(CatalogState.IDLE)
        private set
    var progress by mutableStateOf<Map<String, Progress>>(emptyMap())
        private set

    val installed: Map<String, InstalledPack> get() = PackStore.installed

    private val source = PackCatalogSource(app)
    private val work = WorkManager.getInstance(app)
    private var started = false
    private var lastFinished = emptySet<String>()

    /** Reads what is installed and starts following downloads; safe to call more than once. */
    fun start() {
        if (started) return
        started = true
        scope.launch { PackStore.refresh(app) }
        scope.launch {
            work.getWorkInfosByTagFlow(PackDownloadWorker.TAG).collect { infos -> onWork(infos) }
        }
    }

    /** The catalog as saved or shipped at once, then the published one if the network allows. */
    fun loadCatalog() {
        start()
        if (catalogState == CatalogState.LOADING) return
        catalogState = CatalogState.LOADING
        scope.launch {
            if (catalog == null) catalog = source.offline()
            val fresh = runCatching { source.fetch() }.getOrNull()
            if (fresh != null) catalog = fresh
            catalogState = if (fresh != null) CatalogState.READY else CatalogState.OFFLINE
        }
    }

    /** True when downloading now would use mobile data the reader has not allowed yet. */
    fun shouldAskAboutMobileData(): Boolean =
        MobileData.policy(app) == MobileDataPolicy.ASK && MobileData.isMetered(app)

    /** True when a queued download is waiting for Wi-Fi: on mobile data without leave to use it. */
    fun waitsForWifi(): Boolean =
        MobileData.policy(app) != MobileDataPolicy.ALLOW && MobileData.isMetered(app)

    /**
     * Queues [info]. [overMobileData] lets this one download use mobile data even when the
     * standing choice is to ask or to wait for Wi-Fi.
     */
    fun download(info: PackInfo, overMobileData: Boolean) {
        start()
        val anyNetwork = overMobileData || MobileData.policy(app) == MobileDataPolicy.ALLOW
        val request = OneTimeWorkRequestBuilder<PackDownloadWorker>()
            .setInputData(
                workDataOf(
                    PackDownloadWorker.KEY_ID to info.id,
                    PackDownloadWorker.KEY_URL to info.url,
                    PackDownloadWorker.KEY_SHA256 to info.sha256,
                    PackDownloadWorker.KEY_COMPRESSED to info.compressedBytes,
                    PackDownloadWorker.KEY_INSTALLED to info.installedBytes,
                    PackDownloadWorker.KEY_VERSION to info.version,
                    PackDownloadWorker.KEY_TITLE to info.title,
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (anyNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(PackDownloadWorker.TAG)
            .addTag(PackDownloadWorker.tagFor(info.id))
            .build()
        // REPLACE: a new request for the same pack (after a failure, or for an update) supersedes
        // the old one; the part already downloaded is kept and resumed.
        work.enqueueUniqueWork(PackDownloadWorker.uniqueName(info.id), ExistingWorkPolicy.REPLACE, request)
        progress = progress + (info.id to Progress(Status.QUEUED, 0, info.compressedBytes))
    }

    fun cancel(id: String) {
        work.cancelUniqueWork(PackDownloadWorker.uniqueName(id))
        progress = progress - id
        scope.launch(Dispatchers.IO) {
            PackStore.partFile(app, id).delete()
            PackStore.tempFile(app, id).delete()
        }
    }

    fun delete(id: String) {
        work.cancelUniqueWork(PackDownloadWorker.uniqueName(id))
        progress = progress - id
        scope.launch {
            withContext(Dispatchers.IO) { PackStore.delete(app, id) }
            PackStore.refresh(app)
        }
    }

    fun clearFailure(id: String) {
        if (progress[id]?.status == Status.FAILED) progress = progress - id
    }

    private fun onWork(infos: List<WorkInfo>) {
        val next = HashMap<String, Progress>()
        val finished = HashSet<String>()
        // Newest request per pack wins: an old failed run must not hide a new queued one.
        for (info in infos) {
            val id = info.tags.firstOrNull { it.startsWith("pack:") }?.removePrefix("pack:") ?: continue
            val data = info.progress
            val entry = when (info.state) {
                // A run that ends in a retry puts the work back here until its backoff is over.
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                    Progress(Status.QUEUED, 0, 0, failedAttempts = info.runAttemptCount)
                WorkInfo.State.RUNNING -> {
                    val verifying = data.getString(PackDownloadWorker.KEY_PHASE) == PackDownloadWorker.PHASE_VERIFY
                    Progress(
                        if (verifying) Status.VERIFYING else Status.DOWNLOADING,
                        data.getLong(PackDownloadWorker.KEY_BYTES, 0),
                        data.getLong(PackDownloadWorker.KEY_TOTAL, 0),
                    )
                }
                WorkInfo.State.FAILED -> Progress(
                    Status.FAILED, 0, 0,
                    info.outputData.getString(PackDownloadWorker.KEY_ERROR),
                )
                WorkInfo.State.SUCCEEDED -> {
                    finished += info.id.toString()
                    null
                }
                WorkInfo.State.CANCELLED -> null
            }
            val current = next[id]
            if (entry != null && (current == null || current.status == Status.FAILED)) next[id] = entry
        }
        progress = next
        if (finished != lastFinished) {
            lastFinished = finished
            scope.launch { PackStore.refresh(app) }
        }
    }

    companion object {
        /** The app's own version, which a pack may require at least. */
        val appVersionCode: Int get() = BuildConfig.VERSION_CODE
    }
}
