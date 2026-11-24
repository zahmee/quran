package com.mushaf.reader.data.backup

import com.mushaf.reader.data.backup.CloudFailures.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A poor connection must never read as the server refusing: that is what put a backup alert on
 * screen, again and again, for a reader whose phone was simply offline.
 */
class CloudFailuresTest {

    private fun verdict(
        networkFailure: Boolean = false,
        workingInternet: Boolean = true,
        code: String? = null,
        tooManyRequests: Boolean = false,
    ) = CloudFailures.verdict(networkFailure, workingInternet, code, tooManyRequests)

    @Test
    fun `a failure that names the network is waited out`() {
        assertEquals(Verdict.Transient, verdict(networkFailure = true))
    }

    @Test
    fun `the vague errors a bad line produces are waited out`() {
        listOf("UNKNOWN", "INTERNAL", "ABORTED", "CANCELLED", "UNAVAILABLE", "DEADLINE_EXCEEDED").forEach {
            assertEquals(it, Verdict.Transient, verdict(code = it))
        }
    }

    @Test
    fun `a busy service and a slow-down request pass with time`() {
        assertEquals(Verdict.Transient, verdict(code = "RESOURCE_EXHAUSTED"))
        assertEquals(Verdict.Transient, verdict(tooManyRequests = true))
    }

    @Test
    fun `nothing failed on a line that does not reach the internet is a refusal`() {
        assertEquals(Verdict.Transient, verdict(workingInternet = false, code = "PERMISSION_DENIED"))
        assertEquals(Verdict.Transient, verdict(workingInternet = false))
    }

    @Test
    fun `the server's plain no, on a working line, is a refusal`() {
        assertEquals(Verdict.Refused, verdict(code = "PERMISSION_DENIED"))
        assertEquals(Verdict.Refused, verdict(code = "UNAUTHENTICATED"))
        assertEquals(Verdict.Refused, verdict(code = "INVALID_ARGUMENT"))
    }

    @Test
    fun `an unexplained failure on a working line is still told, not swallowed`() {
        assertEquals(Verdict.Refused, verdict())
    }
}
