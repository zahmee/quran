package com.mushaf.reader.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The second marker used to have a header button of its own ("bookmark2"); the four markers now
 * share the one "bookmark" button. A reader's stored placement must come through that fold with
 * the bookmarks still where they could reach them.
 */
class RetiredBookmarkButtonTest {

    private fun fold(hidden: Set<String>, bar: Set<String>) =
        ReadingStore.foldRetiredBookmarkButton(hidden, bar)

    @Test
    fun `the untouched defaults show the bookmark button on the bar`() {
        val (hidden, bar) = fold(ReadingStore.DEFAULT_HIDDEN_BUTTONS, ReadingStore.DEFAULT_BAR_BUTTONS)
        assertEquals(emptySet<String>(), hidden)
        assertEquals(setOf("bookmark", "fill", "hide"), bar)
    }

    @Test
    fun `a reader who used only the second marker keeps a bookmark button`() {
        val (hidden, _) = fold(hidden = setOf("bookmark", "stats"), bar = emptySet())
        assertEquals(setOf("stats"), hidden)
    }

    @Test
    fun `a reader who hid both markers keeps the button hidden`() {
        val (hidden, _) = fold(hidden = setOf("bookmark", "bookmark2"), bar = emptySet())
        assertEquals(setOf("bookmark"), hidden)
    }

    @Test
    fun `the second marker on the bar brings the bookmark button to the bar`() {
        val (_, bar) = fold(hidden = emptySet(), bar = setOf("bookmark2", "fill"))
        assertEquals(setOf("bookmark", "fill"), bar)
    }

    @Test
    fun `the gold marker in the menu stays in the menu`() {
        val (hidden, bar) = fold(hidden = setOf("bookmark2"), bar = setOf("fill", "hide"))
        assertEquals(emptySet<String>(), hidden)
        assertEquals(setOf("fill", "hide"), bar)
    }
}
