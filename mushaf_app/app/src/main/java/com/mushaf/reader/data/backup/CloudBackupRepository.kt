package com.mushaf.reader.data.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import com.mushaf.reader.BuildConfig
import com.mushaf.reader.data.ReadingStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Keeps backups in the reader's own Firebase account: a name and a password, no e-mail.
 *
 * Each backup is the same gzip JSON the file transport writes, stored as two documents — its
 * metadata under `backups`, its bytes under `payloads` — so the list can be shown without
 * downloading every payload. Each device keeps its latest [KEEP_PER_DEVICE]; restoring goes
 * through [LocalBackupData.mergeIn], exactly like a file.
 */
class CloudBackupRepository(context: Context) {

    private val appContext = context.applicationContext
    private val local = LocalBackupData(appContext)
    private val store = ReadingStore(appContext)

    /** False when the build carries no Firebase project (no google-services.json). */
    val isAvailable: Boolean = FirebaseApp.getApps(appContext).isNotEmpty()

    val isSignedIn: Boolean get() = isAvailable && auth.currentUser != null

    suspend fun signUp(name: String, password: String): String = remote {
        val display = name.trim()
        CloudUsername.problemWith(display)?.let { throw BackupException(it) }
        CloudUsername.problemWithPassword(password)?.let { throw BackupException(it) }
        val user = auth.createUserWithEmailAndPassword(CloudUsername.email(display), password).await().user
            ?: throw BackupException(GENERIC_ERROR)
        userDoc(user).set(mapOf("username" to display, "createdAt" to System.currentTimeMillis())).await()
        store.setCloudAccount(display)
        display
    }

    suspend fun signIn(name: String, password: String): String = remote {
        val typed = name.trim()
        if (typed.isEmpty() || password.isEmpty()) throw BackupException("اكتب الاسم وكلمة المرور.")
        val user = auth.signInWithEmailAndPassword(CloudUsername.email(typed), password).await().user
            ?: throw BackupException(GENERIC_ERROR)
        // The name as it was first written ("أحمد"), not however it was typed today ("احمد").
        val stored = try {
            userDoc(user).get(Source.SERVER).await().getString("username")
        } catch (_: FirebaseFirestoreException) {
            null
        }
        val display = stored?.takeIf { it.isNotBlank() } ?: typed
        store.setCloudAccount(display)
        display
    }

    suspend fun signOut() {
        if (isAvailable) auth.signOut()
        store.setCloudAccount(null)
    }

    /** Forget a remembered account whose sign-in Firebase no longer holds. */
    suspend fun reconcileAccount(): String? {
        val username = store.cloudPrefs().username
        if (username != null && !isSignedIn) {
            store.setCloudAccount(null)
            return null
        }
        return username
    }

