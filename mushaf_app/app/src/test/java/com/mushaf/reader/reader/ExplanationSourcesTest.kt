package com.mushaf.reader.reader

import com.mushaf.reader.reader.ExplanationSources.MAX_PINNED
import com.mushaf.reader.reader.ExplanationSources.MEANINGS
import com.mushaf.reader.reader.ExplanationSources.MUKHTASAR
import com.mushaf.reader.reader.ExplanationSources.MUYASSAR
import com.mushaf.reader.reader.ExplanationSources.SAADI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplanationSourcesTest {

    private val ibnKathir = ExplanationSources.pack("ibn_kathir")

    @Test
    fun packSourcesCarryTheirPackId() {
        assertEquals("ibn_kathir", ExplanationSources.packId(ibnKathir))
        assertNull(ExplanationSources.packId(SAADI))
        assertNull(ExplanationSources.packId(MEANINGS))
    }

    @Test
    fun theOldRememberedTafsirCarriesOver() {
        assertEquals(MUYASSAR, ExplanationSources.fromLegacy("muyassar", null))
        assertEquals(MUKHTASAR, ExplanationSources.fromLegacy("mukhtasar", "tabari"))
        // «المزيد» meant whichever pack was chosen there.
        assertEquals(ExplanationSources.pack("tabari"), ExplanationSources.fromLegacy("more", "tabari"))
        assertEquals(SAADI, ExplanationSources.fromLegacy("more", null))
        assertEquals(SAADI, ExplanationSources.fromLegacy(null, null))
        assertEquals(SAADI, ExplanationSources.fromLegacy("ibn_kathir", null))
    }

    @Test
    fun aDeletedPackIsNotOpenedOn() {
        assertEquals(ibnKathir, ExplanationSources.opening(ibnKathir, setOf("ibn_kathir")))
        assertEquals(SAADI, ExplanationSources.opening(ibnKathir, emptySet()))
        assertEquals(MEANINGS, ExplanationSources.opening(MEANINGS, emptySet()))
        assertEquals(SAADI, ExplanationSources.opening("nonsense", emptySet()))
    }

    @Test
    fun nothingStoredMeansTheDefaultTabs() {
        assertEquals(ExplanationSources.DEFAULT_PINNED, ExplanationSources.parsePinned(null))
    }

    @Test
    fun pinsRoundTripAndAnEmptyListStaysEmpty() {
        val pins = listOf(MEANINGS, ibnKathir, SAADI)
        assertEquals(pins, ExplanationSources.parsePinned(ExplanationSources.encodePinned(pins)))
        assertEquals(emptyList<String>(), ExplanationSources.parsePinned(""))
    }

    @Test
    fun storedPinsAreCleaned() {
        val raw = "saadi, saadi,bogus,pack:,meanings,muyassar,mukhtasar,pack:tabari"
        assertEquals(listOf(SAADI, MEANINGS, MUYASSAR, MUKHTASAR), ExplanationSources.parsePinned(raw))
    }

    @Test
    fun pinningAddsAtTheEndAndUnpinningRemoves() {
        val pins = listOf(SAADI, MUKHTASAR)
        assertEquals(listOf(SAADI, MUKHTASAR, ibnKathir), ExplanationSources.togglePin(pins, ibnKathir))
        assertEquals(listOf(MUKHTASAR), ExplanationSources.togglePin(pins, SAADI))
    }

    @Test
    fun aFullSetOfPinsRefusesAnotherButStillUnpins() {
        val full = ExplanationSources.DEFAULT_PINNED
        assertEquals(MAX_PINNED, full.size)
        assertNull(ExplanationSources.togglePin(full, ibnKathir))
        assertEquals(full - MEANINGS, ExplanationSources.togglePin(full, MEANINGS))
    }

    @Test
    fun pinsOnDeletedPacksAreHidden() {
        val pins = listOf(SAADI, ibnKathir)
        assertEquals(listOf(SAADI), ExplanationSources.visiblePinned(pins, emptySet()))
        assertEquals(pins, ExplanationSources.visiblePinned(pins, setOf("ibn_kathir")))
    }

    @Test
    fun availability() {
        assertTrue(ExplanationSources.isAvailable(MEANINGS, emptySet()))
        assertFalse(ExplanationSources.isAvailable(ibnKathir, setOf("tabari")))
    }

    @Test
    fun pageFawaedAndSurahCardAreSourcesButNotPinnedOutOfTheBox() {
        val fawaed = ExplanationSources.PAGE_FAWAED
        val card = ExplanationSources.SURAH_CARD
        assertTrue(ExplanationSources.isAvailable(fawaed, emptySet()))
        assertTrue(ExplanationSources.isAvailable(card, emptySet()))
        assertEquals(card, ExplanationSources.opening(card, emptySet()))
        assertFalse(fawaed in ExplanationSources.DEFAULT_PINNED)
        assertFalse(card in ExplanationSources.DEFAULT_PINNED)
        assertTrue(ExplanationSources.isAvailable(ExplanationSources.TAJWEED, emptySet()))
        assertFalse(ExplanationSources.TAJWEED in ExplanationSources.DEFAULT_PINNED)
        // Pinned by the reader, they are kept like any other source.
        assertEquals(listOf(SAADI, fawaed, card), ExplanationSources.parsePinned("saadi,page_fawaed,surah_card"))
    }
}
