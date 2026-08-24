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

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
