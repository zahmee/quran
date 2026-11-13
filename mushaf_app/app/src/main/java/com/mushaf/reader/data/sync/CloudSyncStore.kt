package com.mushaf.reader.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.syncStore by preferencesDataStore(name = "cloud_sync")

/**
 * This device's side of reading sync. Device-local and never part of a backup: it describes this
 * install's clock, its unsent bookmarks and what it already uploaded.
 *
 * Times here are on THIS device's clock; [CloudSyncRepository] corrects them to the server's
 * with [State.clockOffset] when it uploads or compares.
 */
class CloudSyncStore(context: Context) {

    private val appContext = context.applicationContext

    private val keyPageAt = longPreferencesKey("page_at")
    private val keyBookmarkAt = longPreferencesKey("bookmark_at")
    private val keyBookmarkKey = stringPreferencesKey("bookmark_key")
    private val keyBookmarkPage = intPreferencesKey("bookmark_page")
    private val keyClockOffset = longPreferencesKey("clock_offset")
    private val keyLastSyncAt = longPreferencesKey("last_sync_at")
    private val keyOutbox = stringSetPreferencesKey("outbox")
    private val keyUploadedKhatmas = stringSetPreferencesKey("uploaded_khatmas")
    private val keyDismissed = stringPreferencesKey("dismissed")

    /** A bookmark set while it could not be sent yet. */
    data class PendingMark(val id: String, val verseKey: String, val page: Int, val at: Long) {
        fun encode(): String = "$id|$verseKey|$page|$at"

        companion object {
            fun decode(value: String): PendingMark? {
                val parts = value.split('|')
                if (parts.size != 4) return null
                return PendingMark(
                    id = parts[0],
                    verseKey = parts[1],
                    page = parts[2].toIntOrNull() ?: return null,
                    at = parts[3].toLongOrNull() ?: return null,
                )
            }
        }
    }

    data class State(
        val pageAt: Long,
        val bookmarkAt: Long,
        /** This device's bookmark as last recorded here; null when cleared. */
        val bookmarkKey: String?,
        val bookmarkPage: Int?,
        val clockOffset: Long,
        val lastSyncAt: Long,
        val outbox: List<PendingMark>,
        val uploadedKhatmas: Set<String>,
        val dismissedKey: String?,
    ) {
        /** This device's latest reading activity, on the server's clock. */
        val latestAt: Long get() = maxOf(pageAt, bookmarkAt).let { if (it > 0L) it + clockOffset else 0L }
    }

    suspend fun state(): State {
        val prefs = appContext.syncStore.data.first()
        return State(
            pageAt = prefs[keyPageAt] ?: 0L,
            bookmarkAt = prefs[keyBookmarkAt] ?: 0L,
            bookmarkKey = prefs[keyBookmarkKey],
            bookmarkPage = prefs[keyBookmarkPage],
            clockOffset = prefs[keyClockOffset] ?: 0L,
            lastSyncAt = prefs[keyLastSyncAt] ?: 0L,
            outbox = prefs[keyOutbox].orEmpty().mapNotNull(PendingMark::decode).sortedBy { it.at },
            uploadedKhatmas = prefs[keyUploadedKhatmas].orEmpty(),
            dismissedKey = prefs[keyDismissed],
        )
    }

    /** The reader moved to another page, or stopped reading on this one. */
    suspend fun setPageAt(at: Long) {
        appContext.syncStore.edit { it[keyPageAt] = at }
    }

    /**
     * The bookmark changed. A newly set one ([verseKey] non-null) also joins the outbox, so the
     * history keeps it even if it is replaced before the device is next online.
     */
    suspend fun recordBookmark(verseKey: String?, page: Int?, at: Long) {
        appContext.syncStore.edit { prefs ->
            prefs[keyBookmarkAt] = at
            if (verseKey != null && page != null) {
                prefs[keyBookmarkKey] = verseKey
                prefs[keyBookmarkPage] = page
            } else {
                prefs.remove(keyBookmarkKey)
                prefs.remove(keyBookmarkPage)
            }
            if (verseKey != null && page != null) {
                val pending = PendingMark(UUID.randomUUID().toString(), verseKey, page, at)
                // Weeks offline must not grow this without limit; the oldest go first.
                val kept = prefs[keyOutbox].orEmpty()
                    .mapNotNull(PendingMark::decode)
                    .sortedByDescending { it.at }
                    .take(MAX_OUTBOX - 1)
                prefs[keyOutbox] = (kept + pending).mapTo(HashSet()) { it.encode() }
            }
        }
    }

    suspend fun removeFromOutbox(ids: Set<String>) {
        if (ids.isEmpty()) return
        appContext.syncStore.edit { prefs ->
            prefs[keyOutbox] = prefs[keyOutbox].orEmpty()
                .filterNot { PendingMark.decode(it)?.id in ids }
                .toSet()
        }
    }

    suspend fun setClockOffset(offset: Long) {
        appContext.syncStore.edit { it[keyClockOffset] = offset }
    }

    suspend fun setLastSyncAt(at: Long) {
        appContext.syncStore.edit { it[keyLastSyncAt] = at }
    }

    suspend fun addUploadedKhatmas(ids: Set<String>) {
        if (ids.isEmpty()) return
        appContext.syncStore.edit { it[keyUploadedKhatmas] = it[keyUploadedKhatmas].orEmpty() + ids }
    }

    suspend fun setDismissed(key: String?) {
        appContext.syncStore.edit { prefs ->
            if (key == null) prefs.remove(keyDismissed) else prefs[keyDismissed] = key
        }
    }

    /**
     * Forgets what belongs to the account: signing out, or into another account, must not carry
     * unsent bookmarks or "already uploaded" marks across. The local reading times stay, since
     * they describe this device, not the account.
     */
    suspend fun forgetAccount() {
        appContext.syncStore.edit { prefs ->
            prefs.remove(keyOutbox)
            prefs.remove(keyUploadedKhatmas)
            prefs.remove(keyDismissed)
            prefs.remove(keyLastSyncAt)
        }
    }

    private companion object {
        const val MAX_OUTBOX = 100
    }
}
