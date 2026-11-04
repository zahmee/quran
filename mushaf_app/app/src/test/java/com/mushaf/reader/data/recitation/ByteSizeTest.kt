package com.mushaf.reader.data.recitation

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeTest {

    @Test
    fun `sizes read in Arabic with the right unit`() {
        assertEquals("٠ كيلوبايت", formatByteSize(0))
        assertEquals("١ كيلوبايت", formatByteSize(200))
        assertEquals("٨٠٠ كيلوبايت", formatByteSize(800L * 1024))
        assertEquals("١٢٠ ميغابايت", formatByteSize(120L * 1024 * 1024))
        assertEquals("١ غيغابايت", formatByteSize(1024L * 1024 * 1024))
        assertEquals("١٫٧ غيغابايت", formatByteSize((1.7 * 1024 * 1024 * 1024).toLong()))
    }
}
