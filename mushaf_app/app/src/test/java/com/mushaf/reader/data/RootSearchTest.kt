package com.mushaf.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootSearchTest {

    private val rahma = RootSearch.Root(
        id = 1, root = "رحم", wordCount = 339, ayahCount = 313,
        forms = RootSearch.parseForms("رحيم\t95\nرحمة\t79\nالرحمن\t48\nيرحمكم\t3"),
    )
    private val mulk = RootSearch.Root(
        id = 2, root = "ملك", wordCount = 118, ayahCount = 100,
        forms = RootSearch.parseForms("ملك\t20\nالملك\t12"),
    )
    private val malak = RootSearch.Root(
        id = 3, root = "ألك", wordCount = 88, ayahCount = 80,
        forms = RootSearch.parseForms("الملائكة\t60\nملك\t8"),
    )
    private val kitab = RootSearch.Root(
        id = 4, root = "كتب", wordCount = 319, ayahCount = 279,
        forms = RootSearch.parseForms("الكتاب\t163\nكتاب\t67"),
    )
    private val index = RootSearch.Index(listOf(rahma, mulk, malak, kitab))

    @Test
    fun formsAndOccurrencesParse() {
        assertEquals(RootSearch.Form("رحيم", 95), rahma.forms.first())
        assertEquals(4, rahma.forms.size)
        assertEquals(
            listOf(RootSearch.Occurrence(1, 3, 1, 0), RootSearch.Occurrence(2, 37, 7, 2)),
            RootSearch.parseOccurrences("1003:1:0 2037:7:2"),
        )
        assertEquals("2:37", RootSearch.parseOccurrences("2037:7:2").single().verseKey)
    }

    @Test
    fun theRootItselfMatches() {
        assertEquals(listOf(rahma), index.match("رحم"))
    }

    @Test
    fun aWordTheQuranUsesMatchesItsRoot() {
        assertEquals(listOf(rahma), index.match("الرَّحْمَٰن"))
        assertEquals(listOf(rahma), index.match("رحمة"))
        // Folded like the rest of the search: ة and ه, the hamza seats, the marks.
        assertEquals(listOf(rahma), index.match("رحمه"))
    }

    @Test
    fun aPrefixTheFormsDoNotCarryIsSetAside() {
        assertEquals(listOf(rahma), index.match("ورحمة"))
        assertEquals(listOf(kitab), index.match("وكتاب"))
        assertEquals(listOf(kitab), index.match("للكتاب"))
    }

    @Test
    fun aRootTypedAsARootComesBeforeTheRootsItIsAWordOf() {
        assertEquals(listOf(mulk, malak), index.match("ملك"))
    }

    @Test
    fun phrasesAndSingleLettersHaveNoRoot() {
        assertTrue(index.match("رب العالمين").isEmpty())
        assertTrue(index.match("ر").isEmpty())
        assertTrue(index.match("قلم").isEmpty())
    }
}
