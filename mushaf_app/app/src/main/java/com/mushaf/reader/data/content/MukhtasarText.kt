package com.mushaf.reader.data.content

/** The Mukhtasar marks the words it explains with underscores: `_word_`. */
object MukhtasarText {
    /** Group 1 is the marked word or phrase. */
    val Gloss = Regex("_([^_\\n]+)_")

    /** The text without its marks, for copying and sharing. */
    fun plain(raw: String): String = raw.replace(Gloss, "$1")
}
