package com.mushaf.reader.data

/** The printed divisions at the start of a page and, if present, a boundary inside it.
 * Unlike the equal-page reading plan, these values come from the bundled ayah metadata. */
data class QuranPagePosition(val start: Division, val boundary: Division?) {
    data class Division(val juz: Int, val hizb: Int, val quarter: Int)

    companion object {
        fun fromAyahs(ayahs: List<AyahMarker>): QuranPagePosition? {
            if (ayahs.isEmpty()) return null
            val ordered = ayahs.sortedWith(compareBy({ it.surahNumber }, { it.ayahNumber }))
            // Missing or inconsistent metadata must not turn into a plausible but wrong label.
            if (ordered.any {
                it.juz !in 1..30 || it.hizb !in 1..60 || it.rub !in 1..240 ||
                    (it.hizb - 1) / 2 + 1 != it.juz || (it.rub - 1) / 4 + 1 != it.hizb
            }) return null
            val divisions = ordered.map {
                Division(it.juz, it.hizb, (it.rub - 1) % 4 + 1)
            }.distinct()
            return QuranPagePosition(divisions.first(), divisions.getOrNull(1))
        }
    }
}
