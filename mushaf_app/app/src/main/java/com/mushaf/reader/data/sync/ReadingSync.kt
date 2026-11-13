package com.mushaf.reader.data.sync

/**
 * Reading sync between a reader's own devices: where each one stands, the bookmarks set on each,
 * and the khatmas completed on each.
 *
 * Nothing here moves the reader. A newer place on another device is only *offered*
 * ([elsewhere]); the reader decides, and going there sets no bookmark.
 *
 * Every time is epoch millis on the server's clock: each device measures how far its own clock is
 * from the server's and corrects what it uploads, so a tablet whose clock runs ten minutes slow
 * cannot pass an older place off as the newer one. Pure on purpose — no Android types — so the
 * rules are pinned by plain JVM tests.
 */

/** A page reached, or a bookmark set, on one device at one moment. */
data class PlacePoint(val page: Int, val verseKey: String?, val at: Long)

/** What one device last reported: the page it was reading and its bookmark. */
data class DevicePlace(
    val deviceId: String,
    val deviceName: String,
    val lastPage: PlacePoint?,
    /** Null when that device has no bookmark (never set, or cleared). */
    val bookmark: PlacePoint?,
    /** When the device last synced, server time; 0 when unknown. */
    val seenAt: Long = 0L,
)

/** A newer place found on another device, to offer the reader. Either part may be missing. */
data class ElsewherePlace(
    val deviceId: String,
    val deviceName: String,
    val lastPage: PlacePoint?,
    val bookmark: PlacePoint?,
) {
    /** Identifies this offer, so dismissing it keeps it dismissed until that device moves on. */
    val key: String
        get() = "$deviceId@${maxOf(lastPage?.at ?: 0L, bookmark?.at ?: 0L)}"

    val at: Long get() = maxOf(lastPage?.at ?: 0L, bookmark?.at ?: 0L)
}

/** One bookmark from the account's history. */
data class MarkRecord(
    val id: String,
    val verseKey: String,
    val page: Int,
    val at: Long,
    val deviceId: String,
    val deviceName: String,
)

/** One completed khatma from the account's history. */
data class KhatmaRecord(
    val completedAt: Long,
    val startedAt: Long,
    val durationDays: Int,
    val pagesRead: Int,
    val deviceId: String,
    val deviceName: String,
) {
    val id: String get() = khatmaId(completedAt, startedAt)
}

/** What the reader and the history tab show of reading sync. */
data class SyncUiState(
    /** A newer place on another device, offered in the reader until gone to or dismissed. */
    val elsewhere: ElsewherePlace? = null,
    val history: SyncHistory? = null,
    val loading: Boolean = false,
    /** Set only by a sync the reader asked for; background checks fail silently. */
    val error: String? = null,
    val lastSyncAt: Long = 0L,
)

/** The same khatma has the same id on every device — completed and begun at the same instants. */
fun khatmaId(completedAt: Long, startedAt: Long): String = "k-$completedAt-$startedAt"

object ReadingSync {

    /**
     * The newest place on another device worth offering, or null.
     *
     * Only what happened after this device's own latest activity ([localLatestAt]) counts: if the
     * reader has read here since, here is where they are. A page the reader is already on, or the
     * bookmark this device already holds, is no news. An offer the reader dismissed
     * ([dismissedKey]) stays dismissed until that device moves on.
     *
     * Of several devices, the one with the newest activity wins; within it, both its page and its
     * bookmark are offered when both qualify, since they may be in different places.
     */
    fun elsewhere(
        thisDeviceId: String,
        localLatestAt: Long,
        currentPage: Int,
        localBookmarkKey: String?,
        devices: List<DevicePlace>,
        dismissedKey: String?,
    ): ElsewherePlace? {
        val offers = devices
            .filter { it.deviceId != thisDeviceId }
            .mapNotNull { device ->
                val page = device.lastPage?.takeIf { it.at > localLatestAt && it.page != currentPage }
                val bookmark = device.bookmark?.takeIf {
                    it.at > localLatestAt && it.verseKey != null && it.verseKey != localBookmarkKey
                }
                if (page == null && bookmark == null) {
                    null
                } else {
                    // The same spot twice is one offer: keep the bookmark, which names the ayah.
                    val samePlace = page != null && bookmark != null && page.page == bookmark.page
                    ElsewherePlace(
                        deviceId = device.deviceId,
                        deviceName = device.deviceName,
                        lastPage = if (samePlace) null else page,
                        bookmark = bookmark,
                    )
                }
            }
        val newest = offers.maxByOrNull { it.at } ?: return null
        return newest.takeIf { it.key != dismissedKey }
    }

    const val KEEP_MARK_DAYS = 7
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * The bookmarks to erase at [now]: those older than [KEEP_MARK_DAYS], except each device's
     * newest — a device left in a drawer for a month still shows where it was left.
     */
    fun staleMarks(marks: List<MarkRecord>, now: Long): List<MarkRecord> {
        val cutoff = now - KEEP_MARK_DAYS * DAY_MS
        return marks.groupBy { it.deviceId }.values.flatMap { ofDevice ->
            ofDevice.sortedByDescending { it.at }.drop(1).filter { it.at < cutoff }
        }
    }

    /**
     * How far this device's clock is behind the server's, in millis, from one round trip: the
     * server stamped [serverAt] somewhere between [sentAt] and [receivedAt] on the local clock,
     * so the midpoint is the best guess.
     */
    fun clockOffset(sentAt: Long, receivedAt: Long, serverAt: Long): Long =
        serverAt - (sentAt + (receivedAt - sentAt) / 2)
}
