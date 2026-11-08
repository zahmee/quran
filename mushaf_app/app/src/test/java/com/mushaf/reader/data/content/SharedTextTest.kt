package com.mushaf.reader.data.content

import com.mushaf.reader.reader.TafsirChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedTextTest {

    @Test
    fun shortTextIsLeftAlone() {
        assertEquals("نص قصير", SharedText.limit("نص قصير"))
    }

    @Test
    fun longTextIsCutAtAWordAndMarked() {
        val text = "كلمة ".repeat(10_000)
        val cut = SharedText.limit(text, max = 1_000)
        assertTrue(cut.endsWith(SharedText.CUT_MARK))
        val body = cut.removeSuffix(SharedText.CUT_MARK)
        assertTrue(body.length <= 1_000)
        assertTrue(body.split(' ').all { it == "كلمة" })
    }

    @Test
    fun theMoreTabIsRememberedAndUnknownChoicesFallBackToSaadi() {
        assertEquals(TafsirChoice.More, TafsirChoice.fromId("more"))
        assertEquals(TafsirChoice.Saadi, TafsirChoice.fromId("ibn_kathir"))
        assertEquals(TafsirChoice.Saadi, TafsirChoice.fromId(null))
    }
}
