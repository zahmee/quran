package com.mushaf.reader.data

import android.content.Context
import org.json.JSONObject

/**
 * Loads enriched per-ayah data + approximate highlight regions from the bundled
 * `ayah_regions.json` (generated offline by build_ayah_regions.py) for all 604 pages.
 * [loadAll] is heavy (~all 6236 ayahs) and is meant to be called off the main thread.
 */
class AyahRepository(private val context: Context) {

    data class Data(
        val byPage: Map<Int, List<AyahMarker>>,
        val keyToPage: Map<String, Int>,
        val imageWidth: Int,
        val imageHeight: Int,
    )

    fun loadAll(): Data {
        val text = runCatching {
            context.assets.open("data/ayah_regions.json")
                .bufferedReader().use { it.readText() }
        }.getOrNull() ?: return Data(emptyMap(), emptyMap(), 1106, 1789)

        val root = JSONObject(text)
        val cs = root.optJSONObject("coordinate_space")
        val imageWidth = cs?.optInt("image_width", 1106) ?: 1106
        val imageHeight = cs?.optInt("image_height", 1789) ?: 1789

        val records = root.optJSONArray("records")
            ?: return Data(emptyMap(), emptyMap(), imageWidth, imageHeight)
        val list = ArrayList<AyahMarker>(records.length())
        for (i in 0 until records.length()) {
            val r = records.optJSONObject(i) ?: continue
            val rectsArr = r.optJSONArray("rects")
            val rects = ArrayList<AyahRect>(rectsArr?.length() ?: 0)
            if (rectsArr != null) {
                for (j in 0 until rectsArr.length()) {
                    val rc = rectsArr.getJSONObject(j)
                    rects.add(
                        AyahRect(
                            x = rc.getDouble("x").toFloat(),
                            y = rc.getDouble("y").toFloat(),
                            w = rc.getDouble("w").toFloat(),
                            h = rc.getDouble("h").toFloat(),
                        )
                    )
                }
            }
            list.add(
                AyahMarker(
                    page = r.getInt("page"),
                    verseKey = r.optString("verse_key"),
                    surahNumber = r.getInt("surah_number"),
                    surahNameAr = r.getString("surah_name_ar"),
                    surahNameEn = r.optString("surah_name_en"),
                    ayahNumber = r.getInt("ayah_number"),
                    textUthmani = r.optString("text_uthmani"),
                    juz = r.optInt("juz"),
                    hizb = r.optInt("hizb"),
                    rub = r.optInt("rub"),
                    isSajdah = r.optBoolean("is_sajdah", false),
                    sajdahNumber = r.optInt("sajdah_number", 0),
                    centerX = r.getDouble("center_x").toFloat(),
                    centerY = r.getDouble("center_y").toFloat(),
                    rects = rects,
                )
            )
        }
        return Data(
            byPage = list.groupBy { it.page },
            keyToPage = list.associate { it.verseKey to it.page },
            imageWidth = imageWidth,
            imageHeight = imageHeight,
        )
    }
}
