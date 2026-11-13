package com.mushaf.reader.data.sync

import android.content.Context
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.data.backup.BackupMerge
import com.mushaf.reader.data.backup.CloudBackupRepository
import com.mushaf.reader.data.backup.LocalBackupData
import com.mushaf.reader.data.backup.await
import com.mushaf.reader.data.stats.AppDatabase
import com.mushaf.reader.data.stats.KhatmaEntity

/** What one sync found: every device's place, and how many khatmas arrived from elsewhere. */
data class SyncResult(
    val thisDeviceId: String,
    val devices: List<DevicePlace>,
    val khatmasAdded: Int,
    val syncedAt: Long,
)

/** The account's history, for the history tab. */
data class SyncHistory(
    val thisDeviceId: String,
    val devices: List<DevicePlace>,
    val marks: List<MarkRecord>,
    val khatmas: List<KhatmaRecord>,
)

/**
 * Sends this device's reading place, bookmarks and khatmas to the reader's account, and reads
 * the other devices' back. See [ReadingSync] for the rules; this class only moves data.
 *
 * Sign-in, time limits and error messages are [CloudBackupRepository]'s, so the two features
 * fail the same way and in the same words.
 */
class CloudSyncRepository(context: Context) {

    private val appContext = context.applicationContext
    private val cloud = CloudBackupRepository(appContext)
    private val reading = ReadingStore(appContext)
    private val sync = CloudSyncStore(appContext)
    private val khatmaDao = AppDatabase.get(appContext).khatmaDao()
    private val deviceName: String by lazy { LocalBackupData(appContext).deviceName() }

    val isSignedIn: Boolean get() = cloud.isSignedIn

    /**
     * Uploads what this device has to say: its place, its unsent bookmarks and its khatmas.
     * Run by [CloudSyncWorker] whenever something changes, so it also works with the app closed.
     */
    suspend fun push() {
        cloud.remote {
            val userDoc = cloud.userDoc(cloud.requireUser())
            val deviceId = reading.cloudDeviceId()
            measureClock(userDoc, deviceId)
            pushPlace(userDoc, deviceId)
            pushOutbox(userDoc, deviceId)
            pushKhatmas(userDoc, deviceId)
        }
    }

    /** [push], then reads every device's place and takes in khatmas completed elsewhere. */
    suspend fun sync(): SyncResult = cloud.remote {
        val userDoc = cloud.userDoc(cloud.requireUser())
        val deviceId = reading.cloudDeviceId()
        measureClock(userDoc, deviceId)
        pushPlace(userDoc, deviceId)
        pushOutbox(userDoc, deviceId)
        pushKhatmas(userDoc, deviceId)
        val added = pullKhatmas(userDoc)
        val devices = fetchDevices(userDoc)
        val now = System.currentTimeMillis()
        sync.setLastSyncAt(now)
        SyncResult(thisDeviceId = deviceId, devices = devices, khatmasAdded = added, syncedAt = now)
    }

    /** The account's history; opening it is also when week-old bookmarks are cleared out. */
    suspend fun history(): SyncHistory = cloud.remote {
        val userDoc = cloud.userDoc(cloud.requireUser())
        val deviceId = reading.cloudDeviceId()
        val marks = userDoc.collection(CloudBackupRepository.MARKS).get(Source.SERVER).await()
            .documents.mapNotNull(::markRecord)
        val stale = ReadingSync.staleMarks(marks, System.currentTimeMillis() + sync.state().clockOffset)
        if (stale.isNotEmpty()) {
            try {
                stale.chunked(400).forEach { chunk ->
                    val batch = CloudBackupRepository.firestore.batch()
                    chunk.forEach { batch.delete(userDoc.collection(CloudBackupRepository.MARKS).document(it.id)) }
                    batch.commit().await()
                }
            } catch (_: FirebaseFirestoreException) {
                // Kept a little longer; the next opening tries again.
            }
        }
        val staleIds = stale.mapTo(HashSet()) { it.id }
        SyncHistory(
            thisDeviceId = deviceId,
            devices = fetchDevices(userDoc),
            marks = marks.filterNot { it.id in staleIds }.sortedByDescending { it.at },
            khatmas = userDoc.collection(CloudBackupRepository.KHATMAS).get(Source.SERVER).await()
                .documents.mapNotNull(::khatmaRecord).sortedByDescending { it.completedAt },
        )
    }

