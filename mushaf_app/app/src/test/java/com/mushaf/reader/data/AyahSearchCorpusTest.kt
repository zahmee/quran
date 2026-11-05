package com.mushaf.reader.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The search run against the whole bundled mushaf rather than a handful of fixtures.
 *
 * [AyahSearchIndexTest] pins the rules; this pins what they add up to on the real text, where the
 * failures that matter — a misaligned highlight, a tier that buries the answer — only show at scale.
 */
class AyahSearchCorpusTest {

    private fun hits(query: String) = index.search(query).ayahs

    private fun marked(hit: AyahSearchIndex.Hit): List<String> =
        hit.highlights.map { hit.marker.textUthmani.substring(it.first, it.last + 1) }

    /** The alignment the highlights rely on holds for every ayah but the one it documents. */
    @Test
    fun `the two orthographies align in every ayah except 72-16`() {
        val unaligned = ayahs.filter { ayah ->
            val imlaei = normalizeArabic(ayah.textImlaei).split(' ').filter { it.isNotEmpty() }
            val uthmani = normalizeArabic(ayah.textUthmani).split(' ').filter { it.isNotEmpty() }
            alignWords(imlaei, uthmani) == null
        }
        assertEquals(listOf("72:16"), unaligned.map { it.verseKey })
    }

    /**
     * The noise the tiers exist for. رب sits inside قرب، ضرب، اقترب in hundreds of ayahs; none of
     * them may come before an ayah that has the word itself.
     */
    @Test
    fun `whole-word hits all come before partial ones`() {
        val hits = hits("رب")
        val firstPartial = hits.indexOfFirst { it.tier == SearchTier.PARTIAL }
        assertTrue(firstPartial > 0)
        assertTrue(hits.drop(firstPartial).none { it.tier == SearchTier.WORD })

        val fatiha = hits.single { it.marker.verseKey == "1:2" }
        assertEquals(SearchTier.WORD, fatiha.tier)
        assertEquals(listOf(uthmaniWords(fatiha.marker, "رب")), marked(fatiha))

        // 2:35 has رب only inside تَقْرَبَا, so it falls below 2:37's رَّبِّهِۦ despite coming first.
        val baqarah35 = hits.single { it.marker.verseKey == "2:35" }
        assertEquals(SearchTier.PARTIAL, baqarah35.tier)
        assertEquals(listOf(uthmaniWords(baqarah35.marker, "تقربا")), marked(baqarah35))
        assertTrue(hits.indexOf(baqarah35) > hits.indexOfFirst { it.marker.verseKey == "2:37" })
    }

    /** 2:54 — قوم matched in the imlaa'i يا قوم lands on the fused Uthmani يَـٰقَوْمِ, and the two
     *  neighbouring hits are marked as one stretch. */
    @Test
    fun `a highlight crosses the vocative fusion onto the right word`() {
        val hit = hits("قوم").single { it.marker.verseKey == "2:54" }
        assertEquals(listOf(uthmaniWords(hit.marker, "لقومه يقوم")), marked(hit))
    }

    /** An Uthmani text that opens with a space still marks the word, not the space before it. */
    @Test
    fun `a leading space does not shift the highlight`() {
        assertTrue(ayahs.single { it.verseKey == "2:1" }.textUthmani.startsWith(" "))
        val hit = hits("الم").single { it.marker.verseKey == "2:1" }
        assertEquals(listOf(uthmaniWords(hit.marker, "الم")), marked(hit))
    }

    /** مريم returns her surah once; the ayahs listed are the ones that name her, from anywhere. */
    @Test
    fun `a surah name does not flood the ayah results`() {
        val results = index.search("مريم")
        assertEquals(listOf(19), results.surahs.map { it.number })
        assertTrue(results.ayahs.none { it.marker.verseKey == "19:2" })
        assertTrue(results.ayahs.any { it.marker.verseKey == "3:36" })
    }

    @Test
    fun `every highlight lies inside its ayah text`() {
        for (query in listOf("الله", "من", "قال", "رب", "يا ايها", "المقربين")) {
            for (hit in hits(query)) {
                val text = hit.marker.textUthmani
                for (h in hit.highlights) {
                    assertTrue("$query ${hit.marker.verseKey}", h.first >= 0 && h.last < text.length)
                    assertTrue("$query ${hit.marker.verseKey}", text[h.first] != ' ' && text[h.last] != ' ')
                }
            }
        }
    }

    companion object {
        private val ayahs: List<AyahMarker> =
            JSONObject(File("src/main/assets/data/ayah_regions.json").readText())
                .getJSONArray("records").let { records ->
                    (0 until records.length()).map { i ->
                        val r = records.getJSONObject(i)
                        AyahMarker(
                            page = r.getInt("page"), verseKey = r.getString("verse_key"),
                            surahNumber = r.getInt("surah_number"),
                            surahNameAr = r.getString("surah_name_ar"), surahNameEn = "",
                            ayahNumber = r.getInt("ayah_number"),
                            textUthmani = r.getString("text_uthmani"),
                            textImlaei = r.getString("text_imlaei"),
                            juz = 0, hizb = 0, rub = 0, isSajdah = false, sajdahNumber = 0,
                            centerX = 0f, centerY = 0f, rects = emptyList(),
                        )
                    }
                }

        private val index = AyahSearchIndex.build(ayahs)
    }
}
