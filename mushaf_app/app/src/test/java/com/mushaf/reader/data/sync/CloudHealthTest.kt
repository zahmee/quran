package com.mushaf.reader.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudHealthTest {

    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000

    private fun input(
        signedIn: Boolean = true,
        syncRefusedAt: Long = 0L,
        backupRefusedAt: Long = 0L,
        outbox: List<Long> = emptyList(),
        autoBackup: Boolean = true,
        backupLastOkAt: Long = now - hour,
        lastUploadAt: Long = 0L,
    ) = CloudHealth.Input(
        signedIn = signedIn,
        syncRefusedAt = syncRefusedAt,
        syncRefusedMessage = if (syncRefusedAt > 0L) "رفض" else null,
        backupRefusedAt = backupRefusedAt,
        backupRefusedMessage = if (backupRefusedAt > 0L) "رفض" else null,
        outbox = outbox,
        autoBackup = autoBackup,
        backupLastOkAt = backupLastOkAt,
        lastUploadAt = lastUploadAt,
    )

    private fun kinds(input: CloudHealth.Input) =
        CloudHealth.problems(input, now).map { "${it.feature}:${it.kind}" }

    @Test
    fun `all well means no alert`() {
        assertEquals(emptyList<String>(), kinds(input()))
    }

    @Test
    fun `a refused sync is reported at once`() {
        assertEquals(listOf("SYNC:Refused"), kinds(input(syncRefusedAt = now - 1_000L)))
    }

    @Test
    fun `a refused backup is reported at once`() {
        assertEquals(listOf("BACKUP:Refused"), kinds(input(backupRefusedAt = now - 1_000L)))
    }

    @Test
    fun `a few hours offline stays quiet`() {
        assertEquals(emptyList<String>(), kinds(input(outbox = listOf(now - 5 * hour))))
    }

    @Test
    fun `a bookmark unsent for a day is reported with the count waiting`() {
        val problems = CloudHealth.problems(input(outbox = listOf(now - 30 * hour, now - 2 * hour)), now)
        assertEquals(1, problems.size)
        assertEquals(CloudProblem.Kind.Stalled, problems[0].kind)
        assertEquals(2, problems[0].waiting)
        assertEquals(now - 30 * hour, problems[0].since)
    }

    @Test
    fun `a refusal is the one alert for its feature, not also a stall`() {
        assertEquals(listOf("SYNC:Refused"), kinds(input(syncRefusedAt = now, outbox = listOf(now - 48 * hour))))
    }

    @Test
    fun `the daily backup missing three days is reported`() {
        assertEquals(listOf("BACKUP:Stalled"), kinds(input(backupLastOkAt = now - 73 * hour)))
    }

    @Test
    fun `a backup missing for two days stays quiet`() {
        assertEquals(emptyList<String>(), kinds(input(backupLastOkAt = now - 47 * hour)))
    }

    @Test
    fun `with the daily backup turned off, its age is no problem`() {
        assertEquals(emptyList<String>(), kinds(input(autoBackup = false, backupLastOkAt = now - 30 * 24 * hour)))
    }

    @Test
    fun `an older install falls back to its last upload`() {
        assertEquals(emptyList<String>(), kinds(input(backupLastOkAt = 0L, lastUploadAt = now - hour)))
        assertEquals(listOf("BACKUP:Stalled"), kinds(input(backupLastOkAt = 0L, lastUploadAt = now - 80 * hour)))
    }

    @Test
    fun `signed out, nothing is reported`() {
        assertEquals(emptyList<String>(), kinds(input(signedIn = false, syncRefusedAt = now, backupRefusedAt = now)))
    }
}
