package com.mushaf.reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mushaf.reader.R

/** Clear contemporary Arabic for navigation, controls, headings, and compact metadata. */
val InterfaceFontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, weight = FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, weight = FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, weight = FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, weight = FontWeight.Bold),
    Font(R.font.ibm_plex_sans_arabic_bold, weight = FontWeight.ExtraBold),
)

/** Traditional Naskh reserved for long explanatory content, never for app chrome. */
val ReadingFontFamily = FontFamily(
    Font(R.font.noto_naskh_arabic_regular, weight = FontWeight.Normal),
    Font(R.font.noto_naskh_arabic_bold, weight = FontWeight.Bold),
)

private val DefaultTypography = Typography()

/** Preserve Material's proven scale while giving every UI role one Arabic type family. */
val Typography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontFamily = InterfaceFontFamily),
    displayMedium = DefaultTypography.displayMedium.copy(fontFamily = InterfaceFontFamily),
    displaySmall = DefaultTypography.displaySmall.copy(fontFamily = InterfaceFontFamily),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = InterfaceFontFamily),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = InterfaceFontFamily),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = InterfaceFontFamily),
    titleLarge = DefaultTypography.titleLarge.copy(fontFamily = InterfaceFontFamily),
    titleMedium = DefaultTypography.titleMedium.copy(fontFamily = InterfaceFontFamily),
    titleSmall = DefaultTypography.titleSmall.copy(fontFamily = InterfaceFontFamily),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = InterfaceFontFamily),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = InterfaceFontFamily),
    bodySmall = DefaultTypography.bodySmall.copy(fontFamily = InterfaceFontFamily),
    labelLarge = DefaultTypography.labelLarge.copy(fontFamily = InterfaceFontFamily),
    labelMedium = DefaultTypography.labelMedium.copy(fontFamily = InterfaceFontFamily),
    labelSmall = DefaultTypography.labelSmall.copy(fontFamily = InterfaceFontFamily),
)

object ReadingType {
    val bodyLarge = TextStyle(
        fontFamily = ReadingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 32.sp,
    )

    val bodyMedium = TextStyle(
        fontFamily = ReadingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 29.sp,
    )
}
