package com.mushaf.reader.share

import com.mushaf.reader.data.recitation.SurahTiming

/** A stretch of one surah's recording, in milliseconds from the start of the file. */
internal data class ClipRange(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Where a run of ayahs sits in its surah's recording, from the reciter's timing table. Pure, so it
 * is unit-tested; the cutting itself is [AudioClipExporter].
 */
internal object AudioClips {

    /** Longest clip shared: past this a file is large for a message, and nobody listens through. */
    const val MAX_DURATION_MS = 20 * 60 * 1000L

    /** Shortest stretch before ayah 1 that counts as an opening worth offering — the isti'adha
     *  and the basmala, recited on their own. Below it the table's ayah 1 simply starts the file. */
    const val MIN_OPENING_MS = 1_500L

    /** The reciter opens this surah with something before ayah 1, long enough to offer. */
    fun hasOpening(timing: SurahTiming): Boolean =
        (timing.ayahs.firstOrNull { it.ayah == 1 }?.startMs ?: 0L) >= MIN_OPENING_MS

    /**
     * From the start of ayah [from] to the end of ayah [to]. With [withOpening] a run from ayah 1
     * starts at the top of the file, taking in what the reciter opens the surah with.
     *
     * Null when the table lacks either ayah, or gives them no length.
     */
    fun range(timing: SurahTiming, from: Int, to: Int, withOpening: Boolean): ClipRange? {
        val first = timing.ayahs.firstOrNull { it.ayah == from } ?: return null
        val last = timing.ayahs.firstOrNull { it.ayah == to } ?: return null
        val start = if (from == 1 && withOpening) 0L else first.startMs
        return ClipRange(start, last.endMs).takeIf { it.durationMs > 0 }
    }

    /**
     * Loudness at [tUs] into a clip [durationUs] long: rising from silence over [fadeInUs], falling
     * back to it over the last [fadeOutUs]. A clip cut from the middle of a recording would
     * otherwise start and stop with a click.
     */
    fun fadeGain(tUs: Long, durationUs: Long, fadeInUs: Long, fadeOutUs: Long): Float {
        val rise = if (fadeInUs > 0) (tUs.toFloat() / fadeInUs).coerceIn(0f, 1f) else 1f
        val fall = if (fadeOutUs > 0) ((durationUs - tUs).toFloat() / fadeOutUs).coerceIn(0f, 1f) else 1f
        return minOf(rise, fall)
    }
}
