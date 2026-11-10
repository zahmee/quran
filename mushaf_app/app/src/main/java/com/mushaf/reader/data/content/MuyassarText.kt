package com.mushaf.reader.data.content

/**
 * The Muyassar text puts the Quran words it quotes between two Arabic ligature code points — U+FD61
 * before, U+FD60 after — that only the source's own Quran font draws as ornate brackets. Every other
 * font draws them as the letters «سمج» and «سمح», glued to the quoted word. They become the real
 * ornate brackets the rest of the app's texts use: ﴿ (U+FD3F) opening, ﴾ (U+FD3E) closing.
 */
object MuyassarText {
    private const val SOURCE_OPEN = 'ﵡ'
    private const val SOURCE_CLOSE = 'ﵠ'
    const val OPEN = '﴿'
    const val CLOSE = '﴾'

    /** [raw] with the source's bracket stand-ins replaced; everything else is left as it is. */
    fun normalize(raw: String): String =
        if (SOURCE_OPEN !in raw && SOURCE_CLOSE !in raw) raw
        else raw.replace(SOURCE_OPEN, OPEN).replace(SOURCE_CLOSE, CLOSE)
}
