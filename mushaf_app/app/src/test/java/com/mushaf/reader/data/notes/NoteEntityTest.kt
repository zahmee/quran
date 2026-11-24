package com.mushaf.reader.data.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a note may stand, and what it keeps of what was typed. */
class NoteEntityTest {

    @Test
    fun `an ayah and a whole surah are places a note can stand`() {
        assertTrue(NoteEntity.isValidPlace(2, 255))
        assertTrue(NoteEntity.isValidPlace(114, 6))
        assertTrue(NoteEntity.isValidPlace(1, NoteEntity.SURAH_NOTE))
    }

    @Test
    fun `nothing past a surah's last ayah or outside the mushaf is`() {
        assertFalse(NoteEntity.isValidPlace(1, 8))
        assertFalse(NoteEntity.isValidPlace(0, 1))
        assertFalse(NoteEntity.isValidPlace(115, 0))
        assertFalse(NoteEntity.isValidPlace(2, -1))
    }

    @Test
    fun `a surah note has no verse key, an ayah note has its own`() {
        assertNull(NoteEntity(2, NoteEntity.SURAH_NOTE, "x", 0, 0).verseKey)
        assertEquals("2:255", NoteEntity(2, 255, "x", 0, 0).verseKey)
    }

    @Test
    fun `a note keeps its inner lines but not the blank around it`() {
        assertEquals("سطر\n\nسطر", cleanNoteText("\n  سطر\n\nسطر  \n"))
        assertEquals("", cleanNoteText("   \n "))
        assertEquals(NoteEntity.MAX_TEXT, cleanNoteText("ا".repeat(NoteEntity.MAX_TEXT + 50)).length)
    }
}
