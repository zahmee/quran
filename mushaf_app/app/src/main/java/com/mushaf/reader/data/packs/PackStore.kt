package com.mushaf.reader.data.packs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/** A pack on the device, as its own `pack_meta` table describes it. */
data class InstalledPack(
    val id: String,
    val kind: PackKind,
    val version: Int,
    val title: String,
    val work: String,
    val author: String,
    /** The credit line that goes with text copied or shared out of the app. */
    val credit: String,
    /** What was changed from the source, for the sources screen. */
    val changes: String,
    val bytes: Long,
)

/**
 * The downloaded packs: one read-only SQLite file each in `files/packs/<id>.db`. Reads share open
 * handles; installing or deleting a pack takes the write lock, so a file is never swapped or removed
 * while a query is reading it.
 */
object PackStore {

    private val lock = ReentrantReadWriteLock()
    private val handles = HashMap<String, SQLiteDatabase>()

    /** Compose state: the installed packs by id, refreshed by [refresh]. */
    var installed by mutableStateOf<Map<String, InstalledPack>>(emptyMap())
        private set

    fun dir(context: Context): File = File(context.filesDir, "packs").apply { mkdirs() }

    fun file(context: Context, id: String): File = File(dir(context), "$id.db")

    /** Where a download of [id] is written before it is checked and unpacked. */
    fun partFile(context: Context, id: String): File = File(dir(context), "$id.gz.part")

    fun tempFile(context: Context, id: String): File = File(dir(context), "$id.db.tmp")

    suspend fun refresh(context: Context) {
        val found = withContext(Dispatchers.IO) { scan(context) }
        withContext(Dispatchers.Main) { installed = found }
    }

    private fun scan(context: Context): Map<String, InstalledPack> = lock.read {
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".db") }.orEmpty()
            .mapNotNull { readMeta(it) }
            .associateBy { it.id }
    }

    /** The pack's description, or null when the file is not a pack this app can read. */
    fun readMeta(file: File): InstalledPack? {
        val meta = HashMap<String, String>()
        runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT key, value FROM pack_meta", null).use { c ->
                    while (c.moveToNext()) meta[c.getString(0)] = c.getString(1)
                }
            }
        }.onFailure { return null }
        val id = meta["pack_id"] ?: return null
        if (file.name != "$id.db") return null
        if ((meta["schema"]?.toIntOrNull() ?: Int.MAX_VALUE) > PackFormat.SCHEMA) return null
        return InstalledPack(
            id = id,
            kind = PackKind.fromId(meta["kind"]) ?: return null,
            version = meta["version"]?.toIntOrNull() ?: return null,
            title = meta["title"].orEmpty(),
            work = meta["work"].orEmpty(),
            author = meta["author"].orEmpty(),
            credit = meta["credit"].orEmpty(),
            changes = meta["changes"].orEmpty(),
            bytes = file.length(),
        )
    }

    /** An unpacked download is the pack the catalog promised and SQLite finds nothing wrong in it. */
    fun checkUnpacked(file: File, id: String, version: Int) {
        val ok = runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val integrity = db.rawQuery("PRAGMA quick_check", null).use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
                val meta = HashMap<String, String>()
                db.rawQuery("SELECT key, value FROM pack_meta", null).use { c ->
                    while (c.moveToNext()) meta[c.getString(0)] = c.getString(1)
                }
                integrity == "ok" &&
                    meta["pack_id"] == id &&
                    meta["version"] == version.toString() &&
                    (meta["schema"]?.toIntOrNull() ?: Int.MAX_VALUE) <= PackFormat.SCHEMA &&
                    PackKind.fromId(meta["kind"]) != null
            }
        }.getOrDefault(false)
        if (!ok) throw PackIntegrityException("The unpacked file is not pack $id version $version")
    }

    /** Runs [block] on the open pack, or returns null when it is not installed. */
    fun <T> read(context: Context, id: String, block: (SQLiteDatabase) -> T): T? = lock.read {
        val db = synchronized(handles) {
            handles[id]?.takeIf { it.isOpen } ?: run {
                val f = file(context, id)
                if (!f.isFile) return@read null
                SQLiteDatabase.openDatabase(
                    f.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                ).also { handles[id] = it }
            }
        }
        block(db)
    }

    /** Moves a checked, unpacked pack into place, replacing an older version of it. */
    fun install(context: Context, id: String, unpacked: File) = lock.write {
        close(id)
        val target = file(context, id)
        if (!unpacked.renameTo(target)) {
            target.delete()
            check(unpacked.renameTo(target)) { "Could not move the pack into place" }
        }
    }

    fun delete(context: Context, id: String) = lock.write {
        close(id)
        file(context, id).delete()
        partFile(context, id).delete()
        tempFile(context, id).delete()
    }

    private fun close(id: String) {
        synchronized(handles) { handles.remove(id)?.close() }
    }
}
