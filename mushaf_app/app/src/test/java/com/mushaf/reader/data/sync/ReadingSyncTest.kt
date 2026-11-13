package com.mushaf.reader.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingSyncTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val day = 24L * 60 * minute

    private fun device(
        id: String,
        page: Int? = null,
        pageAt: Long = 0L,
        bookmark: String? = null,
        bookmarkPage: Int = 1,
        bookmarkAt: Long = 0L,
    ) = DevicePlace(
        deviceId = id,
        deviceName = id,
        lastPage = page?.let { PlacePoint(it, null, pageAt) },
        bookmark = bookmark?.let { PlacePoint(bookmarkPage, it, bookmarkAt) },
    )

    private fun elsewhere(
        vararg devices: DevicePlace,
        localLatestAt: Long = now - 60 * minute,
        currentPage: Int = 10,
        localBookmark: String? = null,
        dismissed: String? = null,
    ) = ReadingSync.elsewhere(
        thisDeviceId = "phone",
        localLatestAt = localLatestAt,
        currentPage = currentPage,
        localBookmarkKey = localBookmark,
        devices = devices.toList(),
        dismissedKey = dismissed,
    )

    @Test
    fun `a newer bookmark on the tablet is offered`() {
        val offer = elsewhere(device("tablet", bookmark = "2:255", bookmarkPage = 42, bookmarkAt = now))
        assertEquals("tablet", offer?.deviceId)
        assertEquals(PlacePoint(42, "2:255", now), offer?.bookmark)
    }

    @Test
    fun `this device's own record is never offered back`() {
        assertNull(elsewhere(device("phone", page = 50, pageAt = now)))
    }

    @Test
    fun `nothing newer than reading here is offered`() {
        assertNull(
            elsewhere(
                device("tablet", page = 50, pageAt = now - 2 * 60 * minute),
                localLatestAt = now - 60 * minute,
            )
        )
    }

    @Test
    fun `the page already open is no news`() {
        assertNull(elsewhere(device("tablet", page = 10, pageAt = now), currentPage = 10))
    }

    @Test
    fun `the bookmark this device already holds is no news`() {
        assertNull(elsewhere(device("tablet", bookmark = "2:255", bookmarkAt = now), localBookmark = "2:255"))
    }

    @Test
    fun `page and bookmark in different places are both offered`() {
        val offer = elsewhere(
            device("tablet", page = 60, pageAt = now, bookmark = "2:255", bookmarkPage = 42, bookmarkAt = now - minute),
        )
        assertEquals(60, offer?.lastPage?.page)
        assertEquals(42, offer?.bookmark?.page)
    }

    @Test
    fun `page and bookmark on the same page make one offer, the bookmark`() {
        val offer = elsewhere(
            device("tablet", page = 42, pageAt = now, bookmark = "2:255", bookmarkPage = 42, bookmarkAt = now - minute),
        )
        assertNull(offer?.lastPage)
        assertEquals("2:255", offer?.bookmark?.verseKey)
    }

    @Test
    fun `of two other devices the most recent wins`() {
        val offer = elsewhere(
            device("tablet", page = 30, pageAt = now - 10 * minute),
            device("laptop", page = 80, pageAt = now - 5 * minute),
        )
        assertEquals("laptop", offer?.deviceId)
    }

    @Test
    fun `a dismissed offer stays dismissed until that device moves on`() {
        val tablet = device("tablet", page = 30, pageAt = now)
        val first = elsewhere(tablet)
        assertNotNull(first)
        assertNull(elsewhere(tablet, dismissed = first!!.key))
        val movedOn = device("tablet", page = 31, pageAt = now + minute)
        assertNotNull(elsewhere(movedOn, dismissed = first.key))
    }

    @Test
    fun `a bookmark set offline earlier loses to a later page read elsewhere`() {
        // Tablet set a bookmark at 10:00 while offline and uploaded it at 12:00; the phone read
        // at 11:00. Ordering is by when it happened, not by when it arrived.
        val offer = elsewhere(
            device("tablet", bookmark = "3:1", bookmarkPage = 50, bookmarkAt = now - 2 * 60 * minute),
            localLatestAt = now - 60 * minute,
        )
        assertNull(offer)
    }

    @Test
    fun `week-old bookmarks go, except each device's newest`() {
        fun mark(id: String, ageDays: Double, device: String) =
            MarkRecord(id, "1:1", 1, now - (ageDays * day).toLong(), device, device)
        val stale = ReadingSync.staleMarks(
            listOf(
                mark("a", 0.5, "phone"),
                mark("b", 8.0, "phone"),
                mark("c", 30.0, "tablet"),
                mark("d", 40.0, "tablet"),
                mark("e", 6.0, "tablet"),
            ),
            now,
        ).map { it.id }.sorted()
        // Tablet's newest ("e") and phone's newest ("a") stay; so does nothing under a week.
        assertEquals(listOf("b", "c", "d"), stale)
    }

    @Test
    fun `a device left alone keeps its last bookmark however old`() {
        val old = MarkRecord("x", "1:1", 1, now - 90 * day, "tablet", "tablet")
        assertEquals(emptyList<MarkRecord>(), ReadingSync.staleMarks(listOf(old), now))
    }

    @Test
    fun `clock offset is measured from the round trip midpoint`() {
        // Local clock 5 minutes slow: the server stamped 10:05 while the device read 10:00.
        val sent = now
        val received = now + 2_000L
        val server = now + 1_000L + 5 * minute
        assertEquals(5 * minute, ReadingSync.clockOffset(sent, received, server))
    }

    @Test
    fun `the same khatma gets the same id on every device`() {
        assertEquals(khatmaId(100L, 50L), KhatmaRecord(100L, 50L, 30, 604, "tablet", "tablet").id)
    }
}
