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

    /** Every row of the passage that explains [verseKey], in the book's reading order. */
    @Query(
        """
        SELECT passage.*
        FROM tafsir_passage AS passage
        WHERE passage.source_id = :sourceId
            AND passage.group_id = (
                SELECT ayah_group.group_id
                FROM tafsir_ayah_group AS ayah_group
                WHERE ayah_group.source_id = :sourceId AND ayah_group.verse_key = :verseKey
            )
        ORDER BY passage.row_id ASC
        """
    )
    suspend fun passageForVerse(sourceId: String, verseKey: String): List<TafsirPassageEntity>

    @Query("SELECT * FROM ayah_nozool WHERE verse_key = :verseKey LIMIT 1")
    suspend fun nozoolForVerse(verseKey: String): AyahNozoolEntity?
}
