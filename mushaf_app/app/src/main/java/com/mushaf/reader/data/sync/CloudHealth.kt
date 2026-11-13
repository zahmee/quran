package com.mushaf.reader.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.healthStore by preferencesDataStore(name = "cloud_health")

/** The cloud features that work in the background and can break without anyone watching. */
enum class CloudFeature { SYNC, BACKUP }

/** Something the reader must be told about, until it is fixed. */
data class CloudProblem(
    val feature: CloudFeature,
    val kind: Kind,
    /** When it started; for [Kind.Stalled] the oldest thing still waiting. */
    val since: Long,
    /** The server's refusal in the reader's words, for [Kind.Refused]. */
    val message: String? = null,
    /** How many bookmarks are waiting, for a stalled sync. */
    val waiting: Int = 0,
) {
    enum class Kind {
        /** The server said no — wrong rules, a lost sign-in, a backup too large. Never fixes itself. */
        Refused,
        /** No connection for so long that the reader should check it. */
        Stalled,
    }
}

/**
 * When a cloud failure is worth an alert.
 *
 * Being offline is normal and passes, so it stays quiet — until it has gone on long enough that
 * something the reader cares about is stuck. A refusal is never normal: the background jobs used
 * to treat it like an outage and fail quietly, which once left reading sync refused by the server
 * for every write with nothing on screen. Pure, so the thresholds are pinned by JVM tests.
 */
object CloudHealth {

    private const val HOUR_MS = 60L * 60 * 1000

    /** Bookmarks unsent this long mean the other device is no longer told where the reader is. */
    const val SYNC_STALL_MS = 24 * HOUR_MS

    /** The daily backup missing this long leaves the reader with an old copy and no warning. */
    const val BACKUP_STALL_MS = 3 * 24 * HOUR_MS

    data class Input(
        val signedIn: Boolean,
        val syncRefusedAt: Long,
        val syncRefusedMessage: String?,
        val backupRefusedAt: Long,
        val backupRefusedMessage: String?,
        /** Times the unsent bookmarks were set, on this device's clock. */
        val outbox: List<Long>,
        val autoBackup: Boolean,
        /** The last time the backup job finished without a problem, uploading or not. */
        val backupLastOkAt: Long,
        /** The last upload; stands in for [backupLastOkAt] on installs from before it existed. */
        val lastUploadAt: Long,
    )

    fun problems(input: Input, now: Long): List<CloudProblem> {
        if (!input.signedIn) return emptyList()
        val problems = mutableListOf<CloudProblem>()

        if (input.syncRefusedAt > 0L) {
            problems += CloudProblem(CloudFeature.SYNC, CloudProblem.Kind.Refused, input.syncRefusedAt, input.syncRefusedMessage)
        } else {
            val oldest = input.outbox.minOrNull()
            if (oldest != null && now - oldest >= SYNC_STALL_MS) {
                problems += CloudProblem(
                    CloudFeature.SYNC,
                    CloudProblem.Kind.Stalled,
                    since = oldest,
                    waiting = input.outbox.size,
                )
            }
        }

        if (input.backupRefusedAt > 0L) {
            problems += CloudProblem(CloudFeature.BACKUP, CloudProblem.Kind.Refused, input.backupRefusedAt, input.backupRefusedMessage)
        } else if (input.autoBackup) {
            val lastOk = maxOf(input.backupLastOkAt, input.lastUploadAt)
            if (lastOk > 0L && now - lastOk >= BACKUP_STALL_MS) {
                problems += CloudProblem(CloudFeature.BACKUP, CloudProblem.Kind.Stalled, since = lastOk)
            }
        }
        return problems
    }
}

/** Where the background jobs and the screens leave word of how the cloud features are doing. */
class CloudHealthStore(context: Context) {

    private val appContext = context.applicationContext

    private fun refusedAt(feature: CloudFeature) = longPreferencesKey("${feature.name.lowercase()}_refused_at")
    private fun refusedMessage(feature: CloudFeature) = stringPreferencesKey("${feature.name.lowercase()}_refused_message")
    private val keyBackupLastOkAt = longPreferencesKey("backup_last_ok_at")

    data class State(
        val syncRefusedAt: Long,
        val syncRefusedMessage: String?,
        val backupRefusedAt: Long,
        val backupRefusedMessage: String?,
        val backupLastOkAt: Long,
    )

    suspend fun state(): State {
        val prefs = appContext.healthStore.data.first()
        return State(
            syncRefusedAt = prefs[refusedAt(CloudFeature.SYNC)] ?: 0L,
            syncRefusedMessage = prefs[refusedMessage(CloudFeature.SYNC)],
            backupRefusedAt = prefs[refusedAt(CloudFeature.BACKUP)] ?: 0L,
            backupRefusedMessage = prefs[refusedMessage(CloudFeature.BACKUP)],
            backupLastOkAt = prefs[keyBackupLastOkAt] ?: 0L,
        )
    }

    /** It worked: any refusal is over. */
    suspend fun recordSuccess(feature: CloudFeature) {
        appContext.healthStore.edit { prefs ->
            prefs.remove(refusedAt(feature))
            prefs.remove(refusedMessage(feature))
            if (feature == CloudFeature.BACKUP) prefs[keyBackupLastOkAt] = System.currentTimeMillis()
        }
    }

    /** The server refused. The first refusal's time is kept, so the alert says how long it has lasted. */
    suspend fun recordRefused(feature: CloudFeature, message: String) {
        appContext.healthStore.edit { prefs ->
            if ((prefs[refusedAt(feature)] ?: 0L) <= 0L) prefs[refusedAt(feature)] = System.currentTimeMillis()
            prefs[refusedMessage(feature)] = message
        }
    }

    /** Signing out or deleting the account ends whatever was wrong with it. */
    suspend fun clear() {
        appContext.healthStore.edit { it.clear() }
    }
}
