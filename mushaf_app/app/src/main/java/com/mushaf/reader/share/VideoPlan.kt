package com.mushaf.reader.share

import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.data.recitation.SurahTiming

/** One picture of the video: [page]'s share of the run, with [highlight] (a verse key) lit, held
 *  for [durationMs]. */
internal data class VideoSegment(val page: Int, val highlight: String?, val durationMs: Long)

/**
 * The pictures a recitation video shows, and for how long: one per ayah, lit while it is recited,
 * each on its own page — so a run over a page break turns the page instead of shrinking both pages
 * onto one screen. Without [highlight][segments] it is one picture per page.
 *
 * Pure, so it is unit-tested.
 */
internal object VideoPlan {

    /** Shorter than this a picture would flash past: it is folded into its neighbour. Ayah timing
     *  tables are not always clean, and a two-frame picture is noise, not reading. */
    const val MIN_SEGMENT_MS = 600L

    /**
     * [ayahs] in reading order, all of [timing]'s surah; [range] the clip as cut from the recording.
     * The durations add up to the clip's exactly, so the last picture ends with the sound.
     *
     * Each ayah's picture runs from its start to the next ayah's start, so the pause after an ayah
     * stays with it; the first runs from the top of the clip, taking in any opening.
     */
    fun segments(
        ayahs: List<AyahMarker>,
        timing: SurahTiming,
        range: ClipRange,
        highlight: Boolean,
    ): List<VideoSegment> {
        if (ayahs.isEmpty() || range.durationMs <= 0) return emptyList()
        val starts = ayahs.mapIndexed { i, a ->
            if (i == 0) range.startMs
            else (timing.ayahs.firstOrNull { it.ayah == a.ayahNumber }?.startMs ?: range.startMs)
                .coerceIn(range.startMs, range.endMs)
        }
        // A table out of order would give a negative length; keep the starts non-decreasing.
        val ordered = starts.runningReduce { prev, s -> maxOf(prev, s) }

        val raw = ayahs.indices.map { i ->
            val end = if (i == ayahs.lastIndex) range.endMs else ordered[i + 1]
            VideoSegment(
                page = ayahs[i].page,
                highlight = if (highlight) ayahs[i].verseKey else null,
                durationMs = end - ordered[i],
            )
        }
        return foldShort(mergeSame(raw))
    }

    /** Neighbours showing the same picture become one. */
    private fun mergeSame(segments: List<VideoSegment>): List<VideoSegment> {
        val out = ArrayList<VideoSegment>(segments.size)
        for (s in segments) {
            val last = out.lastOrNull()
            if (last != null && last.page == s.page && last.highlight == s.highlight) {
                out[out.lastIndex] = last.copy(durationMs = last.durationMs + s.durationMs)
            } else {
                out += s
            }
        }
        return out
    }

    /** Pictures under [MIN_SEGMENT_MS] go to the one before them (the first, to the one after). */
    private fun foldShort(segments: List<VideoSegment>): List<VideoSegment> {
        if (segments.size < 2) return segments
        val out = ArrayList<VideoSegment>(segments.size)
        var carry = 0L
        for (s in segments) {
            val length = s.durationMs + carry
            carry = 0
            if (length < MIN_SEGMENT_MS) {
                val last = out.lastOrNull()
                if (last != null) out[out.lastIndex] = last.copy(durationMs = last.durationMs + length)
                else carry = length
            } else {
                out += s.copy(durationMs = length)
            }
        }
        if (out.isEmpty()) return listOf(segments.first().copy(durationMs = segments.sumOf { it.durationMs }))
        if (carry > 0) out[out.lastIndex] = out.last().let { it.copy(durationMs = it.durationMs + carry) }
        return mergeSame(out)
    }
}
