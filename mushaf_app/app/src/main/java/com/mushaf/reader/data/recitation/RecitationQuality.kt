package com.mushaf.reader.data.recitation

/**
 * Recitations the reader is not offered, and why. From `mushaf_app/tools/check_recitation_timing.py`
 * run over all 113 complete MP3Quran recitations with ayah timing on 2026-11-04 (report:
 * `docs/audits/recitation-timing-2026-11-04.md`). A recitation is hidden when its files are not
 * constant-bitrate (starting at an ayah would land seconds off) or when two or more of six sampled
 * ayah starts in Al-Baqarah fall inside the recitation rather than in the pause before it (the first
 * syllable would be cut, and the highlight would lag).
 *
 * Rerun the tool when MP3Quran adds recitations, and update this list from its report.
 */
object RecitationQuality {

    val HIDDEN: Map<Int, String> = mapOf(
        62 to "variable bitrate",   // عبدالله عواد الجهني
        259 to "variable bitrate",  // أحمد النفيس
        67 to "ayah starts miss",   // عبدالمحسن القاسم
        74 to "ayah starts miss",   // علي بن عبدالرحمن الحذيفي
        88 to "ayah starts miss",   // نعمة الحسان
        119 to "ayah starts miss",  // محمود خليل الحصري — المصحف المجود
        120 to "ayah starts miss",  // محمود خليل الحصري — ورش عن نافع
        122 to "ayah starts miss",  // محمود علي البنا — المصحف المجود
        134 to "ayah starts miss",  // محمد سايد — ورش عن نافع
        288 to "ayah starts miss",  // مصطفى إسماعيل — المصحف المجود
        300 to "ayah starts miss",  // صالح الشمراني
    )

    /** Offered in the reciter list: all 114 surahs recorded, and not hidden above. */
    fun usable(reciter: Reciter): Boolean = reciter.complete && reciter.id !in HIDDEN
}
