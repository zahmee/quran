package com.mushaf.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class AnaaMenuTest {

    private val all = AnaaShortcut.entries.mapTo(HashSet()) { it.id }

    @Test
    fun missingAnaaShowsNothing() {
        assertEquals(AnaaMenu.None, anaaMenu(AnaaStatus.Missing, showInMenu = true, shortcutIds = all))
    }

    @Test
    fun turnedOffShowsNothing() {
        assertEquals(AnaaMenu.None, anaaMenu(AnaaStatus.Ready, showInMenu = false, shortcutIds = all))
        assertEquals(AnaaMenu.None, anaaMenu(AnaaStatus.Old, showInMenu = false, shortcutIds = all))
    }

    @Test
    fun oldAnaaOffersOnlyItsHomeScreen() {
        assertEquals(AnaaMenu.OpenOnly, anaaMenu(AnaaStatus.Old, showInMenu = true, shortcutIds = all))
    }

    @Test
    fun noShortcutChosenFallsBackToOneItem() {
        assertEquals(AnaaMenu.OpenOnly, anaaMenu(AnaaStatus.Ready, showInMenu = true, shortcutIds = emptySet()))
        assertEquals(AnaaMenu.OpenOnly, anaaMenu(AnaaStatus.Ready, showInMenu = true, shortcutIds = setOf("gone")))
    }

    @Test
    fun shortcutsFollowTheFixedOrderNotTheSetsOrder() {
        val menu = anaaMenu(AnaaStatus.Ready, showInMenu = true, shortcutIds = linkedSetOf("qibla", "tasbih", "calendar"))
        assertEquals(
            AnaaMenu.Shortcuts(listOf(AnaaShortcut.Tasbih, AnaaShortcut.Calendar, AnaaShortcut.Qibla)),
            menu
        )
    }

    @Test
    fun defaultsAreTasbihAndPrayer() {
        assertEquals(
            AnaaMenu.Shortcuts(listOf(AnaaShortcut.Tasbih, AnaaShortcut.Prayer)),
            anaaMenu(AnaaStatus.Ready, showInMenu = true, shortcutIds = AnaaShortcut.DEFAULT_IDS)
        )
    }

    /** These are Ana'a's published contract; changing one breaks every installed copy. */
    @Test
    fun destinationsMatchAnaasContract() {
        assertEquals(
            listOf("tasbih", "prayer", "adhkar", "calendar", "qibla", "today"),
            AnaaShortcut.entries.map { it.id }
        )
        assertEquals("com.sakinah.tasbih.action.OPEN", AnaaContract.ACTION_OPEN)
        assertEquals("com.sakinah.tasbih.extra.DESTINATION", AnaaContract.EXTRA_DESTINATION)
        assertEquals("com.sakinah.tasbih", AnaaContract.PACKAGE)
    }
}
