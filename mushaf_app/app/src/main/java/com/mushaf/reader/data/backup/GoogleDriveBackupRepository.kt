package com.mushaf.reader.data.backup

import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.mushaf.reader.data.PageRepository
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.stats.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Google Drive appDataFolder transport plus safe logical export/import of user-owned data. */
class GoogleDriveBackupRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = ReadingStore(appContext)
    private val database = AppDatabase.get(appContext)
    private val pageCount = PageRepository(appContext).pageCount().coerceAtLeast(1)

    suspend fun latestBackup(accessToken: String): RemoteBackupInfo? = withContext(Dispatchers.IO) {
        listLatest(accessToken)
    }

    suspend fun createBackup(accessToken: String): RemoteBackupInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val snapshot = BackupSnapshot(
            backupId = UUID.randomUUID().toString(),
            createdAt = now,
            appVersion = appVersion(),
            pageCount = pageCount,
            reading = store.backupState(),
            sessions = database.sessionDao().allSessions(),
            khatmas = database.khatmaDao().all(),
        )
        val bytes = BackupJsonCodec.encode(snapshot)
        upload(accessToken, snapshot, bytes)
    }

    suspend fun restoreLatest(accessToken: String): RestoredBackup = withContext(Dispatchers.IO) {
        val remote = listLatest(accessToken)
            ?: throw BackupException("لا توجد نسخة احتياطية في حساب Google Drive المحدد.")
        if (remote.sizeBytes <= 0L || remote.sizeBytes > BackupJsonCodec.MAX_COMPRESSED_BYTES) {
            throw BackupException("حجم النسخة الموجودة على Google Drive غير صالح.")
        }

        val bytes = download(accessToken, remote)
        val actualChecksum = sha256(bytes)
        if (!actualChecksum.equals(remote.sha256, ignoreCase = true)) {
            throw BackupException("فشل التحقق من سلامة النسخة؛ لم تُستبدل أي بيانات محلية.")
        }
        val snapshot = BackupJsonCodec.decode(bytes, pageCount)
        if (snapshot.backupId != remote.backupId) {
            throw BackupException("معرّف الملف لا يطابق بيانات النسخة الاحتياطية.")
        }

        replaceLocalData(snapshot)
        RestoredBackup(remote, snapshot)
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

    private fun listLatest(accessToken: String): RemoteBackupInfo? {
        val query = "'appDataFolder' in parents and appProperties has " +
            "{ key='backupType' and value='$BACKUP_TYPE' }"
        val url = FILES_ENDPOINT.toUri().buildUpon()
            .appendQueryParameter("spaces", "appDataFolder")
            .appendQueryParameter("q", query)
            .appendQueryParameter("orderBy", "modifiedTime desc")
            .appendQueryParameter("pageSize", "1")
            .appendQueryParameter("fields", "files($FILE_FIELDS)")
            .build()
            .toString()
        val response = request(accessToken, url, "GET", MAX_METADATA_BYTES)
        val files = JSONObject(response.toString(Charsets.UTF_8)).getJSONArray("files")
        return if (files.length() == 0) null else parseRemoteInfo(files.getJSONObject(0))
    }

    private fun upload(
        accessToken: String,
        snapshot: BackupSnapshot,
        bytes: ByteArray,
    ): RemoteBackupInfo {
        val checksum = sha256(bytes)
        val deviceName = deviceName()
        val metadata = JSONObject()
            .put("name", "quran-reader-backup-${snapshot.createdAt}-${snapshot.backupId.take(8)}.json.gz")
            .put("mimeType", BACKUP_MIME_TYPE)
            .put("parents", JSONArray().put("appDataFolder"))
            .put(
                "appProperties",
                JSONObject()
                    .put("backupType", BACKUP_TYPE)
                    .put("schemaVersion", snapshot.schemaVersion.toString())
                    .put("backupId", snapshot.backupId)
                    .put("sha256", checksum)
                    .put("deviceName", deviceName)
                    .put("appVersion", snapshot.appVersion)
            )

        val boundary = "mushaf-${UUID.randomUUID()}"
        val url = UPLOAD_ENDPOINT.toUri().buildUpon()
            .appendQueryParameter("uploadType", "multipart")
            .appendQueryParameter("fields", FILE_FIELDS)
            .build()
            .toString()
        val connection = openConnection(accessToken, url, "POST").apply {
            doOutput = true
            setChunkedStreamingMode(0)
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }

        try {
            DataOutputStream(connection.outputStream).use { output ->
                output.writeUtf8("--$boundary\r\n")
                output.writeUtf8("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                output.writeUtf8(metadata.toString())
                output.writeUtf8("\r\n--$boundary\r\n")
                output.writeUtf8("Content-Type: $BACKUP_MIME_TYPE\r\n\r\n")
                output.write(bytes)
                output.writeUtf8("\r\n--$boundary--\r\n")
            }
            val response = responseBytes(connection, MAX_METADATA_BYTES)
            return parseRemoteInfo(JSONObject(response.toString(Charsets.UTF_8))).copy(
                sha256 = checksum,
                deviceName = deviceName,
                appVersion = snapshot.appVersion,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun download(accessToken: String, remote: RemoteBackupInfo): ByteArray {
        val url = FILES_ENDPOINT.toUri().buildUpon()
            .appendPath(remote.fileId)
            .appendQueryParameter("alt", "media")
            .build()
            .toString()
        return request(accessToken, url, "GET", BackupJsonCodec.MAX_COMPRESSED_BYTES)
    }

    private fun request(
        accessToken: String,
        url: String,
        method: String,
        maxBytes: Int,
    ): ByteArray {
        val connection = openConnection(accessToken, url, method)
        return try {
            responseBytes(connection, maxBytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(accessToken: String, url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }

    private fun responseBytes(connection: HttpURLConnection, maxBytes: Int): ByteArray {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val bytes = stream?.use { readLimited(it, maxBytes) } ?: ByteArray(0)
        if (code !in 200..299) throw driveError(code)
        return bytes
    }

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw BackupException("استجابة Google Drive أكبر من الحد الآمن.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun parseRemoteInfo(value: JSONObject): RemoteBackupInfo {
        val properties = value.optJSONObject("appProperties")
            ?: throw BackupException("بيانات تعريف النسخة على Google Drive غير مكتملة.")
        val backupId = properties.optString("backupId")
        val checksum = properties.optString("sha256")
        if (backupId.isBlank() || checksum.length != 64) {
            throw BackupException("بيانات التحقق من النسخة على Google Drive غير مكتملة.")
        }
        return RemoteBackupInfo(
            fileId = value.getString("id"),
            name = value.optString("name", "نسخة احتياطية"),
            backupId = backupId,
            createdAt = parseDriveTime(value.optString("createdTime")),
            modifiedAt = parseDriveTime(value.optString("modifiedTime")),
            sizeBytes = value.optString("size", "0").toLongOrNull() ?: 0L,
            sha256 = checksum,
            deviceName = properties.optString("deviceName", "جهاز غير معروف").take(80),
            appVersion = properties.optString("appVersion", "غير معروف").take(40),
        )
    }

    private fun parseDriveTime(value: String): Long {
        if (value.isBlank()) return 0L
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")
        for (pattern in patterns) {
            try {
                return SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(value)?.time ?: continue
            } catch (_: Exception) {
                // Try the next valid Drive timestamp shape.
            }
        }
        return 0L
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

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }

    private fun driveError(code: Int): BackupException = when (code) {
        401 -> BackupException("انتهت صلاحية الوصول إلى Google Drive. أعد المحاولة للاتصال بالحساب.")
        403 -> BackupException("تعذر الوصول إلى Google Drive. تحقق من تفعيل Drive API وصلاحية التطبيق.")
        404 -> BackupException("لم تعد النسخة الاحتياطية موجودة على Google Drive.")
        429 -> BackupException("Google Drive مشغول حالياً. انتظر قليلاً ثم أعد المحاولة.")
        in 500..599 -> BackupException("خدمة Google Drive غير متاحة مؤقتاً. حاول لاحقاً.")
        else -> BackupException("فشل طلب Google Drive (رمز $code).")
    }

    private fun DataOutputStream.writeUtf8(value: String) {
        write(value.toByteArray(Charsets.UTF_8))
    }

    private companion object {
        const val FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"
        const val BACKUP_MIME_TYPE = "application/gzip"
        const val BACKUP_TYPE = "quran-reader-v1"
        const val MAX_METADATA_BYTES = 1024 * 1024
        const val FILE_FIELDS =
            "id,name,createdTime,modifiedTime,size,appProperties"
    }
}
