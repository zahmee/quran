package com.mushaf.reader.data.content

/** What a row of a passage-based tafsir is, as far as laying it out goes. */
enum class TafsirRowKind {
    SurahTitle,
    RevelationPlace,
    Basmala,

    /** The block of Quran text the passage explains. */
    QuranText,

    /** The explanation of one ayah or a run of ayahs. */
    AyahTafsir,

    /** The "* * *" the print puts between parts. */
    Divider,

    /** Anything else said in passing: a lead-in, a closing note, a chapter of lessons. */
    Prose,
    ;

    companion object {
        /** Maps the source's own Arabic row type; an unknown type is shown as plain prose. */
        fun fromSource(kind: String): TafsirRowKind = when (kind) {
            "اسم السورة" -> SurahTitle
            "مكان نزول السورة" -> RevelationPlace
            "البسملة" -> Basmala
            "المقطع" -> QuranText
            "تفسير آية", "تكملة تفسير الآية" -> AyahTafsir
            "نجوم" -> Divider
            else -> Prose
        }
    }
}

data class TafsirPassageRow(
    val kind: TafsirRowKind,
    /** 0 when the row only has a place in the reading order and no ayah range of its own. */
    val fromAyah: Int,
    val toAyah: Int,
    val text: String,
) {
    fun covers(ayah: Int): Boolean = fromAyah in 1..ayah && ayah <= toAyah
}

/** One passage of a passage-based tafsir, seen from the ayah the reader asked about. */
data class TafsirPassage(
    val rows: List<TafsirPassageRow>,
    val ayah: Int,
) {
    /** Indices of the rows that explain [ayah] itself; empty when the author passed over it. */
    val focusRows: List<Int> = rows.indices.filter {
        rows[it].kind == TafsirRowKind.AyahTafsir && rows[it].covers(ayah)
    }

    val hasOwnTafsir: Boolean get() = focusRows.isNotEmpty()

    /** The row to bring into view first: the ayah's own explanation, else its Quran text. */
    val focusIndex: Int = focusRows.firstOrNull()
        ?: rows.indexOfFirst { it.kind == TafsirRowKind.QuranText && it.covers(ayah) }.coerceAtLeast(0)

    /**
     * The text to copy or share: the ayah's own explanation, or — when it has none — the
     * explanation of the whole passage, so the reader never shares an empty message.
     */
    val shareText: String
        get() {
            val chosen = if (hasOwnTafsir) {
                focusRows.map { rows[it] }
            } else {
                rows.filter { it.kind == TafsirRowKind.AyahTafsir || it.kind == TafsirRowKind.Prose }
            }
            return chosen.joinToString("\n\n") { it.text.trim() }
        }

    val isEmpty: Boolean get() = rows.isEmpty()
}
