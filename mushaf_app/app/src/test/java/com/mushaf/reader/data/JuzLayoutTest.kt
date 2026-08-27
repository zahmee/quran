package com.mushaf.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins the juz split down.
 *
 * The important cases are the two where the app DELIBERATELY differs from the paper mushaf. They
 * are asserted, not tolerated: if someone later "corrects" the layout to the printed boundaries,
 * these fail and point at the decision instead of at a bug.
 */
class JuzLayoutTest {

    private fun assertPosition(page: Int, juz: Int, pageInJuz: Int, pagesInJuz: Int) {
        assertEquals(
            "page $page",
            JuzLayout.Position(juz, pageInJuz, pagesInJuz),
            JuzLayout.positionOf(page),
        )
    }

    @Test
    fun `first juz is twenty one pages`() {
        assertPosition(page = 1, juz = 1, pageInJuz = 1, pagesInJuz = 21)
        assertPosition(page = 21, juz = 1, pageInJuz = 21, pagesInJuz = 21)
    }

    @Test
    fun `middle juz are twenty pages each`() {
        assertPosition(page = 22, juz = 2, pageInJuz = 1, pagesInJuz = 20)
        assertPosition(page = 41, juz = 2, pageInJuz = 20, pagesInJuz = 20)
        assertPosition(page = 42, juz = 3, pageInJuz = 1, pagesInJuz = 20)
        assertPosition(page = 562, juz = 29, pageInJuz = 1, pagesInJuz = 20)
        assertPosition(page = 581, juz = 29, pageInJuz = 20, pagesInJuz = 20)
    }

    @Test
    fun `last juz runs to the end of the mushaf`() {
        assertPosition(page = 582, juz = 30, pageInJuz = 1, pagesInJuz = 23)
        assertPosition(page = 604, juz = 30, pageInJuz = 23, pagesInJuz = 23)
    }

    /**
     * The paper mushaf starts juz 7 on page 121, and so does the bundled ayah data that feeds the
     * index screen. The app's even split puts page 121 at the end of juz 6 instead. That is the
     * product decision, not an oversight — see [JuzLayout].
     */
    @Test
    fun `even split deliberately differs from the paper mushaf at juz seven`() {
        assertPosition(page = 121, juz = 6, pageInJuz = 20, pagesInJuz = 20)
        assertPosition(page = 122, juz = 7, pageInJuz = 1, pagesInJuz = 20)
        assertNotEquals(7, JuzLayout.positionOf(121).juz)
    }

    /** Same deliberate divergence at the other uneven boundary: juz 11 starts on page 201 on paper. */
    @Test
    fun `even split deliberately differs from the paper mushaf at juz eleven`() {
        assertPosition(page = 201, juz = 10, pageInJuz = 20, pagesInJuz = 20)
        assertPosition(page = 202, juz = 11, pageInJuz = 1, pagesInJuz = 20)
        assertNotEquals(11, JuzLayout.positionOf(201).juz)
    }

    @Test
    fun `pages outside the mushaf are clamped instead of throwing`() {
        assertEquals(JuzLayout.positionOf(1), JuzLayout.positionOf(0))
        assertEquals(JuzLayout.positionOf(1), JuzLayout.positionOf(-40))
        assertEquals(JuzLayout.positionOf(604), JuzLayout.positionOf(9999))
    }

    @Test
    fun `sections cover every page exactly once and stay contiguous`() {
        val sections = JuzLayout.sections(604)
        assertEquals(30, sections.size)
        assertEquals(1, sections.first().start)
        assertEquals(604, sections.last().end)
        sections.zipWithNext { a, b -> assertEquals("after juz ${a.number}", a.end + 1, b.start) }
        assertEquals(604, sections.sumOf { it.pages.size })
        assertEquals((1..30).toList(), sections.map { it.number })
    }

    @Test
    fun `sections agree with the position of every page`() {
        for (section in JuzLayout.sections(604)) {
            for (page in section.pages) {
                assertEquals("page $page", section.number, JuzLayout.positionOf(page).juz)
            }
        }
    }

    @Test
    fun `a short mushaf is truncated rather than padded`() {
        val sections = JuzLayout.sections(30)
        assertEquals(2, sections.size)
        assertEquals(JuzLayout.Section(1, 1, 21), sections[0])
        assertEquals(JuzLayout.Section(2, 22, 30), sections[1])
        assertEquals(emptyList<JuzLayout.Section>(), JuzLayout.sections(0))
    }
}
