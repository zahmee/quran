package com.mushaf.reader.data.backup

/**
 * Which copies stay in the reader's account: those of the last [KEEP_DAYS] days.
 *
 * A device's newest copy stays whatever its age. An idle device uploads nothing, and a lost one
 * cannot, so without this a tablet still uploading daily would erase the only copy of the phone
 * the reader is about to replace.
 */
internal object CloudBackupRetention {
    const val KEEP_DAYS = 7

    /** Uploading by hand has no daily limit, so a busy week must still not fill the account. */
    const val MAX_PER_DEVICE = 30

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** The copies to erase at [now]. */
    fun stale(backups: List<CloudBackupInfo>, now: Long): List<CloudBackupInfo> {
        val cutoff = now - KEEP_DAYS * DAY_MS
        return backups.groupBy { it.deviceId }.values.flatMap { ofDevice ->
            ofDevice.sortedByDescending { it.createdAt }
                .filterIndexed { index, backup ->
                    index > 0 && (backup.createdAt < cutoff || index >= MAX_PER_DEVICE)
                }
        }
    }

    /** The one copy the reader cannot delete by hand: the account's newest. */
    fun newest(backups: List<CloudBackupInfo>): CloudBackupInfo? = backups.maxByOrNull { it.createdAt }
}
