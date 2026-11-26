package com.mushaf.reader.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

fun Int.toArabicDigits(): String =
    toString().map { c -> if (c in '0'..'9') arabicDigits[c - '0'] else c }.joinToString("")

fun String.toArabicDigits(): String =
    map { c -> if (c in '0'..'9') arabicDigits[c - '0'] else c }.joinToString("")
