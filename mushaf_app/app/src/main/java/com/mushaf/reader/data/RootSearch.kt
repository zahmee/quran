package com.mushaf.reader.data

/**
 * Search by root: every word of the Quran derived from a root, wherever it stands.
 *
 * The roots come from the Tafsir Center's morphology (tools/center_extras.py), one row per root
 * holding its forms and its occurrences. A reader does not need to know a root to use this: the
 * search matches the typed word against every form the Quran uses — «الرحمن»، «رحمة»، «يرحمكم» — as
 * well as against the root itself, «رحم». A form the Quran never writes with the prefix typed
 * («وكتاب») is still found once the prefix is set aside.
 *
 * Pure Kotlin (no Android types) so it can be unit-tested without a device.
 */
object RootSearch {

    /** One root as the search lists it. [forms] are most frequent first. */
    data class Root(
        val id: Int,
        val root: String,
        val wordCount: Int,
        val ayahCount: Int,
        val forms: List<Form>,
    )

    /** A form of a root's words with its prefixes set aside, and how often the Quran uses it. */
    data class Form(val text: String, val count: Int)

    /** One word under a root: its ayah, its index among the ayah's Uthmani words, and its form. */
    data class Occurrence(val surah: Int, val ayah: Int, val word: Int, val form: Int) {
        val verseKey: String get() = "$surah:$ayah"
    }

    /** At most this many roots are offered for one typed word; more is a sign it matched loosely. */
    const val MAX_SUGGESTIONS = 3

    /** «form<TAB>count» lines, as the database stores them. */
    fun parseForms(column: String): List<Form> =
        column.lineSequence().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) null else line.substring(tab + 1).toIntOrNull()?.let { Form(line.substring(0, tab), it) }
        }.toList()

    /** Space-separated «ayahKey:word:form», as the database stores them; ayahKey is surah × 1000 + ayah. */
    fun parseOccurrences(column: String): List<Occurrence> =
        column.split(' ').mapNotNull { item ->
            val parts = item.split(':')
            if (parts.size != 3) return@mapNotNull null
            val key = parts[0].toIntOrNull() ?: return@mapNotNull null
            val word = parts[1].toIntOrNull() ?: return@mapNotNull null
            val form = parts[2].toIntOrNull() ?: return@mapNotNull null
            Occurrence(key / 1000, key % 1000, word, form)
        }

    /** The prefixes a typed word may carry beyond the forms the Quran uses, longest first. */
    private val Prefixes = listOf("وال", "فال", "بال", "كال", "ولل", "فلل", "لل", "ال", "و", "ف", "ب", "ك", "ل")

    /** Typed words matched to roots, built once from every root. */
    class Index(roots: List<Root>) {
        private val byForm = HashMap<String, MutableList<Root>>()
        private val byRoot = HashMap<String, MutableList<Root>>()

        init {
            for (root in roots) {
                byRoot.getOrPut(normalizeArabic(root.root)) { ArrayList() } += root
                for (form in root.forms) {
                    val list = byForm.getOrPut(normalizeArabic(form.text).replace(" ", "")) { ArrayList() }
                    if (root !in list) list += root
                }
            }
        }

        /**
         * The roots [query] may belong to: the root it spells, else the roots of the form it is, else —
         * with a prefix set aside — the form under it. Each group is ordered by how often its root
         * occurs, and the first group that matches is the answer: «ملك» offers the roots ملك and ألك
         * both, because the Quran writes both their words that way. A phrase has no root.
         */
        fun match(query: String): List<Root> {
            val q = normalizeArabic(query.trim())
            if (q.length < 2 || ' ' in q) return emptyList()
            val found = LinkedHashSet<Root>()
            // The root typed as a root comes first; the roots it is a word of follow.
            byRoot[q]?.sortedByDescending { it.wordCount }?.let(found::addAll)
            byForm[q]?.sortedByDescending { it.wordCount }?.let(found::addAll)
            if (found.isEmpty()) {
                for (prefix in Prefixes) {
                    if (!q.startsWith(prefix) || q.length - prefix.length < 2) continue
                    val rest = q.substring(prefix.length)
                    byForm[rest]?.let(found::addAll)
                    // «للكتاب» is لِ + «الكتاب».
                    if (prefix.endsWith("لل")) byForm["ال$rest"]?.let(found::addAll)
                    if (found.isNotEmpty()) break
                }
                return found.sortedByDescending { it.wordCount }.take(MAX_SUGGESTIONS)
            }
            return found.take(MAX_SUGGESTIONS)
        }
    }
}
