package com.mushaf.reader.data

import org.json.JSONObject

/** An actual printed hizb star, in the same pixel coordinates as the page image. */
data class HizbMarker(
    val page: Int,
    val verseKey: String,
    val division: QuranPagePosition.Division,
    val bounds: AyahRect,
) {
    companion object {
        fun fromJson(
            text: String,
            ayahs: List<AyahMarker>,
            imageWidth: Int,
            imageHeight: Int,
        ): List<HizbMarker> {
            val root = JSONObject(text)
            val space = root.getJSONObject("coordinate_space")
            require(space.getInt("image_width") == imageWidth)
            require(space.getInt("image_height") == imageHeight)
            val starts = ayahs.sortedWith(compareBy({ it.surahNumber }, { it.ayahNumber }))
                .distinctBy { it.rub }.associateBy { it.verseKey }
            val records = root.getJSONArray("records")
            val seen = HashSet<String>()
            return (0 until records.length()).map { index ->
                val record = records.getJSONObject(index)
                val key = record.getString("verse_key")
                val ayah = requireNotNull(starts[key])
                require(seen.add(key))
                require(record.getInt("page") == ayah.page)
                require(record.getInt("juz") == ayah.juz && record.getInt("hizb") == ayah.hizb)
                require(record.getInt("rub") == ayah.rub)
                val division = requireNotNull(QuranPagePosition.fromAyahs(listOf(ayah))).start
                val bounds = AyahRect(
                    record.getDouble("x").toFloat(), record.getDouble("y").toFloat(),
                    record.getDouble("w").toFloat(), record.getDouble("h").toFloat(),
                )
                require(bounds.x >= 0 && bounds.y >= 0 && bounds.w > 0 && bounds.h > 0)
                require(bounds.x + bounds.w <= imageWidth && bounds.y + bounds.h <= imageHeight)
                HizbMarker(ayah.page, key, division, bounds)
            }
        }
    }
}
