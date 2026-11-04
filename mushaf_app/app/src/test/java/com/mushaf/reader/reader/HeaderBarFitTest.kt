package com.mushaf.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins down which header actions stay on a bar that is short of room.
 *
 * The bug this guards: the bar used to keep the first N actions in display order, and fill-screen
 * and hide-header are the LAST two, so on a tight bar (an unfolded foldable with big buttons) they
 * were the first to drop into More — the setting looked as if it never saved.
 */
class HeaderBarFitTest {

    private val all = listOf("search", "bookmark", "bookmark2", "stats", "index", "theme", "fill", "hide")

    @Test
    fun `everything fits when there is room`() {
        assertEquals(all, fitBarActions(all, slots = 8).toList())
        assertEquals(all, fitBarActions(all, slots = 20).toList())
    }

    @Test
    fun `fill and hide keep their seats on a tight bar`() {
        assertEquals(listOf("fill", "hide"), fitBarActions(all, slots = 2).toList())
        assertEquals(listOf("bookmark", "fill", "hide"), fitBarActions(all, slots = 3).toList())
    }

    @Test
    fun `the rest fill remaining slots in display order and the result keeps display order`() {
        assertEquals(
            listOf("search", "bookmark", "bookmark2", "fill", "hide"),
            fitBarActions(all, slots = 5).toList(),
        )
    }

    @Test
    fun `fill alone on a one-slot bar`() {
        assertEquals(listOf("fill"), fitBarActions(listOf("search", "stats", "fill"), slots = 1).toList())
    }

    @Test
    fun `no room means nothing on the bar`() {
        assertEquals(emptySet<String>(), fitBarActions(all, slots = 0))
        assertEquals(emptySet<String>(), fitBarActions(all, slots = -3))
    }

    @Test
    fun `slots count whole buttons and tolerate float noise`() {
        assertEquals(4, barSlots(room = 176f, slot = 44f))
        assertEquals(4, barSlots(room = 175.999f, slot = 44f))
        assertEquals(3, barSlots(room = 175f, slot = 44f))
        assertEquals(0, barSlots(room = -10f, slot = 44f))
        assertEquals(0, barSlots(room = 100f, slot = 0f))
    }
}
