package com.mushaf.reader.data.content

import org.junit.Assert.assertEquals
import org.junit.Test

class SurahCardTextTest {

    @Test
    fun countsAgreeWithTheirNumber() {
        assertEquals("كلمة واحدة", SurahCardText.words(1))
        assertEquals("كلمتان", SurahCardText.words(2))
        assertEquals("١٠ كلمات", SurahCardText.words(10))
        assertEquals("٢٩ كلمة", SurahCardText.words(29))
        assertEquals("١٠٣ كلمات", SurahCardText.words(103))
        assertEquals("٧ آيات", SurahCardText.ayahs(7))
        assertEquals("٢٨٦ آية", SurahCardText.ayahs(286))
    }

    @Test
    fun theOpeningReadsAsASentence() {
        assertEquals("تبدأ بحروف مقطعة", SurahCardText.opening("حروف مقطعة"))
    }

    @Test
    fun statsLinesNameOnlyWhatTheSourceHas() {
        val fatiha = SurahStatsEntity(
            surahNumber = 1,
            beginType = "ثناء",
            nameTopic = "القرآن",
            wordCount = 29,
            charCount = 139,
            repeatedAyahs = "",
            longestWords = "الْمُسْتَقِيمَ",
            missingLetters = "ثجخزشظف",
        )
        assertEquals(
            listOf(
                "• عدد حروفها: ١٣٩، وعدد كلماتها: ٢٩.",
                "• موضوع اسمها: القرآن.",
                "• أطول كلماتها: {الْمُسْتَقِيمَ}.",
                "• حروف هجاء لم ترد فيها: ث ج خ ز ش ظ ف.",
            ),
            SurahCardText.statsLines(fatiha),
        )
        val baqara = fatiha.copy(nameTopic = "", repeatedAyahs = "47,122,134,141", longestWords = "", missingLetters = "")
        assertEquals(
            listOf(
                "• عدد حروفها: ١٣٩، وعدد كلماتها: ٢٩.",
                "• آيات يتكرر نصها فيها: ٤٧، ١٢٢، ١٣٤، ١٤١.",
            ),
            SurahCardText.statsLines(baqara),
        )
    }
}
