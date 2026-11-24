package com.mushaf.reader.reader

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

/** Dictated words land where the reader's cursor is, as a word of their own. */
class InsertAtCursorTest {

    @Test
    fun `spoken words go after the text with a space between`() {
        val result = insertAtCursor(TextFieldValue("تأمل", TextRange(4)), "في الآية")
        assertEquals("تأمل في الآية", result.text)
        assertEquals(TextRange(result.text.length), result.selection)
    }

    @Test
    fun `no extra space after a space or a new line, or into an empty note`() {
        assertEquals("سطر\nجديد", insertAtCursor(TextFieldValue("سطر\n", TextRange(4)), "جديد").text)
        assertEquals("أ ب", insertAtCursor(TextFieldValue("أ ", TextRange(2)), "ب").text)
        assertEquals("كلام", insertAtCursor(TextFieldValue("", TextRange(0)), "كلام").text)
    }

    @Test
    fun `spoken words go into the middle and the cursor follows them`() {
        val result = insertAtCursor(TextFieldValue("أول آخر", TextRange(3)), "وسط")
        assertEquals("أول وسط آخر", result.text)
        assertEquals(TextRange(7), result.selection)
    }

    @Test
    fun `a selection is replaced by what was said`() {
        val result = insertAtCursor(TextFieldValue("قديم جديد", TextRange(0, 4)), "حديث")
        assertEquals("حديث جديد", result.text)
    }
}
