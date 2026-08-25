package com.mushaf.reader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * A reading theme: the two colors the printed mushaf page is recolored to, plus whether the app
 * chrome around it follows the same hue.
 *
 * Only one set of page images ships (assets/pages) — every theme is produced from it at draw time,
 * see [pageRecolor], so adding a theme costs nothing in APK size.
 */
data class MushafPalette(
    val id: String,
    /** Arabic name shown in the settings picker and the reader's More menu. */
    val label: String,
    /** What the source page's white paper becomes. */
    val paper: Color,
    /** What the source page's black ink becomes. */
    val ink: Color,
    val dark: Boolean,
    /** False for the two original themes: their app chrome is already hand-tuned in Theme.kt. */
    val tintsChrome: Boolean = true,
)

/** The offered themes in picker order: the three light ones, then the three dark ones. */
val MushafPalettes = listOf(
    MushafPalette("light", "فاتح", Color(0xFFFFFFFF), Color(0xFF20251F), dark = false, tintsChrome = false),
    MushafPalette("sepia", "ورقي", Color(0xFFF2E4C9), Color(0xFF3A2C18), dark = false),
    MushafPalette("mint", "أخضر هادئ", Color(0xFFE4EEE2), Color(0xFF1E2E22), dark = false),
    MushafPalette("dark", "ليلي", Color(0xFF000000), Color(0xFFFFFFFF), dark = true, tintsChrome = false),
    MushafPalette("brown", "بنّي داكن", Color(0xFF241A10), Color(0xFFE9DCC4), dark = true),
    MushafPalette("navy", "أزرق داكن", Color(0xFF101B2B), Color(0xFFD8E4F2), dark = true),
)

/** Ids the stored setting falls back to; also used to migrate the old dark_theme boolean. */
const val DefaultPaletteId = "light"
const val DarkPaletteId = "dark"

fun paletteFor(id: String): MushafPalette =
    MushafPalettes.firstOrNull { it.id == id } ?: MushafPalettes.first()

/**
 * How a palette recolors the printed page.
 *
 * Light themes [multiply] a paper color over the untouched scan: white paper takes the color while
 * the surah-header ornament keeps its printed green and gold. Multiplying can only darken, so dark
 * themes remap luminance through [filter] instead and come out monochrome — exactly as the old
 * pre-rendered dark pages did.
 */
data class PageRecolor(val filter: ColorFilter?, val multiply: Color?)

fun MushafPalette.pageRecolor(): PageRecolor =
    if (dark) PageRecolor(filter = pageTint(paper, ink), multiply = null)
    // Multiplying by white is a no-op; skipping it keeps the default theme on the plain draw path.
    else PageRecolor(filter = null, multiply = paper.takeIf { it != Color.White })

/**
 * Maps the scan's luminance onto an [ink] → [paper] ramp.
 *
 * The printed ink is not pure black — it sits around [lo] of full luminance — so the ramp starts
 * there. Without that, real ink would land short of [ink] and every theme would look washed out.
 */
fun pageTint(paper: Color, ink: Color, lo: Float = 0.12f): ColorFilter {
    val k = 1f / (1f - lo)
    // out = ink + ((L - lo) * k) * (paper - ink), with L the source luminance — linear, so one row
    // of the 4x5 matrix per channel. Offsets are on the 0..255 scale ColorMatrix expects.
    fun row(p: Float, i: Float): FloatArray {
        val d = k * (p - i)
        return floatArrayOf(d * 0.299f, d * 0.587f, d * 0.114f, 0f, 255f * (i - lo * d))
    }
    val r = row(paper.red, ink.red)
    val g = row(paper.green, ink.green)
    val b = row(paper.blue, ink.blue)
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                r[0], r[1], r[2], 0f, r[4],
                g[0], g[1], g[2], 0f, g[4],
                b[0], b[1], b[2], 0f, b[4],
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

/**
 * Re-tints one app-chrome neutral to this palette's hue while keeping its lightness, so Material's
 * surface-elevation ordering survives the swap. The ramp runs between the palette's own darker and
 * lighter ends, which is why it works unchanged for both light and dark themes.
 */
internal fun MushafPalette.tintNeutral(c: Color): Color {
    val lo = if (dark) paper else ink
    val hi = if (dark) ink else paper
    val l = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    return Color(
        red = lo.red + l * (hi.red - lo.red),
        green = lo.green + l * (hi.green - lo.green),
        blue = lo.blue + l * (hi.blue - lo.blue),
        alpha = c.alpha,
    )
}
