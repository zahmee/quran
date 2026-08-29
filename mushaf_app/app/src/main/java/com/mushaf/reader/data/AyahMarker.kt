package com.mushaf.reader.data

/** A highlight rectangle in image-pixel space. */
data class AyahRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    fun contains(px: Float, py: Float): Boolean =
        px >= x && px <= x + w && py >= y && py <= y + h
}

/** One ayah on a page: identity, metadata, end-marker center, and its highlight region. */
data class AyahMarker(
    val page: Int,
    val verseKey: String,
    val surahNumber: Int,
    val surahNameAr: String,
    val surahNameEn: String,
    val ayahNumber: Int,
    val textUthmani: String,
    /** The same ayah in standard (imlaa'i) orthography — the spelling a reader types.
     *  Search only: the page and the result rows always show [textUthmani]. */
    val textImlaei: String,
    val juz: Int,
    val hizb: Int,
    val rub: Int,
    val isSajdah: Boolean,
    val sajdahNumber: Int,
    val centerX: Float,
    val centerY: Float,
    val rects: List<AyahRect>,
)
