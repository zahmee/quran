package com.mushaf.reader.data.recitation

/** "٣٤٠ ميغابايت", "١٫٢ غيغابايت", "٨٠٠ كيلوبايت" — Arabic digits, one decimal above a gigabyte. */
fun formatByteSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val (value, unit) = when {
        bytes >= gb -> bytes / gb to "غيغابايت"
        bytes >= mb -> bytes / mb to "ميغابايت"
        else -> (bytes / kb).coerceAtLeast(if (bytes > 0) 1.0 else 0.0) to "كيلوبايت"
    }
    val text = if (bytes >= gb) {
        val tenths = Math.round(value * 10)
        if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    } else {
        Math.round(value).toString()
    }
    val arabic = text.map { if (it == '.') '٫' else if (it.isDigit()) '٠' + (it - '0') else it }
        .joinToString("")
    return "$arabic $unit"
}
