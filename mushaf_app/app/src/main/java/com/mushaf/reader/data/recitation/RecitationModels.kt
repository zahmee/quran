package com.mushaf.reader.data.recitation

import com.mushaf.reader.data.SurahAyahCounts
import org.json.JSONArray

/**
 * One recitation that MP3Quran publishes ayah timing for (a "read": a reciter in one riwayah or
 * style). Its surahs are whole files, `{folderUrl}{SSS}.mp3`, and the timing table says where each
 * ayah sits inside them — that is what lets the reader start at any ayah and follow along.
 */
data class Reciter(
    val id: Int,
    val name: String,
    val rewaya: String,
    /** Folder holding the full-surah files, always https and ending in a slash. */
    val folderUrl: String,
    val surahCount: Int,
) {
    /** All 114 surahs are recorded. Only complete recitations are offered for now. */
    val complete: Boolean get() = surahCount >= SurahAyahCounts.SURAH_COUNT

    fun surahUrl(surah: Int): String = folderUrl + surah.toString().padStart(3, '0') + ".mp3"
}

/** Where one ayah sits inside its surah's file, in milliseconds from the start of the file. */
data class AyahTiming(val ayah: Int, val startMs: Long, val endMs: Long)

/** An ayah by surah and number. */
data class AyahRef(val surah: Int, val ayah: Int) {
    val verseKey: String get() = "$surah:$ayah"
}

/** The timing table of one surah for one reciter. */
class SurahTiming(val surah: Int, ayahs: List<AyahTiming>) {

    /** Sorted by start time. An "ayah 0" entry — the opening isti'adha/basmala some recitations
     *  list on its own — is kept for lookups but never reported as an ayah. */
    val ayahs: List<AyahTiming> = ayahs.sortedBy { it.startMs }

    /** The ayah being recited at [positionMs], or null in the opening before ayah 1. */
    fun ayahAt(positionMs: Long): Int? {
        var found: AyahTiming? = null
        for (timing in ayahs) {
            if (timing.startMs <= positionMs) found = timing else break
        }
        return found?.ayah?.takeIf { it >= 1 }
    }

    /**
     * Where playback of [ayah] begins. Ayah 1 starts at the very top of the file, so the
     * isti'adha and basmala the reciter opens the surah with are heard as well.
     */
    fun startOf(ayah: Int): Long? =
        if (ayah == 1) 0L else ayahs.firstOrNull { it.ayah == ayah }?.startMs

    fun endOf(ayah: Int): Long? = ayahs.firstOrNull { it.ayah == ayah }?.endMs
}

/** Steps between ayahs across surah boundaries, by the standard ayah counts. */
object AyahSteps {

    fun next(ref: AyahRef): AyahRef? = when {
        ref.ayah < SurahAyahCounts.of(ref.surah) -> ref.copy(ayah = ref.ayah + 1)
        ref.surah < SurahAyahCounts.SURAH_COUNT -> AyahRef(ref.surah + 1, 1)
        else -> null
    }

    fun previous(ref: AyahRef): AyahRef? = when {
        ref.ayah > 1 -> ref.copy(ayah = ref.ayah - 1)
        ref.surah > 1 -> AyahRef(ref.surah - 1, SurahAyahCounts.of(ref.surah - 1))
        else -> null
    }
}

/** Parses the two MP3Quran v3 responses the reader uses. Pure, so it is unit-tested. */
object RecitationJson {

    /** `/api/v3/ayat_timing/reads`: the recitations that have ayah timing. Malformed entries and
     *  any folder that is not https are dropped rather than failing the whole list. */
    fun parseReciters(json: String): List<Reciter> {
        val array = JSONArray(json)
        val out = ArrayList<Reciter>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optInt("id", 0)
            val name = o.optString("name").trim()
            var folder = o.optString("folder_url").trim()
            if (id <= 0 || name.isEmpty() || !folder.startsWith("https://")) continue
            if (!folder.endsWith("/")) folder += "/"
            out += Reciter(
                id = id,
                name = name,
                rewaya = o.optString("rewaya").trim(),
                folderUrl = folder,
                surahCount = o.optInt("soar_count", 0),
            )
        }
        return out.distinctBy { it.id }
    }

    /** `/api/v3/ayat_timing?surah=S&read=ID`: one surah's table. Throws if nothing usable came
     *  back, so an error page or an empty answer is never cached as a timing table. */
    fun parseTiming(surah: Int, json: String): SurahTiming {
        val array = JSONArray(json)
        val out = ArrayList<AyahTiming>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val ayah = o.optInt("ayah", -1)
            val start = o.optLong("start_time", -1L)
            val end = o.optLong("end_time", -1L)
            if (ayah < 0 || start < 0 || end < start) continue
            out += AyahTiming(ayah, start, end)
        }
        val timings = out.distinctBy { it.ayah }
        require(timings.any { it.ayah >= 1 }) { "No ayah timing for surah $surah" }
        return SurahTiming(surah, timings)
    }
}
