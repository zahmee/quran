package com.mushaf.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplanationTextScaleTest {

    @Test
    fun `missing value is the default size`() {
        assertEquals(100, ExplanationTextScale.normalize(null))
    }

    @Test
    fun `an off-step stored value snaps to the nearest step`() {
        assertEquals(115, ExplanationTextScale.normalize(120))
        assertEquals(175, ExplanationTextScale.normalize(400))
        assertEquals(85, ExplanationTextScale.normalize(10))
    }

    @Test
    fun `steps move one size at a time and stop at the ends`() {
        assertEquals(115, ExplanationTextScale.step(100, +1))
        assertEquals(85, ExplanationTextScale.step(100, -1))
        assertEquals(175, ExplanationTextScale.step(175, +1))
        assertEquals(85, ExplanationTextScale.step(85, -1))
    }

    @Test
    fun `the ends disable the matching button`() {
        assertFalse(ExplanationTextScale.canGrow(175))
        assertTrue(ExplanationTextScale.canShrink(175))
        assertFalse(ExplanationTextScale.canShrink(85))
        assertTrue(ExplanationTextScale.canGrow(85))
    }
}
