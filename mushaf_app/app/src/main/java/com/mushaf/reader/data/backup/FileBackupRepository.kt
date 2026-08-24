package com.mushaf.reader.data.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.mushaf.reader.data.PageRepository
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.stats.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Exports and imports the backup through a document the user picks themselves.
 *
 * The transport is deliberately storage-agnostic: whatever the system picker offers — internal
 * storage, an SD card, Google Drive, or any other provider — works without an account, a network
 * permission, or an OAuth client registered against the app's signing certificate.
 */
class FileBackupRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = ReadingStore(appContext)
    private val database = AppDatabase.get(appContext)
    private val pageCount = PageRepository(appContext).pageCount().coerceAtLeast(1)

    /** Pre-filled name for the picker; the user stays free to rename it. */
    fun suggestedFileName(): String =
        "mushaf-backup-${SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())}.json.gz"

    suspend fun exportTo(target: Uri): BackupFileInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val snapshot = BackupSnapshot(
            backupId = UUID.randomUUID().toString(),
            createdAt = now,
            appVersion = appVersion(),
            deviceName = deviceName(),
            pageCount = pageCount,
            reading = store.backupState(),
            sessions = database.sessionDao().allSessions(),
            khatmas = database.khatmaDao().all(),
        )
        val bytes = BackupJsonCodec.encode(snapshot)
        try {
            // "wt" truncates first, so overwriting a larger older backup leaves no stale tail.
            appContext.contentResolver.openOutputStream(target, "wt").use { output ->
                output ?: throw BackupException("تعذر فتح الملف المحدد للكتابة.")
                output.write(bytes)
                output.flush()
            }
        } catch (known: BackupException) {
            throw known
        } catch (error: Exception) {
            throw BackupException("تعذر حفظ النسخة في الموقع المحدد. اختر موقعاً آخر ثم أعد المحاولة.", error)
        }
        BackupFileInfo(fileName = displayName(target), savedAt = now, sizeBytes = bytes.size.toLong())
    }

    suspend fun importFrom(source: Uri): RestoredBackup = withContext(Dispatchers.IO) {
        val bytes = try {
            appContext.contentResolver.openInputStream(source).use { input ->
                input ?: throw BackupException("تعذر فتح الملف المحدد للقراءة.")
                readLimited(input, BackupJsonCodec.MAX_COMPRESSED_BYTES)
            }
        } catch (known: BackupException) {
            throw known
        } catch (error: Exception) {
            throw BackupException("تعذر قراءة الملف المحدد. تأكد أنه ملف نسخة احتياطية من هذا التطبيق.", error)
        }

        // gzip carries its own CRC32 and the decoder re-validates every field against the installed
        // mushaf, so a truncated, foreign, or tampered file is rejected before any local write.
        val snapshot = BackupJsonCodec.decode(bytes, pageCount)
        replaceLocalData(snapshot)
        RestoredBackup(
            file = BackupFileInfo(
                fileName = displayName(source),
                savedAt = snapshot.createdAt,
                sizeBytes = bytes.size.toLong(),
            ),
            snapshot = snapshot,
        )
    }

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

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw BackupException("حجم الملف المحدد أكبر من الحد الآمن للنسخ الاحتياطي.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    /** Human-readable name of a document uri, for showing the user which file was used. */
    private fun displayName(uri: Uri): String {
        val fromProvider = try {
            appContext.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (_: Exception) {
            null
        }
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "نسخة احتياطية"
    }

    private fun deviceName(): String {
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
}
