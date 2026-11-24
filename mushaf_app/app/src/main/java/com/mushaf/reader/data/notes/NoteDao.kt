package com.mushaf.reader.data.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /** Every note in mushaf order, kept current: the reader, the list and a restore all see it. */
    @Query("SELECT * FROM notes ORDER BY surah, ayah")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY surah, ayah")
    suspend fun all(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE surah = :surah AND ayah = :ayah")
    suspend fun find(surah: Int, ayah: Int): NoteEntity?

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE surah = :surah AND ayah = :ayah")
    suspend fun delete(surah: Int, ayah: Int)

    @Insert
    suspend fun insertAll(notes: List<NoteEntity>)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}
