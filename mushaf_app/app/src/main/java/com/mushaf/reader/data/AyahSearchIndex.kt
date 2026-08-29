package com.mushaf.reader.data

/**
 * The mushaf, folded once for searching and then scanned per query.
 *
 * ## Two orthographies
 *
 * Every ayah is indexed under BOTH spellings, and a query matches when it occurs in either.
 * Neither one is enough alone:
 *
 *  - The **Uthmani** script is what the page shows, but it is not what anyone types. It writes the
 *    long /aa/ as a superscript alef rather than a full one, so ٱلْمَلَـٰٓئِكَةُ folds to «المليكه»
 *    while a reader typing الملائكة folds to «الملايكه». The two can never meet, no matter how the
 *    marks are stripped — 4367 of the 6236 ayahs carry a superscript alef.
 *  - The **imlaa'i** text is standard orthography, so it folds to what actually gets typed. But it
 *    spells دَاوُودَ with two waws, so a search for داود misses it, while the Uthmani دَاوُۥدَ —
 *    whose small waw folds away — matches.
 *
 * Together they cover each other's gaps. Measured over 24 ordinary queries against the bundled
 * mushaf: 18 found nothing under the Uthmani text alone, 1 under the imlaa'i alone, none under
 * both.
 *
 * ## Two tiers
 *
 * The scan runs twice. The first pass is a plain substring match and owns the top of the list. The
 * second pass repeats it over [lightStemArabic]'d text, and whatever it adds is flagged
 * [Hit.expanded] so the reader can see where the exact answers stopped.
 *
 * The order is the point. Stemming trades precision for reach — it is the pass that can put a
 * wrong ayah on screen — so it never displaces an exact hit, and a reader who found what they
 * wanted never has to look at it.
 *
 * Only the imlaa'i text is stemmed. The Uthmani index exists for the handful of words the imlaa'i
 * spells differently, and stemming its unusual forms would cost a fourth string per ayah to widen
 * a set that is already narrow.
 *
 * Only the index is doubled. The reader and the result rows still show [AyahMarker.textUthmani] —
 * the imlaa'i text is never displayed, it exists so the search can be typed in.
 *
 * Pure Kotlin (no Android types) so it can be unit-tested without a device.
 */
internal class AyahSearchIndex private constructor(private val entries: List<Entry>) {

    /** One ayah with everything the scan compares against already folded, so a keystroke never
     *  re-folds 6236 strings. */
    private class Entry(
        val marker: AyahMarker,
        val uthmani: String,
        val imlaei: String,
        val stemmed: String,
        val surahName: String,
    )

    /** One result. [expanded] marks an ayah only the stemmed pass reached — a widened, and
     *  therefore less certain, match. */
    class Hit(val marker: AyahMarker, val expanded: Boolean)

    val size: Int get() = entries.size

    /**
     * Ayahs matching [query], in mushaf order, capped at [limit].
     *
     * A bare "surah:ayah" ("2:255") resolves to that single ayah. Anything else is folded and
     * looked for in both orthographies and in the surah name; if that leaves room under [limit],
     * the stemmed pass fills the rest.
     */
    fun search(query: String, limit: Int = DEFAULT_LIMIT): List<Hit> {
        val raw = query.trim()
        if (raw.isEmpty() || limit <= 0) return emptyList()

        // Direct "surah:ayah" jump. An unresolvable key falls through to the text scan rather than
        // ending the search, so "2:1000" still behaves like an ordinary (fruitless) query.
        VerseKeyQuery.find(raw)?.let { match ->
            val key = "${match.groupValues[1]}:${match.groupValues[2]}"
            entries.firstOrNull { it.marker.verseKey == key }
                ?.let { return listOf(Hit(it.marker, expanded = false)) }
        }

        val q = normalizeArabic(raw)
        if (q.isEmpty()) return emptyList()

        val hits = ArrayList<Hit>(minOf(limit, 16))
        val exact = HashSet<String>()
        for (entry in entries) {
            // Imlaa'i first: it is the spelling a typed query is most likely to hit, so the two
            // remaining scans are usually short-circuited away.
            if (entry.imlaei.contains(q) || entry.uthmani.contains(q) || entry.surahName.contains(q)) {
                hits.add(Hit(entry.marker, expanded = false))
                exact.add(entry.marker.verseKey)
                if (hits.size >= limit) return hits
            }
        }

        val stemmedQuery = lightStemArabic(q)
        if (stemmedQuery.isEmpty()) return hits
        for (entry in entries) {
            if (entry.marker.verseKey in exact) continue
            if (entry.stemmed.contains(stemmedQuery)) {
                hits.add(Hit(entry.marker, expanded = true))
                if (hits.size >= limit) break
            }
        }
        return hits
    }

    companion object {
        /** Enough hits to scroll through; the reader refines the query rather than paging. */
        const val DEFAULT_LIMIT = 60

        private val VerseKeyQuery = Regex("""^\s*(\d{1,3})\s*[:：]\s*(\d{1,3})\s*$""")

        /**
         * Fold every ayah once. Heavy (6236 ayahs, folded twice and stemmed once) and meant to be
         * called off the main thread; each distinct surah name is folded once, not once per ayah.
         */
        fun build(markers: List<AyahMarker>): AyahSearchIndex {
            val foldedNames = HashMap<String, String>(128)
            return AyahSearchIndex(
                markers.map { marker ->
                    val imlaei = normalizeArabic(marker.textImlaei)
                    Entry(
                        marker = marker,
                        uthmani = normalizeArabic(marker.textUthmani),
                        imlaei = imlaei,
                        stemmed = lightStemArabic(imlaei),
                        surahName = foldedNames.getOrPut(marker.surahNameAr) {
                            normalizeArabic(marker.surahNameAr)
                        },
                    )
                }
            )
        }
    }
}
