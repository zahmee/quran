package com.mushaf.reader.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class MuyassarTextTest {

    @Test
    fun theSourceStandInsBecomeOrnateBrackets() {
        // The bundled Muyassar row for 1:3, verbatim.
        val raw = "[3] <span class='aya'>ﵡٱلرَّحۡمَٰنِﵠ</span> ذي الرحمة العامة، " +
            "<span class='aya'>ﵡٱلرَّحِيمِﵠ</span> بالمؤمنين."
        val fixed = MuyassarText.normalize(raw)
        assertEquals(
            "[3] <span class='aya'>﴿ٱلرَّحۡمَٰنِ﴾</span> ذي الرحمة العامة، " +
                "<span class='aya'>﴿ٱلرَّحِيمِ﴾</span> بالمؤمنين.",
            fixed,
        )
        assertFalse('ﵡ' in fixed || 'ﵠ' in fixed)
    }

    @Test
    fun openingAndClosingAreTheOnesTheOtherSourcesUse() {
        // The word meanings and the Mukhtasar open a quote with U+FD3F and close it with U+FD3E.
        assertEquals("﴿آمين﴾", MuyassarText.normalize("ﵡآمينﵠ"))
    }

    @Test
    fun otherPresentationFormsAreLeftAlone() {
        // «بـمكة» is written with the initial-form beh on purpose; it draws correctly.
        val raw = "إلى الكعبة ﺑ«مكة»، إلا لنعلم"
        assertSame(raw, MuyassarText.normalize(raw))
    }
}
