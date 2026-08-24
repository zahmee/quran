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
    val pageCount: Int,
    val reading: ReadingStore.BackupState,
    val sessions: List<SessionEntity>,
    val khatmas: List<KhatmaEntity>,
)

data class RemoteBackupInfo(
    val fileId: String,
    val name: String,
    val backupId: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val sizeBytes: Long,
    val sha256: String,
    val deviceName: String,
    val appVersion: String,
)

data class RestoredBackup(
    val remote: RemoteBackupInfo,
    val snapshot: BackupSnapshot,
)

enum class DriveBackupStage {
    Authorizing,
    Loading,
    Uploading,
    Restoring,
}

data class DriveBackupUiState(
    val accountEmail: String? = null,
    val latestBackup: RemoteBackupInfo? = null,
    val remoteChecked: Boolean = false,
    val busy: Boolean = false,
    val stage: DriveBackupStage? = null,
    val message: String? = null,
    val error: String? = null,
)

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
