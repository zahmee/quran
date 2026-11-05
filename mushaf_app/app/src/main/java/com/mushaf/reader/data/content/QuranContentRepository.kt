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
    /** The Sa'di snapshot's version as recorded in the content database, e.g. "1.0.0". */
    val saadiVersion: String?,
)

class QuranContentRepository(context: Context) {
    private val dao = QuranContentDatabase.get(context).contentDao()

    @Volatile
    private var saadiVersion: String? = null

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
            )
        }
        val version = saadiVersion ?: dao.meta("saadi_version").also { saadiVersion = it }
        return AyahExplanation(
            tafsirHtml = tafsir?.textHtml,
            meanings = meanings,
            saadi = if (ayah != null && saadiRows.isNotEmpty()) TafsirPassage(saadiRows, ayah) else null,
            saadiVersion = version,
        )
    }

    private companion object {
        const val SAADI_SOURCE_ID = "saadi"
    }
}
