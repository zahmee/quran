package com.mushaf.reader.data.backup

import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.stats.KhatmaEntity
import com.mushaf.reader.data.stats.SessionEntity

const val BACKUP_SCHEMA_VERSION = 1

data class BackupSnapshot(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val backupId: String,
    val createdAt: Long,
    val appVersion: String,
    val deviceName: String,
    val pageCount: Int,
    val reading: ReadingStore.BackupState,
    val sessions: List<SessionEntity>,
    val khatmas: List<KhatmaEntity>,
)

/** The backup file the user saved to, or picked from, a location of their own choosing. */
data class BackupFileInfo(
    val fileName: String,
    val savedAt: Long,
    val sizeBytes: Long,
)

data class RestoredBackup(
    val file: BackupFileInfo,
    val snapshot: BackupSnapshot,
)

enum class BackupStage {
    Exporting,
    Importing,
}

data class BackupUiState(
    val lastBackup: BackupFileInfo? = null,
    val busy: Boolean = false,
    val stage: BackupStage? = null,
    val message: String? = null,
    val error: String? = null,
)

open class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The cloud could not be reached: worth retrying later, unlike a refusal. */
class CloudOfflineException(cause: Throwable? = null) :
    BackupException("تعذر الاتصال بالخادم. تأكد من اتصالك بالإنترنت ثم أعد المحاولة.", cause)

/** One backup stored in the reader's cloud account, as listed without downloading it. */
data class CloudBackupInfo(
    val id: String,
    val createdAt: Long,
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val sizeBytes: Long,
    val auto: Boolean,
    val fromThisDevice: Boolean,
)

enum class CloudStage {
    SigningIn,
    SigningUp,
    Loading,
    Uploading,
    Restoring,
    Deleting,
}

data class CloudUiState(
    /** False when the build carries no Firebase project; the cloud section is then hidden. */
    val available: Boolean = false,
    /** The signed-in account's name, or null when signed out. */
    val username: String? = null,
    val autoBackup: Boolean = false,
    val lastUploadAt: Long = 0L,
    val backups: List<CloudBackupInfo> = emptyList(),
    val listLoaded: Boolean = false,
    val busy: Boolean = false,
    val stage: CloudStage? = null,
    val message: String? = null,
    val error: String? = null,
)
