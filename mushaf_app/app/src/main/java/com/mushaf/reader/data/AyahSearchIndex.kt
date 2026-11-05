package com.mushaf.reader.data

/** How a hit matched, best first. The search screen groups and labels its results by this. */
enum class SearchTier {
    /** A typed "surah:ayah" key — the one ayah it names. */
    VERSE,

    /** The query is a word of the ayah, bare or with its usual particles and endings: ربك, وربك
     *  and الرب for رب. */
    WORD,

    /** The query's letters occur only inside a different word: قرب, ضرب, اقترب for رب. */
    PARTIAL,

    /** Reached only by the stemmed pass — a widened, and therefore less certain, match. */
    STEMMED,
}

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
 * ## Three tiers
 *
 * A plain substring scan is what makes the search forgiving, and also what makes it noisy: رب
 * occurs inside a word in 1069 ayahs but stands as a word in only 126. Results are therefore
 * ranked by how they matched, each tier kept in mushaf order:
 *
 *  1. [SearchTier.WORD] — the query is a whole word once the particles in front of it (و ف ب ك ل س
 *     and the article) and the endings behind it (pronouns, duals, plurals, verb endings) are set
 *     aside. ربك and الرب rank here for رب.
 *  2. [SearchTier.PARTIAL] — the letters occur, but only inside another word: قرب, حرب.
 *  3. [SearchTier.STEMMED] — the stemmed pass, which repeats the scan over [lightStemArabic]'d
 *     text and adds only ayahs the first two tiers did not reach.
 *
 * The tiers order the results; they never drop one. The affix lists can therefore afford to be
 * generous — the cost of a wrong guess is a hit one tier too high, not a hit lost. The stemmed pass
 * is the opposite case: it is the pass that can put a wrong ayah on screen, so it never displaces a
 * hit from the scan above it, and the screen labels where it begins.
 *
 * Only the imlaa'i text is stemmed. The Uthmani index exists for the handful of words the imlaa'i
 * spells differently, and stemming its unusual forms would cost a fourth string per ayah to widen
 * a set that is already narrow.
 *
 * ## Highlights
 *
 * Every hit carries the character ranges, in [AyahMarker.textUthmani], of the words it matched, so
 * the screen can mark them. Whole words are marked rather than the matched letters: colouring part
 * of a joined Arabic word would split it, and the word is what the reader is looking for.
 *
 * A match found in the imlaa'i text is carried over to the Uthmani words by [alignWords]. Within a
 * tier, only that tier's occurrences are marked — an ayah ranked for ربك does not also light up the
 * قرب beside it.
 *
 * ## Surah names
 *
 * A query that matches a surah's name returns that surah once, as its own result, rather than
 * every ayah in it. Otherwise مريم would add all 98 ayahs of her surah — only 3 of which name her —
 * to the 31 that do, and the counts the screen shows would stop meaning "ayahs containing the word".
 *
 * Only the index is doubled. The reader and the result rows still show [AyahMarker.textUthmani] —
 * the imlaa'i text is never displayed, it exists so the search can be typed in.
 *
 * Pure Kotlin (no Android types) so it can be unit-tested without a device.
 */
