package com.mushaf.reader.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudBackupRetentionTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun backup(id: String, ageDays: Double, device: String = "phone") = CloudBackupInfo(
        id = id,
        createdAt = now - (ageDays * day).toLong(),
        deviceId = device,
        deviceName = device,
        appVersion = "0.13.0",
        sizeBytes = 1_000L,
        auto = true,
        fromThisDevice = device == "phone",
    )

    private fun stale(vararg backups: CloudBackupInfo): List<String> =
        CloudBackupRetention.stale(backups.toList(), now).map { it.id }.sorted()

    @Test
    fun `copies of the last seven days all stay`() {
        assertEquals(emptyList<String>(), stale(backup("a", 0.0), backup("b", 3.0), backup("c", 6.9)))
    }

    @Test
    fun `a copy older than seven days goes`() {
        assertEquals(listOf("c", "d"), stale(backup("a", 0.0), backup("b", 6.9), backup("c", 7.1), backup("d", 40.0)))
    }

    @Test
    fun `exactly seven days old still stays`() {
        assertEquals(emptyList<String>(), stale(backup("a", 0.0), backup("b", 7.0)))
    }

    @Test
    fun `a device keeps its newest copy however old it is`() {
        assertEquals(listOf("b"), stale(backup("a", 30.0), backup("b", 31.0)))
        assertEquals(emptyList<String>(), stale(backup("a", 300.0)))
    }

    @Test
    fun `another device's only copy survives this device's daily uploads`() {
        val result = stale(
            backup("phone-new", 0.0),
            backup("phone-old", 9.0),
            backup("lost-tablet", 90.0, device = "tablet"),
        )
        assertEquals(listOf("phone-old"), result)
    }

    @Test
    fun `another device's older copies go too`() {
        val result = stale(
            backup("phone", 0.0),
            backup("tablet-new", 20.0, device = "tablet"),
            backup("tablet-old", 21.0, device = "tablet"),
        )
        assertEquals(listOf("tablet-old"), result)
    }

    @Test
    fun `a week of uploading by hand cannot grow past the cap`() {
        val many = List(CloudBackupRetention.MAX_PER_DEVICE + 5) { backup("n$it", it / 100.0) }
        val result = CloudBackupRetention.stale(many, now).map { it.id }.toSet()
        assertEquals((CloudBackupRetention.MAX_PER_DEVICE until many.size).map { "n$it" }.toSet(), result)
    }

    @Test
    fun `nothing to keep or erase in an empty account`() {
        assertEquals(emptyList<String>(), stale())
        assertNull(CloudBackupRetention.newest(emptyList()))
    }

    @Test
    fun `the newest copy of the account is across all its devices`() {
        val newest = CloudBackupRetention.newest(
            listOf(backup("old", 3.0), backup("new", 1.0, device = "tablet"), backup("mid", 2.0)),
        )
        assertEquals("new", newest?.id)
    }
}