    /** Erases every backup, then the account itself. Asks for the password again first. */
    suspend fun deleteAccount(password: String) = remote {
        val user = requireUser()
        val email = user.email ?: throw BackupException(GENERIC_ERROR)
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        val userDoc = userDoc(user)
        val ids = userDoc.collection(BACKUPS).get(Source.SERVER).await().documents.map { it.id }
        // A Firestore batch holds up to 500 writes; each backup is two.
        ids.chunked(200).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { id ->
                batch.delete(userDoc.collection(BACKUPS).document(id))
                batch.delete(userDoc.collection(PAYLOADS).document(id))
            }
            batch.commit().await()
        }
        userDoc.delete().await()
        user.delete().await()
        store.setCloudAccount(null)
    }

    suspend fun list(): List<CloudBackupInfo> = remote {
        val user = requireUser()
        val deviceId = store.cloudDeviceId()
        userDoc(user).collection(BACKUPS).get(Source.SERVER).await().documents.mapNotNull { doc ->
            val createdAt = doc.getLong("createdAt") ?: return@mapNotNull null
            val backupDevice = doc.getString("deviceId").orEmpty()
            CloudBackupInfo(
                id = doc.id,
                createdAt = createdAt,
                deviceId = backupDevice,
                deviceName = doc.getString("deviceName") ?: "جهاز غير معروف",
                appVersion = doc.getString("appVersion").orEmpty(),
                sizeBytes = doc.getLong("sizeBytes") ?: 0L,
                auto = doc.getBoolean("auto") ?: false,
                fromThisDevice = backupDevice == deviceId,
            )
        }.sortedByDescending { it.createdAt }
    }

    /**
     * Uploads this device's data. An [auto] upload is skipped — and returns null — when nothing
     * has changed since the last upload, so a day without reading costs no write.
     */
    suspend fun upload(auto: Boolean): CloudBackupInfo? = remote {
        val user = requireUser()
        val snapshot = local.snapshot()
        val fingerprint = fingerprint(snapshot)
        if (auto && fingerprint == store.cloudPrefs().fingerprint) return@remote null

        val bytes = withContext(Dispatchers.Default) { BackupJsonCodec.encode(snapshot) }
        if (bytes.size > MAX_PAYLOAD_BYTES) {
            throw BackupException("النسخة أكبر من حد الحفظ السحابي. احفظها في ملف بدلاً من ذلك.")
        }
        val info = CloudBackupInfo(
            id = snapshot.backupId,
            createdAt = snapshot.createdAt,
            deviceId = store.cloudDeviceId(),
            deviceName = snapshot.deviceName,
            appVersion = snapshot.appVersion,
            sizeBytes = bytes.size.toLong(),
            auto = auto,
            fromThisDevice = true,
        )
        val userDoc = userDoc(user)
        firestore.batch()
            .set(
                userDoc.collection(BACKUPS).document(info.id),
                mapOf(
                    "createdAt" to info.createdAt,
                    "deviceId" to info.deviceId,
                    "deviceName" to info.deviceName,
                    "appVersion" to info.appVersion,
                    "schemaVersion" to snapshot.schemaVersion,
                    "sizeBytes" to info.sizeBytes,
                    "auto" to info.auto,
                ),
            )
            .set(userDoc.collection(PAYLOADS).document(info.id), mapOf("data" to Blob.fromBytes(bytes)))
            .commit()
            .await()
        store.setCloudUploaded(info.createdAt, fingerprint)
        pruneOlderBackups(userDoc, info.deviceId)
        info
    }

    /** Downloads [backup] and folds it into this device; returns what the device now holds. */
    suspend fun restore(backup: CloudBackupInfo, takeDisplaySettings: Boolean): BackupSnapshot {
        val bytes = remote {
            val user = requireUser()
            userDoc(user).collection(PAYLOADS).document(backup.id).get(Source.SERVER).await()
                .getBlob("data")?.toBytes()
                ?: throw BackupException("لم تعد هذه النسخة موجودة في حسابك.")
        }
        val snapshot = withContext(Dispatchers.Default) { BackupJsonCodec.decode(bytes, local.pageCount) }
        return local.mergeIn(snapshot, takeDisplaySettings)
    }

    private suspend fun pruneOlderBackups(userDoc: DocumentReference, deviceId: String) {
        try {
            val stale = userDoc.collection(BACKUPS).get(Source.SERVER).await().documents
                .filter { it.getString("deviceId") == deviceId }
                .sortedByDescending { it.getLong("createdAt") ?: 0L }
                .drop(KEEP_PER_DEVICE)
            if (stale.isEmpty()) return
            val batch = firestore.batch()
            stale.forEach {
                batch.delete(it.reference)
                batch.delete(userDoc.collection(PAYLOADS).document(it.id))
            }
            batch.commit().await()
        } catch (_: FirebaseFirestoreException) {
            // The new backup is safe either way; the next upload tries the cleanup again.
        }
    }

    /** Digest of the content alone — the id, time and labels differ on every snapshot. */
    private fun fingerprint(snapshot: BackupSnapshot): String {
        val content = snapshot.copy(backupId = "-", createdAt = 1L, appVersion = "", deviceName = "")
        return MessageDigest.getInstance("SHA-256")
            .digest(BackupJsonCodec.encode(content))
            .joinToString("") { "%02x".format(it) }
    }

    private fun requireUser(): FirebaseUser =
        auth.currentUser ?: throw BackupException("انتهت جلسة الدخول؛ سجّل الدخول من جديد.")

    private fun userDoc(user: FirebaseUser): DocumentReference =
        firestore.collection(USERS).document(user.uid)

    /**
     * Runs one remote operation with a time limit and turns Firebase's failures into messages a
     * reader can act on. The limit matters for writes: with no connection Firestore keeps them
     * pending instead of failing, and the screen would wait forever.
     */
    private suspend fun <T> remote(block: suspend () -> T): T {
        if (!isAvailable) throw BackupException("النسخ السحابي غير متاح في هذه النسخة من التطبيق.")
        // Fail at once rather than after the time limit when the device is plainly offline.
        if (!hasConnection()) throw CloudOfflineException()
        return try {
            withTimeout(TIMEOUT_MS) { block() }
        } catch (timeout: TimeoutCancellationException) {
            throw CloudOfflineException(timeout)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: BackupException) {
            throw known
        } catch (error: Exception) {
            throw describe(error)
        }
    }

    private fun hasConnection(): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Firebase Auth does not always say "network" when it means it: an unreachable server can
     * surface as a generic internal error whose only clue is the wrapped I/O failure or its text.
     */
    private fun isNetworkFailure(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { cause ->
            val message = cause.message.orEmpty()
            cause is FirebaseNetworkException || cause is IOException ||
                "Failed to connect" in message || "Unable to resolve host" in message ||
                "timeout" in message.lowercase()
        }

    private fun describe(error: Exception): BackupException = when {
        isNetworkFailure(error) -> CloudOfflineException(error)
        else -> describeRefusal(error)
    }

    private fun describeRefusal(error: Exception): BackupException = when (error) {
        is FirebaseTooManyRequestsException ->
            BackupException("محاولات كثيرة متتالية؛ انتظر قليلاً ثم أعد المحاولة.", error)
        is FirebaseAuthUserCollisionException ->
            BackupException("هذا الاسم مستخدم من قبل؛ اختر اسماً آخر.", error)
        is FirebaseAuthWeakPasswordException ->
            BackupException("كلمة المرور ضعيفة؛ اختر كلمة أطول.", error)
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException ->
            BackupException("الاسم أو كلمة المرور غير صحيحة.", error)
        is FirebaseAuthRecentLoginRequiredException ->
            BackupException("سجّل الخروج ثم الدخول من جديد، وأعد المحاولة.", error)
        is FirebaseAuthException ->
            if (error.errorCode == "ERROR_OPERATION_NOT_ALLOWED") {
                BackupException("الدخول بكلمة مرور غير مفعّل في مشروع فايربيس.", error)
            } else {
                BackupException(GENERIC_ERROR, error)
            }
        is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> CloudOfflineException(error)
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                BackupException("رفض الخادم العملية. سجّل الدخول من جديد ثم أعد المحاولة.", error)
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                BackupException("الخدمة مشغولة حالياً؛ أعد المحاولة لاحقاً.", error)
            else -> BackupException(GENERIC_ERROR, error)
        }
        else -> BackupException(GENERIC_ERROR, error)
    }

    private companion object {
        const val USERS = "users"
        const val BACKUPS = "backups"
        const val PAYLOADS = "payloads"
        const val KEEP_PER_DEVICE = 3
        const val TIMEOUT_MS = 30_000L
        /** Firestore caps a document near 1 MiB; years of heavy reading still compress far below. */
        const val MAX_PAYLOAD_BYTES = 900_000
        const val GENERIC_ERROR = "تعذر إكمال العملية. أعد المحاولة بعد قليل."

        /** Payloads are fetched only on demand, so no copy of them is cached on disk. */
        val firestore: FirebaseFirestore by lazy {
            FirebaseFirestore.getInstance().apply {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
                BuildConfig.FIREBASE_EMULATOR_HOST?.let { useEmulator(it, 8080) }
            }
        }

        val auth: FirebaseAuth by lazy {
            FirebaseAuth.getInstance().apply {
                BuildConfig.FIREBASE_EMULATOR_HOST?.let { useEmulator(it, 9099) }
            }
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> continuation.resumeWithException(error)
            task.isCanceled -> continuation.cancel()
            else -> continuation.resume(task.result)
        }
    }
}