internal class AyahSearchIndex private constructor(
    private val entries: List<Entry>,
    private val surahs: List<Surah>,
) {

    /** One ayah with everything the scan compares against already folded, so a keystroke never
     *  re-folds 6236 strings. */
    private class Entry(
        val marker: AyahMarker,
        val uthmani: FoldedWords,
        val imlaei: FoldedWords,
        val stemmed: FoldedWords,
        /** Where each Uthmani word sits in [AyahMarker.textUthmani]: start and end (exclusive) of
         *  word i at [2i] and [2i + 1]. Standalone waqf signs are not words and are skipped. */
        val uthmaniSpans: IntArray,
        /** For each imlaa'i word, the first and last Uthmani word it spells (see [alignWords]);
         *  null for an ayah the two texts could not be aligned in. */
        val imlaeiToUthmani: IntArray?,
    ) {
        val uthmaniWordCount: Int get() = uthmaniSpans.size / 2
    }

    private class Surah(val number: Int, val nameAr: String, val folded: String, val firstPage: Int)

    /** One ayah result. [highlights] are character ranges in the ayah's Uthmani text. */
    class Hit(val marker: AyahMarker, val tier: SearchTier, val highlights: List<IntRange>)

    /** A surah whose name matched the query. */
    class SurahHit(val number: Int, val nameAr: String, val firstPage: Int)

    class Results(val surahs: List<SurahHit>, val ayahs: List<Hit>) {
        companion object {
            val EMPTY = Results(emptyList(), emptyList())
        }
    }

    val size: Int get() = entries.size

    /**
     * Surahs whose name matches [query], and ayahs matching it ranked by [SearchTier] — each tier
     * in mushaf order, the whole list capped at [limit].
     *
     * A bare "surah:ayah" ("2:255") resolves to that single ayah. Anything else is folded and
     * looked for in both orthographies, then in the stemmed text.
     */
    fun search(query: String, limit: Int = Int.MAX_VALUE): Results {
        val raw = query.trim()
        if (raw.isEmpty() || limit <= 0) return Results.EMPTY

        // Direct "surah:ayah" jump. An unresolvable key falls through to the text scan rather than
        // ending the search, so "2:1000" still behaves like an ordinary (fruitless) query.
        VerseKeyQuery.find(raw)?.let { match ->
            val key = "${match.groupValues[1]}:${match.groupValues[2]}"
            entries.firstOrNull { it.marker.verseKey == key }?.let {
                return Results(emptyList(), listOf(Hit(it.marker, SearchTier.VERSE, emptyList())))
            }
        }

        val q = foldPhrase(raw)
        if (q.isEmpty()) return Results.EMPTY

        val surahHits = surahs.filter { it.folded.contains(q) }
            .map { SurahHit(it.number, it.nameAr, it.firstPage) }

        val word = ArrayList<Hit>()
        val partial = ArrayList<Hit>()
        val reached = BooleanArray(entries.size)
        for ((i, entry) in entries.withIndex()) {
            // Imlaa'i first: it is the spelling a typed query is most likely to hit.
            val inImlaei = entry.imlaei.text.contains(q)
            val inUthmani = entry.uthmani.text.contains(q)
            if (!inImlaei && !inUthmani) continue
            reached[i] = true

            val marks = Marks(entry.uthmaniWordCount)
            if (inImlaei) {
                entry.imlaei.forEachMatch(q) { first, last, whole ->
                    marks.addImlaei(entry, first, last, whole)
                }
            }
            if (inUthmani) {
                entry.uthmani.forEachMatch(q) { first, last, whole -> marks.add(first, last, whole) }
            }
            if (marks.anyWhole) {
                word += Hit(entry.marker, SearchTier.WORD, entry.spansOf(marks.whole))
            } else {
                partial += Hit(entry.marker, SearchTier.PARTIAL, entry.spansOf(marks.part))
            }
        }

        val stemmed = ArrayList<Hit>()
        val stemmedQuery = lightStemArabic(q)
        if (stemmedQuery.isNotEmpty() && word.size + partial.size < limit) {
            for ((i, entry) in entries.withIndex()) {
                if (reached[i] || !entry.stemmed.text.contains(stemmedQuery)) continue
                // Stemming trims each word and never drops one, so stemmed word i is imlaa'i word i.
                val marks = Marks(entry.uthmaniWordCount)
                entry.stemmed.forEachMatch(stemmedQuery) { first, last, _ ->
                    marks.addImlaei(entry, first, last, isWhole = false)
                }
                stemmed += Hit(entry.marker, SearchTier.STEMMED, entry.spansOf(marks.part))
            }
        }

        val ayahs = ArrayList<Hit>(minOf(limit, word.size + partial.size + stemmed.size))
        for (tier in listOf(word, partial, stemmed)) {
            for (hit in tier) {
                if (ayahs.size >= limit) break
                ayahs += hit
            }
        }
        return Results(surahHits, ayahs)
    }

    /** The Uthmani words one ayah's occurrences touched, split by whether the occurrence was a
     *  whole word. */
    private class Marks(wordCount: Int) {
        var anyWhole = false
        val whole = BooleanArray(wordCount)
        val part = BooleanArray(wordCount)

        fun add(first: Int, last: Int, isWhole: Boolean) {
            if (isWhole) anyWhole = true
            val into = if (isWhole) whole else part
            for (w in first..last) if (w in into.indices) into[w] = true
        }

        /** An occurrence in the imlaa'i (or stemmed) text. It still decides the tier when the ayah
         *  has no alignment; it just cannot be marked. */
        fun addImlaei(entry: Entry, first: Int, last: Int, isWhole: Boolean) {
            val map = entry.imlaeiToUthmani
            if (map == null || 2 * last + 1 >= map.size) {
                if (isWhole) anyWhole = true
                return
            }
            add(map[2 * first], map[2 * last + 1], isWhole)
        }
    }

    /** Marked words → character ranges, with consecutive words joined into one range so a phrase
     *  is marked as one stretch, the space between its words included. */
    private fun Entry.spansOf(marked: BooleanArray): List<IntRange> {
        val out = ArrayList<IntRange>(2)
        var w = 0
        while (w < marked.size) {
            if (!marked[w]) {
                w++
                continue
            }
            var last = w
            while (last + 1 < marked.size && marked[last + 1]) last++
            out += uthmaniSpans[2 * w] until uthmaniSpans[2 * last + 1]
            w = last + 1
        }
        return out
    }

    companion object {
        private val VerseKeyQuery = Regex("""^\s*(\d{1,3})\s*[:：]\s*(\d{1,3})\s*$""")

        /**
         * Fold every ayah once. Heavy (6236 ayahs, folded twice, stemmed and aligned once) and meant
         * to be called off the main thread.
         */
        fun build(markers: List<AyahMarker>): AyahSearchIndex {
            val surahs = LinkedHashMap<Int, Surah>(128)
            val entries = markers.map { marker ->
                // Markers arrive in mushaf order, so a surah's first ayah seen is where it begins.
                surahs.getOrPut(marker.surahNumber) {
                    Surah(marker.surahNumber, marker.surahNameAr, foldPhrase(marker.surahNameAr), marker.page)
                }

                val uthmaniWords = ArrayList<String>()
                val spans = ArrayList<Int>()
                forEachToken(marker.textUthmani) { start, end ->
                    val folded = normalizeArabic(marker.textUthmani.substring(start, end))
                    if (folded.isNotEmpty()) {
                        uthmaniWords += folded
                        spans += start
                        spans += end
                    }
                }
                val imlaeiWords = foldWords(marker.textImlaei)
                val imlaei = FoldedWords(imlaeiWords.joinToString(" "))
                Entry(
                    marker = marker,
                    uthmani = FoldedWords(uthmaniWords.joinToString(" ")),
                    imlaei = imlaei,
                    stemmed = FoldedWords(lightStemArabic(imlaei.text)),
                    uthmaniSpans = spans.toIntArray(),
                    imlaeiToUthmani = alignWords(imlaeiWords, uthmaniWords),
                )
            }
            return AyahSearchIndex(entries, surahs.values.toList())
        }
    }
}

