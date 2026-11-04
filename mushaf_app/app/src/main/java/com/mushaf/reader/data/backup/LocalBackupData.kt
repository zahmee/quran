package com.mushaf.reader.data.backup

import android.content.Context
import android.os.Build
import androidx.room.withTransaction
import com.mushaf.reader.data.PageRepository
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.stats.AppDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * This device's side of every backup, whatever carries it: reading the current data into a
 * snapshot, and folding a restored snapshot back in.
 *
 * The file and the cloud transports both go through here, and so does the daily background
 * upload — which is why one lock serialises them: a snapshot taken halfway through a restore
 * would pair the new database with the old preferences.
 */
class LocalBackupData(context: Context) {

    private val appContext = context.applicationContext
    private val store = ReadingStore(appContext)
    private val database = AppDatabase.get(appContext)
    val pageCount = PageRepository(appContext).pageCount().coerceAtLeast(1)

    suspend fun snapshot(): BackupSnapshot = lock.withLock { readSnapshot() }

    /** Fold [incoming] into this device's data and return what it now holds. */
    suspend fun mergeIn(incoming: BackupSnapshot, takeDisplaySettings: Boolean): BackupSnapshot =
        lock.withLock {
            val merged = BackupMerge.merge(readSnapshot(), incoming, takeDisplaySettings)
            replaceLocalData(merged)
            merged
        }

    private suspend fun readSnapshot() = BackupSnapshot(
        backupId = UUID.randomUUID().toString(),
        createdAt = System.currentTimeMillis(),
        appVersion = appVersion(),
        deviceName = deviceName(),
        pageCount = pageCount,
        reading = store.backupState(),
        sessions = database.sessionDao().allSessions(),
        khatmas = database.khatmaDao().all(),
    )

    private suspend fun replaceLocalData(snapshot: BackupSnapshot) {
        val previousReading = store.backupState()
        val previousSessions = database.sessionDao().allSessions()
        val previousKhatmas = database.khatmaDao().all()
        try {
            database.withTransaction {
                database.sessionDao().deleteAll()
                database.khatmaDao().deleteAll()
                if (snapshot.sessions.isNotEmpty()) database.sessionDao().insertAll(snapshot.sessions)
                if (snapshot.khatmas.isNotEmpty()) database.khatmaDao().insertAll(snapshot.khatmas)
            }
            store.restoreBackupState(snapshot.reading)
        } catch (error: Exception) {
            // Best-effort rollback: a failed import must not leave half of the previous state behind.
            try {
                database.withTransaction {
                    database.sessionDao().deleteAll()
                    database.khatmaDao().deleteAll()
                    if (previousSessions.isNotEmpty()) database.sessionDao().insertAll(previousSessions)
                    if (previousKhatmas.isNotEmpty()) database.khatmaDao().insertAll(previousKhatmas)
                }
                store.restoreBackupState(previousReading)
            } catch (_: Exception) {
                // Preserve the original, more useful import failure below.
            }
            throw BackupException("تعذر استعادة البيانات المحلية؛ لم تكتمل العملية.", error)
        }
    }

    fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val value = if (model.startsWith(manufacturer, ignoreCase = true)) model
        else "$manufacturer $model".trim()
        return value.ifBlank { "جهاز أندرويد" }.take(80)
    }

    @Suppress("DEPRECATION")
    private fun appVersion(): String = appContext.packageManager
        .getPackageInfo(appContext.packageName, 0)
        .versionName
        ?.take(40)
        ?: "غير معروف"

    private companion object {
        /** Process-wide: the screen and the background worker each build their own instance. */
        val lock = Mutex()
    }
}
