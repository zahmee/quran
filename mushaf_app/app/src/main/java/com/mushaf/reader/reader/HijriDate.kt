package com.mushaf.reader.reader

import android.icu.util.IslamicCalendar
import java.util.Calendar
import java.util.Date

/** Formats an epoch-millis instant as Arabic Hijri (Umm al-Qura) and Gregorian date strings.
 *  Uses android.icu (API 24+, our minSdk) so no external library is needed. */
object HijriDate {

    private val hijriMonths = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )

    private val gregorianMonths = arrayOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    /** e.g. "١٩ محرم ١٤٤٨هـ" — Umm al-Qura reckoning. */
    fun hijri(millis: Long): String {
        val cal = IslamicCalendar()
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.time = Date(millis)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH).coerceIn(0, 11)
        val year = cal.get(Calendar.YEAR)
        return "${day.toArabicDigits()} ${hijriMonths[month]} (${(month + 1).toArabicDigits()}) ${year.toArabicDigits()}هـ"
    }

    /** e.g. "محرم ١٤٤٨هـ" — month and year only (Umm al-Qura). */
    fun hijriMonthYear(millis: Long): String {
        val cal = IslamicCalendar()
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.time = Date(millis)
        val month = cal.get(Calendar.MONTH).coerceIn(0, 11)
        val year = cal.get(Calendar.YEAR)
        return "${hijriMonths[month]} (${(month + 1).toArabicDigits()}) ${year.toArabicDigits()}هـ"
    }

    /** e.g. "٤ يوليو (٧) ٢٠٢٦م" — month name with its number. */
    fun gregorian(millis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH).coerceIn(0, 11)
        val year = cal.get(Calendar.YEAR)
        return "${day.toArabicDigits()} ${gregorianMonths[month]} (${(month + 1).toArabicDigits()}) ${year.toArabicDigits()}م"
    }

    /** e.g. "يوليو (٧) ٢٠٢٦" — Gregorian month name with its number, and year. */
    fun gregorianMonthYear(millis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        val month = cal.get(Calendar.MONTH).coerceIn(0, 11)
        val year = cal.get(Calendar.YEAR)
        return "${gregorianMonths[month]} (${(month + 1).toArabicDigits()}) ${year.toArabicDigits()}"
    }
}
