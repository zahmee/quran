package com.mushaf.reader.data.notes

import androidx.room.Entity
import com.mushaf.reader.data.SurahAyahCounts

/**
 * One note the reader wrote: on an ayah, or on a whole surah when [ayah] is [SURAH_NOTE].
 * A place holds one note, which the reader keeps adding to, so the place is the key; the two
 * numbers together also sort the notes in mushaf order, each surah's own note first.
 */
@Entity(tableName = "notes", primaryKeys = ["surah", "ayah"])
data class NoteEntity(
    val surah: Int,
    val ayah: Int,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isSurahNote: Boolean get() = ayah == SURAH_NOTE

    /** "2:255" for an ayah note, the key the page and the bookmarks use; null for a surah note. */
    val verseKey: String? get() = if (isSurahNote) null else "$surah:$ayah"

    companion object {
        /** The [ayah] of a note on the whole surah. */
        const val SURAH_NOTE = 0

        /** Long enough for a page of reflection, short enough to keep a backup small. */
        const val MAX_TEXT = 10_000

        /** True when [surah] and [ayah] name a real place in the mushaf (or a whole surah). */
        fun isValidPlace(surah: Int, ayah: Int): Boolean =
            surah in 1..SurahAyahCounts.SURAH_COUNT && ayah in SURAH_NOTE..SurahAyahCounts.of(surah)
    }
}
