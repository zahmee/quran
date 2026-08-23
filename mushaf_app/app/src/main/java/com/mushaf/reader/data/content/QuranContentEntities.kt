package com.mushaf.reader.data.content

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "content_meta")
data class ContentMetaEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String,
)

@Entity(
    tableName = "tafsir_ayah",
    indices = [
        Index(value = ["surah_number", "ayah_number"], unique = true),
    ],
)
data class TafsirAyahEntity(
    @PrimaryKey @ColumnInfo(name = "verse_key") val verseKey: String,
    @ColumnInfo(name = "surah_number") val surahNumber: Int,
    @ColumnInfo(name = "ayah_number") val ayahNumber: Int,
    @ColumnInfo(name = "source_id") val sourceId: Int,
    @ColumnInfo(name = "text_html") val textHtml: String,
)

@Entity(tableName = "gharib_entry")
data class GharibEntryEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "surah_number") val surahNumber: Int,
    @ColumnInfo(name = "ayah_start") val ayahStart: Int,
    @ColumnInfo(name = "ayah_end") val ayahEnd: Int,
    @ColumnInfo(name = "entry_index") val entryIndex: Int,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "meaning") val meaning: String,
    @ColumnInfo(name = "source_text") val sourceText: String,
)

@Entity(
    tableName = "gharib_ayah_map",
    primaryKeys = ["verse_key", "entry_id"],
    indices = [Index(value = ["verse_key", "sort_order"])],
)
data class GharibAyahMapEntity(
    @ColumnInfo(name = "verse_key") val verseKey: String,
    @ColumnInfo(name = "entry_id") val entryId: Int,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)