/**
 * A folded text whose words are separated by exactly one space, with the offset of each word.
 *
 * Folding empties the standalone waqf signs, which would otherwise leave double spaces behind —
 * and a two-word query typed with one space would then miss every pair a waqf sign stands between.
 */
private class FoldedWords(val text: String) {
    private val starts: IntArray = if (text.isEmpty()) IntArray(0) else {
        val list = ArrayList<Int>()
        list += 0
        for (i in text.indices) if (text[i] == ' ') list += i + 1
        list.toIntArray()
    }

    /** Index of the word containing character [offset]. */
    private fun wordAt(offset: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun wordEnd(word: Int): Int =
        if (word + 1 < starts.size) starts[word + 1] - 1 else text.length

    /**
     * Each occurrence of [q], as the words it spans and whether it is a whole word — what is left of
     * its first word in front of it a known prefix, and what is left of its last word after it a
     * known ending.
     */
    fun forEachMatch(q: String, action: (first: Int, last: Int, whole: Boolean) -> Unit) {
        var start = text.indexOf(q)
        while (start >= 0) {
            val end = start + q.length
            val first = wordAt(start)
            val last = wordAt(end - 1)
            val whole = text.substring(starts[first], start) in WORD_PREFIXES &&
                text.substring(end, wordEnd(last)) in WORD_ENDINGS
            action(first, last, whole)
            start = text.indexOf(q, start + 1)
        }
    }
}

/**
 * What may stand in front of a word that still counts as the word itself, folded.
 *
 * The conjunctions و ف, the prepositions ب ك ل, the future س, the article, and their usual stacks.
 * The interrogative أ is deliberately absent: it folds to a bare ا, and as a prefix it would rank
 * آمن (folded امن) as the word من.
 */
private val WORD_PREFIXES = hashSetOf(
    "", "و", "ف", "ب", "ك", "ل", "س",
    "ال", "وال", "فال", "بال", "كال", "لل", "ولل", "فلل",
    "وب", "فب", "ول", "فل", "وك", "فك", "وس", "فس",
)

/** Single endings: attached pronouns, duals, sound plurals, verb endings, and the accusative alef. */
private val SINGLE_ENDINGS = listOf(
    "ه", "ها", "هم", "هما", "هن", "ك", "كم", "كما", "كن", "نا", "ني", "ي",
    "ون", "ين", "ان", "ات", "وا", "ت", "تم", "تن", "تما", "ا",
)

/** What may follow a word that still counts as the word itself: nothing, one ending, or two in a
 *  row (يعلمونه, قالتا, رسلهم). */
private val WORD_ENDINGS: Set<String> = buildSet {
    add("")
    addAll(SINGLE_ENDINGS)
    for (a in SINGLE_ENDINGS) for (b in SINGLE_ENDINGS) add(a + b)
}

/** Fold a phrase: the words of [s] folded by [normalizeArabic], joined by single spaces. */
private fun foldPhrase(s: String): String = foldWords(s).joinToString(" ")

private fun foldWords(s: String): List<String> =
    normalizeArabic(s).split(' ').filter { it.isNotEmpty() }

/** Each space-separated token of [s], as its start and end (exclusive), empty tokens included. */
private inline fun forEachToken(s: String, action: (start: Int, end: Int) -> Unit) {
    var start = 0
    while (start <= s.length) {
        val space = s.indexOf(' ', start)
        val end = if (space < 0) s.length else space
        action(start, end)
        start = end + 1
    }
}
