package com.mushaf.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins down why the index carries BOTH orthographies of the mushaf.
 *
 * Each test names the failure it prevents. Between them they cover the two directions: words the
 * Uthmani script cannot match, and words the imlaa'i text cannot match. If someone later drops one
 * of the two indexes to save memory, the half they dropped fails here.
 *
 * The ayah strings are real mushaf text copied out of assets/data/ayah_regions.json, not retyped.
 */
class AyahSearchIndexTest {

    // Built in mushaf order, which is the order the reader supplies (pages, then ayahs within one).
    private val index = AyahSearchIndex.build(
        listOf(BAQARAH_251, BAQARAH_255, NISA_172, WAQIAH_11)
    )

    private fun keys(query: String): List<String> =
        index.search(query).map { it.marker.verseKey }

    /** Keys of the exact tier only — what the reader sees above the "نتائج قريبة" line. */
    private fun exactKeys(query: String): List<String> =
        index.search(query).filterNot { it.expanded }.map { it.marker.verseKey }

    /** Keys the stemmed tier added, in order, below the line. */
    private fun expandedKeys(query: String): List<String> =
        index.search(query).filter { it.expanded }.map { it.marker.verseKey }

    // ── The bug the reader reported ─────────────────────────────────────────────

    /**
     * The mushaf writes الملائكة with a superscript alef, so the folded Uthmani text reads
     * «المليكه» while the typed query folds to «الملايكه». Only the imlaa'i text bridges that.
     */
    @Test
    fun `a dagger-alef word is found, though only the imlaai text can match it`() {
        assertEquals(listOf("4:172"), keys("الملائكة"))
        assertFalse(
            "لو طابق الرسم العثماني لبطل سبب وجود الفهرس الثاني",
            normalizeArabic(NISA_172.textUthmani).contains(normalizeArabic("الملائكة")),
        )
        assertTrue(normalizeArabic(NISA_172.textImlaei).contains(normalizeArabic("الملائكة")))
    }

    /**
     * أولئك carries U+06DF between the waw and the lam. Unicode calls the block it belongs to
     * annotation, the old fold left it in place, and the word became unsearchable.
     */
    @Test
    fun `a word interrupted by a quranic annotation sign is found`() {
        assertEquals(listOf("56:11"), keys("أولئك"))
    }

    // ── The other direction: what the imlaai text alone would lose ──────────────

    /**
     * The imlaa'i text spells دَاوُودَ with two waws, so it cannot match داود. The Uthmani
     * دَاوُۥدَ can, because its small waw folds away — this is why the Uthmani index stays.
     */
    @Test
    fun `a small-waw word is found, though only the uthmani text can match it`() {
        assertEquals(listOf("2:251"), keys("داود"))
        assertFalse(
            "لو طابق النص الإملائي لبطل سبب بقاء الفهرس العثماني",
            normalizeArabic(BAQARAH_251.textImlaei).contains(normalizeArabic("داود")),
        )
        assertTrue(normalizeArabic(BAQARAH_251.textUthmani).contains(normalizeArabic("داود")))
    }

    /** Both directions inside a single ayah: العالمين needs the imlaa'i, داود needs the Uthmani. */
    @Test
    fun `one ayah can need each index for a different word`() {
        assertEquals(listOf("2:251"), keys("داود"))
        assertEquals(listOf("2:251"), keys("العالمين"))
    }

    // ── Behaviour that must not regress ─────────────────────────────────────────

    @Test
    fun `an unvocalised query matches the vocalised mushaf`() {
        assertEquals(listOf("2:255"), keys("الحي القيوم"))
    }

    @Test
    fun `a verse key resolves to that single ayah`() {
        assertEquals(listOf("2:255"), keys("2:255"))
        assertEquals(listOf("56:11"), keys(" 56 : 11 "))
    }

    @Test
    fun `an unresolvable verse key falls through to the text scan instead of ending the search`() {
        assertEquals(emptyList<String>(), keys("2:1000"))
    }

    @Test
    fun `a surah name matches its ayahs`() {
        assertEquals(listOf("56:11"), keys("الواقعة"))
    }

    @Test
    fun `blank queries return nothing`() {
        assertEquals(emptyList<String>(), keys(""))
        assertEquals(emptyList<String>(), keys("   "))
    }

    @Test
    fun `the limit caps the results and a non-positive limit returns nothing`() {
        assertEquals(1, index.search("الله", limit = 1).size)
        assertTrue(index.search("الله", limit = 0).isEmpty())
    }

    // ── The two tiers ───────────────────────────────────────────────────────────

    /**
     * The point of the stemmed pass: the query and the mushaf carry DIFFERENT endings, so no
     * substring of one occurs in the other. المقربين against ٱلْمُقَرَّبُونَ.
     */
    @Test
    fun `a differently-inflected query is reached only by the stemmed pass`() {
        assertEquals(emptyList<String>(), exactKeys("المقربين"))
        assertEquals(listOf("4:172", "56:11"), expandedKeys("المقربين"))
    }

    /** An exact hit is never displaced by a widened one, however many the stemmer finds. */
    @Test
    fun `exact hits come first and are not flagged`() {
        val hits = index.search("المقربون")
        assertEquals(listOf("4:172", "56:11"), hits.filterNot { it.expanded }.map { it.marker.verseKey })
        assertTrue("لا يجوز أن تسبق نتيجة موسّعة نتيجة مطابقة", hits.first().expanded.not())
    }

    /** A hit the exact pass already returned must not appear a second time under the line. */
    @Test
    fun `the stemmed pass does not repeat what the exact pass found`() {
        val hits = index.search("المقربون")
        assertEquals(hits.map { it.marker.verseKey }.distinct(), hits.map { it.marker.verseKey })
    }

