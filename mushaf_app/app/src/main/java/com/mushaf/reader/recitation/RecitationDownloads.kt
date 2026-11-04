package com.mushaf.reader.recitation

import android.app.Application
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.mushaf.reader.data.recitation.RecitationRepository
import com.mushaf.reader.data.recitation.Reciter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The reader's view of recitation downloads (whole surah files, one download per reciter and
 * surah) and of the audio kept from listening, as Compose state for the downloads screen.
 * The downloading itself is [RecitationDownloadService]'s.
 */
@OptIn(UnstableApi::class)
class RecitationDownloads(
    private val app: Application,
    private val scope: CoroutineScope,
    private val repo: RecitationRepository,
) {
    /** A download's state in the app's own terms, so the UI never touches the library's. */
    enum class Status { DONE, DOWNLOADING, QUEUED, RESTARTING, STOPPED, REMOVING, FAILED }

    data class Item(
        val reciterId: Int,
        val surah: Int,
        val url: String,
        val status: Status,
        /** 0..100, or negative while unknown. */
        val percent: Float,
        val bytes: Long,
    ) {
        val done: Boolean get() = status == Status.DONE
        val pending: Boolean
            get() = status == Status.QUEUED || status == Status.DOWNLOADING || status == Status.RESTARTING
        val failed: Boolean get() = status == Status.FAILED
    }

    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    /** Everything recitation keeps on the device. */
    var cacheBytes by mutableLongStateOf(0L)
        private set
    /** The part kept from listening only, not belonging to any download. */
    var autoBytes by mutableLongStateOf(0L)
        private set
    /** Downloads are queued but the network they may use is not there (usually: no Wi-Fi). */
    var waitingForNetwork by mutableStateOf(false)
        private set

    private var started = false
    private var visible = false
    private var ticker: Job? = null
    private val manager: DownloadManager get() = RecitationStorage.downloadManager(app)

    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) = refresh()

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) = refresh()

        override fun onRequirementsStateChanged(
            downloadManager: DownloadManager,
            requirements: Requirements,
            notMetRequirements: Int,
        ) = refresh()
    }

    /** The downloads screen is open or closed: follow progress only while it is open. */
    fun setVisible(open: Boolean) {
        visible = open
        if (open) ensureStarted()
        refresh()
    }

    fun download(reciter: Reciter, surahs: Iterable<Int>) {
        ensureStarted()
        val known = items.associateBy { it.reciterId to it.surah }
        val wanted = surahs.toList()
        for (surah in wanted) {
            val existing = known[reciter.id to surah]
            if (existing != null && (existing.done || existing.pending)) continue
            val request = DownloadRequest.Builder(idOf(reciter.id, surah), reciter.surahUrl(surah).toUri())
                .build()
            DownloadService.sendAddDownload(app, RecitationDownloadService::class.java, request, false)
        }
        // A downloaded surah needs its timing table too, or it cannot start at an ayah offline.
        // The tables are a few kilobytes each and are kept for good once fetched.
        scope.launch {
            for (surah in wanted) runCatching { repo.timing(reciter.id, surah) }
        }
        refresh()
    }

    fun remove(item: Item) {
        DownloadService.sendRemoveDownload(
            app, RecitationDownloadService::class.java, idOf(item.reciterId, item.surah), false
        )
    }

    fun removeReciter(reciterId: Int) = items.filter { it.reciterId == reciterId }.forEach(::remove)

    /** Tell the download manager which networks it may use after the mobile-data choice changed. */
    fun applyMobileDataPolicy() {
        DownloadService.sendSetRequirements(
            app, RecitationDownloadService::class.java, MobileData.downloadRequirements(app), false
        )
    }

    /** Delete the audio kept from listening; downloads stay. */
    fun clearAutoCache() {
        val m = manager // created on the main thread, never on IO
        scope.launch {
            withContext(Dispatchers.IO) { trimTo(m, 0L) }
            refresh()
        }
    }

    /** Keep the audio kept from listening under [RecitationStorage.AUTO_LIMIT_BYTES], dropping
     *  what was heard longest ago. Only call it while nothing is playing. */
    fun trimAutoCache() {
        val m = manager
        scope.launch(Dispatchers.IO) { trimTo(m, RecitationStorage.AUTO_LIMIT_BYTES) }
    }

    private fun trimTo(m: DownloadManager, limit: Long) {
        val cache = RecitationStorage.cache(app)
        val keep = readDownloads(m).mapTo(HashSet()) { it.url }
        val candidates = cache.keys.filter { it !in keep }.map { key ->
            val spans = cache.getCachedSpans(key)
            Triple(key, spans.sumOf { it.length }, spans.maxOfOrNull { it.lastTouchTimestamp } ?: 0L)
        }
        var total = candidates.sumOf { it.second }
        for ((key, size, _) in candidates.sortedBy { it.third }) {
            if (total <= limit) break
            runCatching { cache.removeResource(key) }
            total -= size
        }
    }

    private fun ensureStarted() {
        if (started) return
        started = true
        manager.addListener(listener)
    }

    private fun refresh() {
        scope.launch {
            val m = manager
            val notMet = m.notMetRequirements
            val (list, total) = withContext(Dispatchers.IO) {
                readDownloads(m) to RecitationStorage.cache(app).cacheSpace
            }
            items = list.sortedWith(compareBy<Item> { it.reciterId }.thenBy { it.surah })
            cacheBytes = total
            autoBytes = (total - list.sumOf { it.bytes }).coerceAtLeast(0L)
            waitingForNetwork = notMet != 0 && list.any { it.pending }
            updateTicker()
        }
    }

    /** Blocking read of the download index. */
    private fun readDownloads(m: DownloadManager): List<Item> {
        val out = ArrayList<Item>()
        m.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                val d = cursor.download
                val parts = d.request.id.split(':')
                val reciterId = parts.getOrNull(0)?.toIntOrNull() ?: continue
                val surah = parts.getOrNull(1)?.toIntOrNull() ?: continue
                val status = when (d.state) {
                    Download.STATE_COMPLETED -> Status.DONE
                    Download.STATE_DOWNLOADING -> Status.DOWNLOADING
                    Download.STATE_RESTARTING -> Status.RESTARTING
                    Download.STATE_STOPPED -> Status.STOPPED
                    Download.STATE_REMOVING -> Status.REMOVING
                    Download.STATE_FAILED -> Status.FAILED
                    else -> Status.QUEUED
                }
                out += Item(
                    reciterId, surah, d.request.uri.toString(), status, d.percentDownloaded,
                    d.bytesDownloaded,
                )
            }
        }
        return out
    }

    private fun updateTicker() {
        val follow = visible && items.any { it.pending }
        if (follow && ticker?.isActive != true) {
            ticker = scope.launch {
                while (isActive) {
                    delay(1_000)
                    refresh()
                }
            }
        } else if (!follow) {
            ticker?.cancel()
            ticker = null
        }
    }

    private fun idOf(reciterId: Int, surah: Int) = "$reciterId:$surah"
}
