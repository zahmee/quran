package com.mushaf.reader.data.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports and imports the backup through a document the user picks themselves.
 *
 * The transport is deliberately storage-agnostic: whatever the system picker offers — internal
 * storage, an SD card, Google Drive, or any other provider — works without an account, a network
 * permission, or an OAuth client registered against the app's signing certificate.
 */
class FileBackupRepository(context: Context) {

    private val appContext = context.applicationContext
    private val local = LocalBackupData(appContext)

    /** Pre-filled name for the picker; the user stays free to rename it. */
    fun suggestedFileName(): String =
        "mushaf-backup-${SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())}.json.gz"

    suspend fun exportTo(target: Uri): BackupFileInfo = withContext(Dispatchers.IO) {
        val snapshot = local.snapshot()
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
        BackupFileInfo(fileName = displayName(target), savedAt = snapshot.createdAt, sizeBytes = bytes.size.toLong())
    }

    suspend fun importFrom(source: Uri, takeDisplaySettings: Boolean): RestoredBackup = withContext(Dispatchers.IO) {
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
        val snapshot = BackupJsonCodec.decode(bytes, local.pageCount)
        RestoredBackup(
            file = BackupFileInfo(
                fileName = displayName(source),
                savedAt = snapshot.createdAt,
                sizeBytes = bytes.size.toLong(),
            ),
            snapshot = local.mergeIn(snapshot, takeDisplaySettings),
        )
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
}
