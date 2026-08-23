package com.mushaf.reader.data.content

import androidx.room.Dao
import androidx.room.Query

@Dao
interface QuranContentDao {

    @Query("SELECT * FROM tafsir_ayah WHERE verse_key = :verseKey LIMIT 1")
    suspend fun tafsirForVerse(verseKey: String): TafsirAyahEntity?

    @Query(
        """
        SELECT entry.*
        FROM gharib_entry AS entry
        INNER JOIN gharib_ayah_map AS ayah_map
            ON ayah_map.entry_id = entry.id
        WHERE ayah_map.verse_key = :verseKey
        ORDER BY ayah_map.sort_order ASC, entry.id ASC
        """
    )
    suspend fun meaningsForVerse(verseKey: String): List<GharibEntryEntity>
}