    /**
     * The scan preserves the order it was built in, so the reader gets hits in mushaf order.
     *
     * 4:172 is deliberately absent: it carries لِّلَّهِ, not ٱللَّهِ, so a search for الله must
     * not reach it. Substring matching is what makes that distinction, and it is easy to lose.
     */
    @Test
    fun `results keep the order the index was built in`() {
        assertEquals(listOf("2:251", "2:255"), keys("الله"))
    }
}

// ── Fixtures ───────────────────────────────────────────────────────────────────

private fun ayah(
    page: Int,
    verseKey: String,
    surahNumber: Int,
    surahNameAr: String,
    ayahNumber: Int,
    uthmani: String,
    imlaei: String,
) = AyahMarker(
    page = page,
    verseKey = verseKey,
    surahNumber = surahNumber,
    surahNameAr = surahNameAr,
    surahNameEn = "",
    ayahNumber = ayahNumber,
    textUthmani = uthmani,
    textImlaei = imlaei,
    juz = 0,
    hizb = 0,
    rub = 0,
    isSajdah = false,
    sajdahNumber = 0,
    centerX = 0f,
    centerY = 0f,
    rects = emptyList(),
)

/** النساء 172 — real mushaf text, generated from the bundled asset. */
private val NISA_172 = ayah(
    page = 105,
    verseKey = "4:172",
    surahNumber = 4,
    surahNameAr = "النساء",
    ayahNumber = 172,
    uthmani = "لَّن يَسْتَنكِفَ ٱلْمَسِيحُ أَن يَكُونَ عَبْدًا لِّلَّهِ وَلَا ٱلْمَلَـٰٓئِكَةُ ٱلْمُقَرَّبُونَ ۚ وَمَن يَسْتَنكِفْ عَنْ عِبَادَتِهِۦ وَيَسْتَكْبِرْ فَسَيَحْشُرُهُمْ إِلَيْهِ جَمِيعًا",
    imlaei = "لَّن يَسْتَنكِفَ الْمَسِيحُ أَن يَكُونَ عَبْدًا لِّلَّهِ وَلَا الْمَلَائِكَةُ الْمُقَرَّبُونَ ۚ وَمَن يَسْتَنكِفْ عَنْ عِبَادَتِهِ وَيَسْتَكْبِرْ فَسَيَحْشُرُهُمْ إِلَيْهِ جَمِيعًا",
)

/** الواقعة 11 — real mushaf text, generated from the bundled asset. */
private val WAQIAH_11 = ayah(
    page = 534,
    verseKey = "56:11",
    surahNumber = 56,
    surahNameAr = "الواقعة",
    ayahNumber = 11,
    uthmani = "أُو۟لَـٰٓئِكَ ٱلْمُقَرَّبُونَ",
    imlaei = "أُولَٰئِكَ الْمُقَرَّبُونَ",
)

/** البقرة 251 — real mushaf text, generated from the bundled asset. */
private val BAQARAH_251 = ayah(
    page = 41,
    verseKey = "2:251",
    surahNumber = 2,
    surahNameAr = "البقرة",
    ayahNumber = 251,
    uthmani = "فَهَزَمُوهُم بِإِذْنِ ٱللَّهِ وَقَتَلَ دَاوُۥدُ جَالُوتَ وَءَاتَىٰهُ ٱللَّهُ ٱلْمُلْكَ وَٱلْحِكْمَةَ وَعَلَّمَهُۥ مِمَّا يَشَآءُ ۗ وَلَوْلَا دَفْعُ ٱللَّهِ ٱلنَّاسَ بَعْضَهُم بِبَعْضٍ لَّفَسَدَتِ ٱلْأَرْضُ وَلَـٰكِنَّ ٱللَّهَ ذُو فَضْلٍ عَلَى ٱلْعَـٰلَمِينَ",
    imlaei = "فَهَزَمُوهُم بِإِذْنِ اللَّهِ وَقَتَلَ دَاوُودُ جَالُوتَ وَآتَاهُ اللَّهُ الْمُلْكَ وَالْحِكْمَةَ وَعَلَّمَهُ مِمَّا يَشَاءُ ۗ وَلَوْلَا دَفْعُ اللَّهِ النَّاسَ بَعْضَهُم بِبَعْضٍ لَّفَسَدَتِ الْأَرْضُ وَلَٰكِنَّ اللَّهَ ذُو فَضْلٍ عَلَى الْعَالَمِينَ",
)

/** البقرة 255 — real mushaf text, generated from the bundled asset. */
private val BAQARAH_255 = ayah(
    page = 42,
    verseKey = "2:255",
    surahNumber = 2,
    surahNameAr = "البقرة",
    ayahNumber = 255,
    uthmani = "ٱللَّهُ لَآ إِلَـٰهَ إِلَّا هُوَ ٱلْحَىُّ ٱلْقَيُّومُ ۚ لَا تَأْخُذُهُۥ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُۥ مَا فِى ٱلسَّمَـٰوَٰتِ وَمَا فِى ٱلْأَرْضِ ۗ مَن ذَا ٱلَّذِى يَشْفَعُ عِندَهُۥٓ إِلَّا بِإِذْنِهِۦ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَىْءٍ مِّنْ عِلْمِهِۦٓ إِلَّا بِمَا شَآءَ ۚ وَسِعَ كُرْسِيُّهُ ٱلسَّمَـٰوَٰتِ وَٱلْأَرْضَ ۖ وَلَا يَـُٔودُهُۥ حِفْظُهُمَا ۚ وَهُوَ ٱلْعَلِىُّ ٱلْعَظِيمُ",
    imlaei = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ",
)
