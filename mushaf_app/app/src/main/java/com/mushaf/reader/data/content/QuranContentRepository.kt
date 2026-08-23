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
        return AyahExplanation(
            tafsirHtml = tafsir?.textHtml,
            meanings = meanings,
        )
    }
}
