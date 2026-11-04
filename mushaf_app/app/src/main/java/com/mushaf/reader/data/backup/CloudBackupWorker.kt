package com.mushaf.reader.data.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mushaf.reader.data.ReadingStore
import java.util.concurrent.TimeUnit

/**
 * The daily cloud backup, run by the system while the app is closed — so the other device always
 * finds yesterday's reading waiting. A day with nothing new uploads nothing.
 */
class CloudBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cloud = CloudBackupRepository(applicationContext)
        if (!cloud.isSignedIn || !ReadingStore(applicationContext).cloudPrefs().autoBackup) {
            return Result.success()
        }
        return try {
            cloud.upload(auto = true)
            Result.success()
        } catch (_: CloudOfflineException) {
            Result.retry()
        } catch (_: BackupException) {
            // A refusal will not fix itself by retrying; tomorrow's run tries again.
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "cloud-auto-backup"

        fun schedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                // Not at once: a device that has just signed in usually came to restore, and an
                // upload of its still-empty state would only crowd the list.
                .setInitialDelay(6, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
