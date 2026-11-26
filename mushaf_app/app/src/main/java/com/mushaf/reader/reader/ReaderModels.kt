package com.mushaf.reader.reader

import com.mushaf.reader.data.AyahSearchIndex
import com.mushaf.reader.data.RootSearch
import com.mushaf.reader.data.SearchTier
import com.mushaf.reader.data.content.GharibMeaning
import com.mushaf.reader.data.content.TafsirPassage
import com.mushaf.reader.data.content.TafsirPassageRow
import com.mushaf.reader.data.packs.PackContent

/** One surah in the navigation index: number, Arabic name, and the page it begins on. */
data class SurahEntry(val number: Int, val nameAr: String, val firstPage: Int, val ayahCount: Int)

/** One juz in the navigation index: number and the page it begins on. */
data class JuzEntry(val number: Int, val firstPage: Int, val ayahCount: Int)

/** A single ayah hit. */
data class SearchResult(
    val verseKey: String,
    val surahNameAr: String,
    val ayahNumber: Int,
    val page: Int,
    val text: String,
    /** How the ayah matched; the search screen groups and labels hits by it. See [AyahSearchIndex]. */
    val tier: SearchTier = SearchTier.WORD,
    /** Character ranges in [text] of the words that matched, for the screen to mark. */
    val highlights: List<IntRange> = emptyList(),
)

/** A search result's ayah being flashed on its page; [token] tells one opening from the next. */
data class SearchFlash(val verseKey: String, val page: Int, val token: Long)

/** A surah whose name matched the query — shown once, not as every ayah in it. */
data class SurahSearchResult(val number: Int, val nameAr: String, val firstPage: Int)

data class SearchResults(
    val surahs: List<SurahSearchResult> = emptyList(),
    val ayahs: List<SearchResult> = emptyList(),
    /** The roots the typed word belongs to, offered above the other results. See [RootSearch]. */
    val roots: List<RootSearch.Root> = emptyList(),
) {
    val isEmpty: Boolean get() = surahs.isEmpty() && ayahs.isEmpty() && roots.isEmpty()
}

/** Every ayah holding a word from [root] — or only from its form [form] — with those words marked. */
data class RootResults(
    val root: RootSearch.Root,
    val form: Int?,
    val ayahs: List<SearchResult>,
)

data class AyahExplanationUiState(
    val verseKey: String? = null,
    val loading: Boolean = false,
    val tafsirHtml: String? = null,
    val meanings: List<GharibMeaning> = emptyList(),
    val saadi: TafsirPassage? = null,
    val mukhtasar: String? = null,
    /** The occasion of the ayah's revelation, for the ayahs the source records one for. */
    val nozool: TafsirPassageRow? = null,
    val errorMessage: String? = null,
)

/** What the sheet shows for a downloaded pack: that pack's text for one ayah. */
data class PackContentUiState(
    val verseKey: String? = null,
    val packId: String? = null,
    val loading: Boolean = false,
    val content: PackContent? = null,
)
