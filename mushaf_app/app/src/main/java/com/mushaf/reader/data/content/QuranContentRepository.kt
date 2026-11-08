package com.mushaf.reader.data.content

import android.content.Context

data class GharibMeaning(
    val word: String,
    val meaning: String,
    val sourceText: String,
)

data class AyahExplanation(
    val tafsirHtml: String?,
    val meanings: List<GharibMeaning>,
    val saadi: TafsirPassage?,
    /** The Mukhtasar's text for this ayah; it may hold marks, see [MukhtasarText]. */
    val mukhtasar: String?,
    /** The occasion of the ayah's revelation, when the source records one. */
    val nozool: TafsirPassageRow? = null,
)

class QuranContentRepository(context: Context) {
    private val dao = QuranContentDatabase.get(context).contentDao()

    suspend fun explanationFor(verseKey: String): AyahExplanation {
        val tafsir = dao.tafsirForVerse(verseKey)
        val meanings = dao.meaningsForVerse(verseKey).map { entry ->
            GharibMeaning(
                word = entry.word,
                meaning = entry.meaning,
                sourceText = entry.sourceText,
            )
        }
        val ayah = verseKey.substringAfter(':').toIntOrNull()
        val saadiRows = dao.passageForVerse(SAADI_SOURCE_ID, verseKey).map { row ->
            TafsirPassageRow(
                kind = TafsirRowKind.fromSource(row.kind),
                fromAyah = row.fromAyah,
                toAyah = row.toAyah,
                text = row.text,
                notes = TafsirPassageRow.parseNotes(row.notes),
            )
        }
        return AyahExplanation(
            tafsirHtml = tafsir?.textHtml,
            meanings = meanings,
            saadi = if (ayah != null && saadiRows.isNotEmpty()) TafsirPassage(saadiRows, ayah) else null,
            mukhtasar = dao.passageForVerse(MUKHTASAR_SOURCE_ID, verseKey).firstOrNull()?.text,
            nozool = dao.nozoolForVerse(verseKey)?.let { row ->
                TafsirPassageRow(
                    kind = TafsirRowKind.Prose,
                    fromAyah = ayah ?: 0,
                    toAyah = ayah ?: 0,
                    text = row.text,
                    notes = TafsirPassageRow.parseNotes(row.notes),
                )
            },
        )
    }

    private companion object {
        const val SAADI_SOURCE_ID = "saadi"
        const val MUKHTASAR_SOURCE_ID = "mukhtasar"
    }
}
