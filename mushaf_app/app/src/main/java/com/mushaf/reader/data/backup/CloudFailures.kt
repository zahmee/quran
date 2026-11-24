package com.mushaf.reader.data.backup

/**
 * Whether a failed cloud call is a refusal — worth an alert, because it will not fix itself — or
 * a failure that passes by itself and is only waited out.
 *
 * Getting this backwards is what put «تعذّر النسخ السحابي» on a reader's screen while their phone
 * merely had a poor connection: a weak or broken line can make Firestore answer with a vague
 * error (UNKNOWN, INTERNAL, ABORTED…) rather than UNAVAILABLE, and every such answer used to be
 * filed as a refusal. A refusal is kept until a later success clears it, so the alert came back
 * each time the app did, and «أعد المحاولة» could only answer that there was no connection.
 *
 * The rule now: only an answer that can only mean "no" is a refusal. A failure on a network the
 * system has not confirmed reaches the internet, or one whose code a bad line can produce, is
 * waited out — and if it goes on, [com.mushaf.reader.data.sync.CloudHealth] still raises the
 * quieter "stuck" alert after its threshold. Pure, so every case is pinned by JVM tests.
 */
internal object CloudFailures {

    enum class Verdict {
        /** Passes by itself: retry quietly. */
        Transient,
        /** The server said no: tell the reader. */
        Refused,
    }

    /**
     * Firestore codes a poor or dropped connection can produce, or that clear on their own
     * (a busy service, a quota that resets). Anything else from Firestore — permission denied,
     * not signed in, a bad request — is the server's considered answer.
     */
    val TRANSIENT_FIRESTORE_CODES = setOf(
        "CANCELLED",
        "UNKNOWN",
        "DEADLINE_EXCEEDED",
        "RESOURCE_EXHAUSTED",
        "ABORTED",
        "INTERNAL",
        "UNAVAILABLE",
    )

    /**
     * @param networkFailure the error itself names the network (an I/O failure, an unreachable host).
     * @param workingInternet the system confirms the current network reaches the internet.
     * @param firestoreCode the Firestore status code's name, when the failure came from Firestore.
     * @param tooManyRequests the server asked to slow down, which passes with time.
     */
    fun verdict(
        networkFailure: Boolean,
        workingInternet: Boolean,
        firestoreCode: String?,
        tooManyRequests: Boolean = false,
    ): Verdict = when {
        networkFailure -> Verdict.Transient
        // Whatever the error says, a call that failed without a working line proves nothing
        // about the server.
        !workingInternet -> Verdict.Transient
        tooManyRequests -> Verdict.Transient
        firestoreCode != null && firestoreCode in TRANSIENT_FIRESTORE_CODES -> Verdict.Transient
        else -> Verdict.Refused
    }
}
