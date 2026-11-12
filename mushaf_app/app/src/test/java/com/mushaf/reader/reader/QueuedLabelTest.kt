package com.mushaf.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class QueuedLabelTest {

    @Test
    fun aDownloadThatHasNotTriedYetWaitsItsTurn() {
        assertEquals("في الانتظار…", queuedLabel(waitsForWifi = false, failedAttempts = 0))
    }

    @Test
    fun aFailedTrySaysItWillBeRetried() {
        assertEquals("تعذّر الاتصال، ستُعاد المحاولة تلقائيًا…", queuedLabel(waitsForWifi = false, failedAttempts = 2))
    }

    @Test
    fun waitingForWifiComesFirstThenTheRetryIsMoot() {
        assertEquals("بانتظار شبكة واي فاي", queuedLabel(waitsForWifi = true, failedAttempts = 3))
    }
}
