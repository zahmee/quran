package com.mushaf.reader.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sign-in identity is derived from the name alone, so a reader on a second device finds their
 * account only if they can type something that folds to the same key. These tests pin the folding
 * that makes that forgiving, and the address it produces.
 */
class CloudUsernameTest {

    // ── Spellings of one name reach one account ─────────────────────────────────

    @Test
    fun `hamza forms fold together`() {
        assertEquals(CloudUsername.email("احمد"), CloudUsername.email("أحمد"))
        assertEquals(CloudUsername.email("اسراء"), CloudUsername.email("إسراء"))
    }

    @Test
    fun `harakat and tatweel are ignored`() {
        assertEquals(CloudUsername.email("محمد"), CloudUsername.email("مُحَمَّد"))
        assertEquals(CloudUsername.email("محمد"), CloudUsername.email("مـحـمـد"))
    }

    @Test
    fun `ta marbuta and alef maqsura fold`() {
        assertEquals(CloudUsername.email("فاطمه"), CloudUsername.email("فاطمة"))
        assertEquals(CloudUsername.email("هدي"), CloudUsername.email("هدى"))
    }

    @Test
    fun `latin case and surrounding spaces are ignored`() {
        assertEquals(CloudUsername.email("ahmed"), CloudUsername.email("  Ahmed "))
    }

    @Test
    fun `arabic-indic digits equal ascii digits`() {
        assertEquals(CloudUsername.email("قارئ123"), CloudUsername.email("قارئ١٢٣"))
        assertEquals(CloudUsername.email("قارئ123"), CloudUsername.email("قارئ۱۲۳"))
    }

    @Test
    fun `different names stay different`() {
        assertNotEquals(CloudUsername.email("احمد"), CloudUsername.email("محمد"))
        assertNotEquals(CloudUsername.email("reader1"), CloudUsername.email("reader2"))
    }

    // ── The address ─────────────────────────────────────────────────────────────

    @Test
    fun `the address is ascii on the reserved domain`() {
        val email = CloudUsername.email("عبدالرحمن")
        assertTrue(email.matches(Regex("^[0-9a-f]{40}@natlu\\.example\\.com$")))
    }

    @Test
    fun `the derivation never changes`() {
        // Existing accounts are found by this exact value; a change here locks every reader out.
        assertEquals(
            "6b86b273ff34fce19d6b804eff5a3f5747ada4ea@natlu.example.com",
            CloudUsername.email("1"),
        )
    }

    // ── What the form accepts ───────────────────────────────────────────────────

    @Test
    fun `ordinary names are accepted`() {
        listOf("أحمد", "reader_1", "أم.سارة", "عبد-الله", "مُحَمَّد").forEach {
            assertNull(it, CloudUsername.problemWith(it))
        }
    }

    @Test
    fun `unusable names are refused`() {
        listOf("ab", "ا".repeat(25), "عبد الله", "name!", "123", "___").forEach {
            assertNotNull(it, CloudUsername.problemWith(it))
        }
    }

    @Test
    fun `short passwords are refused`() {
        assertNotNull(CloudUsername.problemWithPassword("1234567"))
        assertNull(CloudUsername.problemWithPassword("12345678"))
    }
}
