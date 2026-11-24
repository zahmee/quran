package com.mushaf.reader.data.notes

import android.content.Context
import com.mushaf.reader.data.stats.AppDatabase
import kotlinx.coroutines.flow.Flow

/** The reader's notes on ayahs and surahs, kept in the app database beside their reading history. */
class NotesRepository(context: Context) {

    private val dao = AppDatabase.get(context).noteDao()

    val notes: Flow<List<NoteEntity>> = dao.observeAll()

    /**
     * Writes [text] as the note at [surah]:[ayah], keeping when it was first written. Blank text
     * removes the note: an emptied note is a deleted one, not an empty row in the list.
     */
    suspend fun save(surah: Int, ayah: Int, text: String, now: Long = System.currentTimeMillis()) {
        require(NoteEntity.isValidPlace(surah, ayah)) { "no such place: $surah:$ayah" }
        val clean = cleanNoteText(text)
        if (clean.isEmpty()) {
            dao.delete(surah, ayah)
            return
        }
        val createdAt = dao.find(surah, ayah)?.createdAt ?: now
        dao.upsert(NoteEntity(surah, ayah, clean, createdAt = createdAt, updatedAt = now))
    }

    suspend fun delete(surah: Int, ayah: Int) = dao.delete(surah, ayah)
}

/** A note as it is kept: no blank lines around it, and no longer than [NoteEntity.MAX_TEXT]. */
fun cleanNoteText(raw: String): String = raw.trim().take(NoteEntity.MAX_TEXT).trim()
