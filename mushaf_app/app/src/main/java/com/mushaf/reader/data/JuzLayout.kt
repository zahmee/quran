package com.mushaf.reader.data

/**
 * How the app divides the mushaf into juz.
 *
 * DELIBERATE: the split is kept EVEN — juz 1 is 21 pages, juz 2..29 are 20 pages each, and juz 30
 * is 23 pages. The paper mushaf is not even: juz 7 really starts on page 121 and juz 11 on page 201,
 * which is also what the bundled `ayah_regions.json` records (and therefore what the juz list in the
 * index screen shows). The even split is a product decision — it makes a daily wird divide into
 * regular chunks — so the two disagree on pages 121 and 201 ON PURPOSE.
 *
 * Do not "fix" this to match the paper layout; [JuzLayoutTest] pins the behaviour down on purpose.
 *
 * Pure Kotlin (no Android types) so it can be unit-tested, and shared so the reader header, the juz
 * progress bar, and the khatma map can never drift apart the way three private copies would.
 */
object JuzLayout {

    /** Pages in juz 1 — one more than the middle juz. */
    const val FIRST_JUZ_PAGES = 21

    /** Pages in every juz from 2 to 29. */
    const val MIDDLE_JUZ_PAGES = 20

    /** First page of the last juz; it runs from here to the end of the mushaf. */
    const val LAST_JUZ_FIRST_PAGE = 582

    /** Pages in juz 30 — the remainder of a 604-page mushaf. */
    const val LAST_JUZ_PAGES = 23

    const val JUZ_COUNT = 30

    /** Pages in the mushaf the layout is written for; used when no page count is supplied. */
    const val DEFAULT_PAGE_COUNT = 604

    /** Which juz a page falls in, its index within that juz (1-based), and that juz's page count. */
    data class Position(val juz: Int, val pageInJuz: Int, val pagesInJuz: Int)

    /** One juz as a contiguous page range, for the khatma map's per-juz grids. */
    data class Section(val number: Int, val start: Int, val end: Int) {
        val pages: List<Int> = (start..end).toList()
    }

    fun positionOf(page: Int, pageCount: Int = DEFAULT_PAGE_COUNT): Position {
        val p = page.coerceIn(1, pageCount.coerceAtLeast(1))
        return when {
            p <= FIRST_JUZ_PAGES -> Position(1, p, FIRST_JUZ_PAGES)
            p >= LAST_JUZ_FIRST_PAGE ->
                Position(JUZ_COUNT, p - LAST_JUZ_FIRST_PAGE + 1, LAST_JUZ_PAGES)
            else -> {
                val first = FIRST_JUZ_PAGES + 1
                val juz = 2 + (p - first) / MIDDLE_JUZ_PAGES
                val start = first + (juz - 2) * MIDDLE_JUZ_PAGES
                Position(juz, p - start + 1, MIDDLE_JUZ_PAGES)
            }
        }
    }

    /**
     * The mushaf as consecutive juz ranges. A mushaf shorter than 604 pages is truncated at
     * [totalPages]; a longer one gets one extra section for the leftover pages.
     */
    fun sections(totalPages: Int): List<Section> {
        if (totalPages <= 0) return emptyList()
        val sections = ArrayList<Section>(JUZ_COUNT)
        var start = 1
        for (juz in 1..JUZ_COUNT) {
            val pagesInJuz = when (juz) {
                1 -> FIRST_JUZ_PAGES
                JUZ_COUNT -> LAST_JUZ_PAGES
                else -> MIDDLE_JUZ_PAGES
            }
            val end = (start + pagesInJuz - 1).coerceAtMost(totalPages)
            if (start <= totalPages) sections.add(Section(juz, start, end))
            start = end + 1
        }
        if (start <= totalPages) sections.add(Section(sections.size + 1, start, totalPages))
        return sections
    }
}
