package com.mushaf.reader.data.recitation

/**
 * A memorisation run inside one surah: ayahs [fromAyah]..[toAyah], each heard [ayahTimes] times
 * in a row, and the whole passage heard [rangeTimes] times ([ENDLESS] = until stopped).
 * "Listen to this ayah only" is the plan with one ayah, once.
 */
data class RepeatPlan(
    val surah: Int,
    val fromAyah: Int,
    val toAyah: Int,
    val ayahTimes: Int,
    val rangeTimes: Int,
) {
    val endless: Boolean get() = rangeTimes == ENDLESS

    companion object {
        const val ENDLESS = 0

        fun single(ref: AyahRef) = RepeatPlan(ref.surah, ref.ayah, ref.ayah, 1, 1)
    }
}

/**
 * One playlist entry of a plan: a clip of the surah file holding exactly one ayah, and which
 * repetition of that ayah ([ayahPass] of [ayahTimes]) and of the passage ([rangePass] of
 * [rangeTimes]) it is. It round-trips through a media id, so the playlist describes itself even to
 * a controller that connects later and never saw the plan.
 */
data class AyahClip(
    val reciterId: Int,
    val surah: Int,
    val ayah: Int,
    val startMs: Long,
    val endMs: Long,
    val ayahPass: Int,
    val ayahTimes: Int,
    val rangePass: Int,
    val rangeTimes: Int,
) {
    val ref: AyahRef get() = AyahRef(surah, ayah)

    fun mediaId(): String =
        "clip:$reciterId:$surah:$ayah:$ayahPass:$ayahTimes:$rangePass:$rangeTimes"

    companion object {
        /** Parses [mediaId]; the clip times are not part of it and come back as 0. */
        fun fromMediaId(mediaId: String?): AyahClip? {
            val p = mediaId?.split(':') ?: return null
            if (p.size != 8 || p[0] != "clip") return null
            val n = p.drop(1).map { it.toIntOrNull() ?: return null }
            return AyahClip(n[0], n[1], n[2], 0L, 0L, n[3], n[4], n[5], n[6])
        }
    }
}

object RepeatPlanner {

    /** A playlist longer than this is refused: the whole of Al-Baqarah seven times is ~2000. */
    const val MAX_CLIPS = 4000

    /** How many clips [plan] makes. An endless plan is one pass, looped by the player. */
    fun clipCount(plan: RepeatPlan): Long {
        val ayahs = (plan.toAyah - plan.fromAyah + 1).coerceAtLeast(0).toLong()
        val passes = if (plan.endless) 1L else plan.rangeTimes.toLong()
        return ayahs * plan.ayahTimes * passes
    }

    /**
     * The clips of [plan], in play order. Each ayah is cut at its exact timing (ayah 1 too: a
     * repeated ayah should not repeat the surah's opening basmala with it). Ayahs missing from
     * [timing] are skipped.
     */
    fun clips(plan: RepeatPlan, timing: SurahTiming, reciterId: Int): List<AyahClip> {
        require(plan.surah == timing.surah) { "Plan and timing are for different surahs" }
        require(plan.fromAyah in 1..plan.toAyah && plan.ayahTimes >= 1 && plan.rangeTimes >= 0)
        require(clipCount(plan) <= MAX_CLIPS) { "Too many clips" }
        val byAyah = timing.ayahs.associateBy { it.ayah }
        val passes = if (plan.endless) 1 else plan.rangeTimes
        val out = ArrayList<AyahClip>()
        for (rangePass in 1..passes) {
            for (ayah in plan.fromAyah..plan.toAyah) {
                val t = byAyah[ayah] ?: continue
                if (t.endMs <= t.startMs) continue
                for (ayahPass in 1..plan.ayahTimes) {
                    out += AyahClip(
                        reciterId, plan.surah, ayah, t.startMs, t.endMs,
                        ayahPass, plan.ayahTimes, rangePass, plan.rangeTimes,
                    )
                }
            }
        }
        return out
    }

    /** Index of the first clip of the ayah after the one at [index] (skipping its remaining
     *  repetitions), or null at the end of the playlist. */
    fun nextAyahStart(clips: List<AyahClip?>, index: Int): Int? {
        val here = clips.getOrNull(index) ?: return null
        for (j in index + 1 until clips.size) {
            val c = clips[j] ?: continue
            if (c.ayahPass == 1 && (c.ayah != here.ayah || c.rangePass != here.rangePass)) return j
        }
        return null
    }

    /** Index of the first clip of the ayah before the one at [index], or of this ayah's first
     *  clip when it is the first ayah of the playlist. */
    fun previousAyahStart(clips: List<AyahClip?>, index: Int): Int? {
        val here = clips.getOrNull(index) ?: return null
        var start = index
        while (start > 0 && clips[start - 1]?.let { sameGroup(it, here) } == true) start--
        if (start == 0) return 0
        val before = clips[start - 1] ?: return start
        var prev = start - 1
        while (prev > 0 && clips[prev - 1]?.let { sameGroup(it, before) } == true) prev--
        return prev
    }

    private fun sameGroup(a: AyahClip, b: AyahClip) = a.ayah == b.ayah && a.rangePass == b.rangePass
}