    /**
     * Stamps this device's document with the server's time and compares it with the local clock.
     * Every time this device uploads is then corrected by the difference.
     */
    private suspend fun measureClock(userDoc: DocumentReference, deviceId: String) {
        val doc = userDoc.collection(CloudBackupRepository.DEVICES).document(deviceId)
        val sentAt = System.currentTimeMillis()
        doc.set(
            mapOf("deviceName" to deviceName, "seenAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
        val receivedAt = System.currentTimeMillis()
        val serverAt = doc.get(Source.SERVER).await().getTimestamp("seenAt")?.toDate()?.time ?: return
        sync.setClockOffset(ReadingSync.clockOffset(sentAt, receivedAt, serverAt))
    }

    private suspend fun pushPlace(userDoc: DocumentReference, deviceId: String) {
        val state = sync.state()
        val settings = reading.settings()
        val fields = mutableMapOf<String, Any?>(
            "deviceName" to deviceName,
            "seenAt" to FieldValue.serverTimestamp(),
        )
        if (state.pageAt > 0L) {
            fields["page"] = settings.lastPage
            fields["pageAt"] = state.pageAt + state.clockOffset
        }
        if (state.bookmarkAt > 0L) {
            // Read from the sync record, not the live bookmark: the two are written together there.
            fields["bookmarkKey"] = state.bookmarkKey
            fields["bookmarkPage"] = state.bookmarkPage
            fields["bookmarkAt"] = state.bookmarkAt + state.clockOffset
        }
        userDoc.collection(CloudBackupRepository.DEVICES).document(deviceId).set(fields, SetOptions.merge()).await()
    }

    private suspend fun pushOutbox(userDoc: DocumentReference, deviceId: String) {
        val state = sync.state()
        if (state.outbox.isEmpty()) return
        val batch = CloudBackupRepository.firestore.batch()
        state.outbox.forEach { mark ->
            batch.set(
                userDoc.collection(CloudBackupRepository.MARKS).document(mark.id),
                mapOf(
                    "verseKey" to mark.verseKey,
                    "page" to mark.page,
                    "at" to mark.at + state.clockOffset,
                    "deviceId" to deviceId,
                    "deviceName" to deviceName,
                ),
            )
        }
        batch.commit().await()
        sync.removeFromOutbox(state.outbox.mapTo(HashSet()) { it.id })
    }

    private suspend fun pushKhatmas(userDoc: DocumentReference, deviceId: String) {
        val uploaded = sync.state().uploadedKhatmas
        val fresh = khatmaDao.all().filter { khatmaId(it.completedAt, it.startedAt) !in uploaded }
        if (fresh.isEmpty()) return
        val batch = CloudBackupRepository.firestore.batch()
        fresh.forEach { khatma ->
            batch.set(
                userDoc.collection(CloudBackupRepository.KHATMAS).document(khatmaId(khatma.completedAt, khatma.startedAt)),
                mapOf(
                    "completedAt" to khatma.completedAt,
                    "startedAt" to khatma.startedAt,
                    "durationDays" to khatma.durationDays,
                    "pagesRead" to khatma.pagesRead,
                    "deviceId" to deviceId,
                    "deviceName" to deviceName,
                ),
            )
        }
        batch.commit().await()
        sync.addUploadedKhatmas(fresh.mapTo(HashSet()) { khatmaId(it.completedAt, it.startedAt) })
    }

    /** Adds khatmas completed on other devices to this one's archive; returns how many. */
    private suspend fun pullKhatmas(userDoc: DocumentReference): Int {
        val remote = userDoc.collection(CloudBackupRepository.KHATMAS).get(Source.SERVER).await()
            .documents.mapNotNull(::khatmaRecord)
        val local = khatmaDao.all()
        val incoming = remote.map {
            KhatmaEntity(
                completedAt = it.completedAt,
                startedAt = it.startedAt,
                durationDays = it.durationDays,
                pagesRead = it.pagesRead,
            )
        }
        val merged = BackupMerge.mergeKhatmas(local, incoming)
        val added = merged.filter { it.id == 0L }
        if (added.isNotEmpty()) khatmaDao.insertAll(added)
        // Already in the account, so this device need not upload them again.
        sync.addUploadedKhatmas(remote.mapTo(HashSet()) { it.id })
        return added.size
    }

    private suspend fun fetchDevices(userDoc: DocumentReference): List<DevicePlace> =
        userDoc.collection(CloudBackupRepository.DEVICES).get(Source.SERVER).await().documents.map { doc ->
            val page = doc.getLong("page")?.toInt()
            val pageAt = doc.getLong("pageAt")
            val bookmarkKey = doc.getString("bookmarkKey")
            val bookmarkAt = doc.getLong("bookmarkAt")
            val bookmarkPage = doc.getLong("bookmarkPage")?.toInt()
            DevicePlace(
                deviceId = doc.id,
                deviceName = doc.getString("deviceName") ?: "جهاز غير معروف",
                lastPage = if (page != null && pageAt != null) PlacePoint(page, null, pageAt) else null,
                bookmark = if (bookmarkKey != null && bookmarkPage != null && bookmarkAt != null) {
                    PlacePoint(bookmarkPage, bookmarkKey, bookmarkAt)
                } else {
                    null
                },
                seenAt = doc.getTimestamp("seenAt")?.toDate()?.time ?: 0L,
            )
        }

    private fun markRecord(doc: DocumentSnapshot): MarkRecord? = MarkRecord(
        id = doc.id,
        verseKey = doc.getString("verseKey") ?: return null,
        page = doc.getLong("page")?.toInt() ?: return null,
        at = doc.getLong("at") ?: return null,
        deviceId = doc.getString("deviceId").orEmpty(),
        deviceName = doc.getString("deviceName") ?: "جهاز غير معروف",
    )

    private fun khatmaRecord(doc: DocumentSnapshot): KhatmaRecord? = KhatmaRecord(
        completedAt = doc.getLong("completedAt") ?: return null,
        startedAt = doc.getLong("startedAt") ?: return null,
        durationDays = doc.getLong("durationDays")?.toInt() ?: 0,
        pagesRead = doc.getLong("pagesRead")?.toInt() ?: 0,
        deviceId = doc.getString("deviceId").orEmpty(),
        deviceName = doc.getString("deviceName") ?: "جهاز غير معروف",
    )
}
