package com.mushaf.reader.data.packs

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mushaf.reader.BuildConfig
import com.mushaf.reader.R
import com.mushaf.reader.data.recitation.formatByteSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Downloads one pack: the compressed file, resumed if a part is already there, then its checksum,
 * unpacking, a look inside, and only then the swap into place. An older version of the pack stays
 * readable until the new one has passed every check.
 */
class PackDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext
        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val sha256 = inputData.getString(KEY_SHA256) ?: return@withContext Result.failure()
        val compressed = inputData.getLong(KEY_COMPRESSED, -1L)
        val installed = inputData.getLong(KEY_INSTALLED, -1L)
        val version = inputData.getInt(KEY_VERSION, -1)
        val title = inputData.getString(KEY_TITLE).orEmpty()
        if (compressed <= 0 || installed <= 0 || version < 0) return@withContext Result.failure()

        val part = PackStore.partFile(app, id)
        val unpacked = PackStore.tempFile(app, id)
        // A pack of a few megabytes fits in a normal worker's time; the notification is a courtesy
        // and Android may refuse it when the app is in the background.
        runCatching { setForeground(foregroundInfo(id, title, 0L, compressed)) }
        try {
            val needed = (compressed - part.length().coerceAtMost(compressed)) + installed + SPACE_MARGIN
            if (PackStore.dir(app).usableSpace < needed) {
                return@withContext Result.failure(workDataOf(KEY_ERROR to ERROR_SPACE))
            }
            var lastReport = 0L
            PackTransfer.download(
                url = url,
                part = part,
                expectedBytes = compressed,
                allowCleartext = BuildConfig.DEBUG,
                shouldStop = { isStopped },
            ) { done ->
                val now = System.currentTimeMillis()
                if (now - lastReport >= REPORT_EVERY_MS || done == compressed) {
                    lastReport = now
                    setProgressAsync(workDataOf(KEY_BYTES to done, KEY_TOTAL to compressed, KEY_PHASE to PHASE_DOWNLOAD))
                    runCatching { setForegroundAsync(foregroundInfo(id, title, done, compressed)) }
                }
            }
            setProgress(workDataOf(KEY_BYTES to compressed, KEY_TOTAL to compressed, KEY_PHASE to PHASE_VERIFY))
            if (!PackTransfer.sha256(part).equals(sha256, ignoreCase = true)) {
                part.delete()
                throw PackIntegrityException("Checksum mismatch")
            }
            PackTransfer.gunzip(part, unpacked, limitBytes = installed + installed / 10)
            PackStore.checkUnpacked(unpacked, id, version)
            PackStore.install(app, id, unpacked)
            part.delete()
            PackStore.refresh(app)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: PackIntegrityException) {
            part.delete()
            Result.failure(workDataOf(KEY_ERROR to ERROR_CORRUPT))
        } catch (_: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to ERROR_NETWORK))
        } finally {
            unpacked.delete()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = inputData.getString(KEY_TITLE).orEmpty()
        return foregroundInfo(inputData.getString(KEY_ID).orEmpty(), title, 0L, 0L)
    }

    private fun foregroundInfo(id: String, title: String, done: Long, total: Long): ForegroundInfo {
        ensureChannel(applicationContext)
        val percent = if (total > 0) (done * 100 / total).toInt() else 0
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("تنزيل $title")
            .setContentText(
                if (total > 0) "${formatByteSize(done)} من ${formatByteSize(total)}" else "يبدأ التنزيل…"
            )
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, "إلغاء", WorkManager.getInstance(applicationContext).createCancelPendingIntent(getId()))
            .build()
        val notificationId = NOTIFICATION_BASE + (id.hashCode() and 0x3FF)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val TAG = "content-pack"
        const val KEY_ID = "id"
        const val KEY_URL = "url"
        const val KEY_SHA256 = "sha256"
        const val KEY_COMPRESSED = "compressed"
        const val KEY_INSTALLED = "installed"
        const val KEY_VERSION = "version"
        const val KEY_TITLE = "title"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_PHASE = "phase"
        const val KEY_ERROR = "error"
        const val PHASE_DOWNLOAD = "download"
        const val PHASE_VERIFY = "verify"
        const val ERROR_SPACE = "space"
        const val ERROR_CORRUPT = "corrupt"
        const val ERROR_NETWORK = "network"

        private const val CHANNEL_ID = "content_downloads"
        private const val NOTIFICATION_BASE = 3000
        private const val REPORT_EVERY_MS = 500L
        private const val MAX_ATTEMPTS = 5
        private const val SPACE_MARGIN = 5L * 1024 * 1024

        fun uniqueName(id: String) = "pack-$id"
        fun tagFor(id: String) = "pack:$id"

        private fun ensureChannel(context: Context) {
            NotificationManagerCompat.from(context).createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("تنزيل التفاسير")
                    .setDescription("تقدّم تنزيل التفاسير والكتب الإضافية")
                    .build()
            )
        }
    }
}
