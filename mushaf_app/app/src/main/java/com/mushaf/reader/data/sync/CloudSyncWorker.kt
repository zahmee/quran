package com.mushaf.reader.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mushaf.reader.data.backup.BackupException
import com.mushaf.reader.data.backup.CloudOfflineException
import java.util.concurrent.TimeUnit

/**
 * Sends this device's reading place, bookmarks and khatmas as soon as there is a connection.
 *
 * Queued on every change, with the app open or closed. Without a connection it simply waits — the
 * system starts it once the device is back online — so a bookmark set on a plane reaches the
 * other device after landing, not never.
 */
class CloudSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sync = CloudSyncRepository(applicationContext)
        if (!sync.isSignedIn) return Result.success()
        return try {
            sync.push()
            Result.success()
        } catch (_: CloudOfflineException) {
            Result.retry()
        } catch (_: BackupException) {
            // A refusal (signed out elsewhere, rules) will not fix itself; the next change retries.
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "cloud-reading-sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // Appended, not replaced: a run already under way may have read the state before this
            // change, so another run after it picks the change up.
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }
    }
}
