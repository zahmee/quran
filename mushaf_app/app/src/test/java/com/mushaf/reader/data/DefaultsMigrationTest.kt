package com.mushaf.reader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins down the one rule the defaults migration turns on.
 *
 * Everything else in [ReadingStore.migrateDefaults] is straight-line writing; the only judgement
 * it makes is "was this install here before the defaults changed", and getting that backwards is
 * silent and expensive in both directions. Called it wrong on a fresh install and the new reader
 * is handed the old layout forever; called it wrong on an existing one and their reader is
 * rearranged under them, which is the whole thing the migration exists to prevent.
 *
 * Pure Kotlin, so it runs without a device.
 */
class DefaultsMigrationTest {

    @Test
    fun `a fresh install has written nothing`() {
        assertFalse(ReadingStore.isExistingInstall(emptySet()))
    }

    @Test
    fun `the stamp alone does not make an install an old one`() {
        // The migration writes the stamp itself. If it counted, the next migration would read its
        // own handiwork as evidence of a reader who was here all along.
        assertFalse(ReadingStore.isExistingInstall(setOf(ReadingStore.DEFAULTS_VERSION_KEY)))
    }

    @Test
    fun `any preference the reader caused counts as an existing install`() {
        // last_page is written as soon as the reader opens a page, so in practice it is the one
        // that settles this; the rule is deliberately not a list of names, so a key added later
        // cannot quietly fall outside it.
        assertTrue(ReadingStore.isExistingInstall(setOf("last_page")))
        assertTrue(ReadingStore.isExistingInstall(setOf("theme_id")))
        assertTrue(ReadingStore.isExistingInstall(setOf("some_key_added_in_a_later_version")))
    }

    @Test
    fun `the stamp beside a real preference is still an existing install`() {
        assertTrue(
            ReadingStore.isExistingInstall(
                setOf(ReadingStore.DEFAULTS_VERSION_KEY, "last_page")
            )
        )
    }

    @Test
    fun `the version is a positive stamp, not a flag`() {
        // A later change adds a branch keyed off this number, so it has to be ordered, not boolean.
        assertTrue(ReadingStore.DEFAULTS_VERSION >= 1)
    }
}
