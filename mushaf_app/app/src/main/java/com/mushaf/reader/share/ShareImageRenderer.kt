package com.mushaf.reader.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.mushaf.reader.R
import com.mushaf.reader.data.AyahMarker
import com.mushaf.reader.reader.toArabicDigits
import com.mushaf.reader.ui.theme.AccentDark
import com.mushaf.reader.ui.theme.AccentLight
import com.mushaf.reader.ui.theme.MushafPalette
import com.mushaf.reader.ui.theme.pageRecolor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** What to cut out: a whole page, or a run of ayahs — on one page, or running over several. */
sealed interface ShareScope {

    data class Page(val page: Int) : ShareScope

    /** [ayahs] in reading order, all of one surah. */
    data class Ayahs(val ayahs: List<AyahMarker>) : ShareScope {
        init {
            require(ayahs.isNotEmpty()) { "no ayahs" }
        }

        /** The pages the run covers, in order. */
        val pages: List<Int> get() = ayahs.map { it.page }.distinct()
    }
}

/**
 * Draws a share image straight from the printed page asset — never retyped text, so the ayah keeps
 * the mushaf's own script and every mark of it.
 *
 * An ayah is cut out of the page by [InkOwnership], recolored the way the reader recolors the page
 * for the chosen theme, and — when [framed] — set in a double hairline frame with the surah name
 * above and the reference and the app's name below.
 *
 * Layout runs in "card units": the card is 1080 units wide, and a unit is sized so the page's text
 * lands on the image pixel for pixel. The page assets are the sharpest source there is; scaling
 * them down to a round width would only blur them.
 */
class ShareImageRenderer(context: Context) {
    private val app = context.applicationContext

    // Decoding a lossless page and labelling its ink are the slow parts. Changing the theme or the
    // frame reuses the last cut instead of redoing both.
    private var cachedKey: Any? = null
    private var cachedCut: Cut? = null

    /**
     * A cut: the text, and the width of the page it came from, which sizes the card. [shift] says
     * where each page sits in it: a point (x, y) of page p is at (x + dx, y + dy) of the cut.
     */
    private class Cut(val bitmap: Bitmap, val pageWidth: Int, val shift: Map<Int, Pair<Int, Int>>)

    /** A page's share of a cut, and where on the page its top-left corner was. */
    private class Piece(val bitmap: Bitmap, val left: Int, val top: Int)

    /**
     * [markersFor] gives the ayahs of a page: every one of them decides who owns the ink.
     * [highlight], when given, is lit the way the reader lights a selected ayah — the video uses it
     * to show which ayah is being recited.
     */
    suspend fun render(
        scope: ShareScope,
        markersFor: (Int) -> List<AyahMarker>,
        palette: MushafPalette,
        framed: Boolean,
        highlight: AyahMarker? = null,
    ): Bitmap = withContext(Dispatchers.Default) {
        val cut = cut(scope, markersFor)
        val text = recolor(cut.bitmap, palette)
        if (highlight != null) light(text, highlight, cut, palette)
        val card = if (framed) frame(text, cut.pageWidth, scope, markersFor, palette)
        else plain(text, cut.pageWidth, palette)
        text.recycle()
        card
    }

    /** The page, or the scope's ayahs alone on white, still in the asset's own colors. */
    private fun cut(scope: ShareScope, markersFor: (Int) -> List<AyahMarker>): Cut = synchronized(this) {
        val key: Any = when (scope) {
            is ShareScope.Page -> scope.page
            is ShareScope.Ayahs -> scope.ayahs.map { it.verseKey }
        }
        cachedCut?.takeIf { cachedKey == key }?.let { return it }

        val result = when (scope) {
            is ShareScope.Page -> decode(scope.page).let { Cut(it, it.width, mapOf(scope.page to (0 to 0))) }
            is ShareScope.Ayahs -> {
                val pages = scope.pages
                var width = 0
                // One page decoded at a time: a run over three pages never holds three of them.
                val parts = pages.map { p ->
                    val page = decode(p)
                    width = page.width
                    excerpt(
                        page = page,
                        ayahs = scope.ayahs.filter { it.page == p },
                        pageMarkers = markersFor(p),
                        // On a later page the run's first line goes on from the line before; it
                        // keeps its place on the line instead of being centred.
                        trimOneLine = pages.size == 1,
                    ).also { page.recycle() }
                }
                var y = 0
                val shift = pages.zip(parts).associate { (p, piece) ->
                    (p to (-piece.left to y - piece.top)).also { y += piece.bitmap.height }
                }
                Cut(stack(parts.map { it.bitmap }), width, shift)
            }
        }
        // Not recycled: a render still recoloring the previous cut may hold it. The GC frees it.
        cachedCut = result
        cachedKey = key
        result
    }

    private fun decode(page: Int): Bitmap =
        app.assets.open("pages/$page.webp").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: error("page $page did not decode")

    /**
     * Cuts from consecutive pages, one under the next, so the text reads on. No rule between them:
     * a page break is a fact of the printing, not of the text. The margins each cut keeps above and
     * below leave a gap a little wider than between two lines, which is all it shows.
     */
    private fun stack(parts: List<Bitmap>): Bitmap {
        if (parts.size == 1) return parts.single()
        val out = Bitmap.createBitmap(parts.maxOf { it.width }, parts.sumOf { it.height }, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(WHITE)
        var y = 0f
        for (part in parts) {
            canvas.drawBitmap(part, 0f, y, null)
            y += part.height
            part.recycle()
        }
        return out
    }

    private fun excerpt(
        page: Bitmap,
        ayahs: List<AyahMarker>,
        pageMarkers: List<AyahMarker>,
        trimOneLine: Boolean,
    ): Piece {
        val keys = ayahs.map { it.verseKey }.toSet()
        val owners = pageMarkers.withIndex().associate { (i, m) -> m.verseKey to i }
        val rects = pageMarkers.flatMapIndexed { i, m ->
            m.rects.map { InkRect(i, it.x.roundToInt(), it.y.roundToInt(), it.w.roundToInt(), it.h.roundToInt()) }
        }
        val chosen = rects.filter { pageMarkers[it.owner].verseKey in keys }
        require(chosen.isNotEmpty()) { "no rectangles for $keys on this page" }

        val top = (chosen.minOf { it.y } - CUT_MARGIN).coerceAtLeast(0)
        val bottom = (chosen.maxOf { it.y + it.h } + CUT_MARGIN).coerceAtMost(page.height)
        // The ink is read over a window a line taller each way than the cut, so a blob crossing
        // the cut's edge — a surah header's frame, the next line's tall letter — is judged whole,
        // not by the sliver of it that falls inside.
        val winTop = (top - LOOK_AROUND).coerceAtLeast(0)
        val winBottom = (bottom + LOOK_AROUND).coerceAtMost(page.height)
        val w = page.width
        val winH = winBottom - winTop
        val pixels = IntArray(w * winH)
        page.getPixels(pixels, 0, w, 0, winTop, w, winH)

        val ink = BooleanArray(pixels.size) { i ->
            val p = pixels[i]
            minOf(p shr 16 and 0xFF, p shr 8 and 0xFF, p and 0xFF) < INK_BELOW
        }
        val keep = InkOwnership.keepMask(ink, w, winH, winTop, rects, keys.mapNotNull { owners[it] }.toSet())
        for (i in pixels.indices) if (!keep[i]) pixels[i] = WHITE

        // On one line the ayah keeps only its own stretch of it, to be centred in the card — a
        // short ayah left where it sat on the line would hug one edge of a wide empty image.
        // Across lines the cut keeps the full width: the lines are justified edge to edge.
        val oneLine = trimOneLine && chosen.map { it.y }.distinct().size == 1
        val left = if (oneLine) (chosen.minOf { it.x } - SIDE_PAD).coerceAtLeast(0) else 0
        val right = if (oneLine) (chosen.maxOf { it.x + it.w } + SIDE_PAD).coerceAtMost(w) else w
        val bitmap = Bitmap.createBitmap(
            pixels, (top - winTop) * w + left, w, right - left, bottom - top, Bitmap.Config.ARGB_8888,
        )
        return Piece(bitmap, left, top)
    }

    /** Lights [ayah]'s rectangles on [text] (the recolored cut), as the reader lights a selection. */
    private fun light(text: Bitmap, ayah: AyahMarker, cut: Cut, palette: MushafPalette) {
        val (dx, dy) = cut.shift[ayah.page] ?: return
        val accent = if (palette.dark) AccentDark else AccentLight
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent.copy(alpha = if (palette.dark) 0.28f else 0.18f).toArgb()
        }
        val canvas = Canvas(text)
        ayah.rects.forEach { r ->
            canvas.drawRoundRect(
                RectF(r.x + dx, r.y + dy, r.x + r.w + dx, r.y + r.h + dy),
                HIGHLIGHT_CORNER, HIGHLIGHT_CORNER, paint,
            )
        }
    }

    /** The same recolor the reader draws the page with, see [com.mushaf.reader.ui.theme.PageRecolor]. */
    private fun recolor(src: Bitmap, palette: MushafPalette): Bitmap {
        val recolor = palette.pageRecolor()
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = recolor.filter?.asAndroidColorFilter()
        })
        recolor.multiply?.let { canvas.drawColor(it.toArgb(), PorterDuff.Mode.MULTIPLY) }
        return out
    }

    private fun plain(text: Bitmap, pageWidth: Int, palette: MushafPalette): Bitmap {
        val pad = (pageWidth * PLAIN_PAD).roundToInt()
        val out = Bitmap.createBitmap(text.width + 2 * pad, text.height + 2 * pad, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(palette.paper.toArgb())
            drawBitmap(text, pad.toFloat(), pad.toFloat(), null)
        }
        return out
    }

    private fun frame(
        text: Bitmap,
        pageWidth: Int,
        scope: ShareScope,
        markersFor: (Int) -> List<AyahMarker>,
        palette: MushafPalette,
    ): Bitmap {
        // Sized by the page, not the cut, so a one-line ayah trimmed to its own width sits centred
        // in a card of the usual size, at the page's own sharpness.
        val u = pageWidth / TEXT_WIDTH_UNITS
        val accent = if (palette.dark) AccentDark else AccentLight
        val ink = palette.ink
        val muted = lerp(palette.paper, ink, 0.62f)
        val naskh = ResourcesCompat.getFont(app, R.font.noto_naskh_arabic_bold) ?: Typeface.SERIF
        val plexBold = ResourcesCompat.getFont(app, R.font.ibm_plex_sans_arabic_semibold) ?: Typeface.DEFAULT_BOLD
        val plex = ResourcesCompat.getFont(app, R.font.ibm_plex_sans_arabic_medium) ?: Typeface.DEFAULT

        val first = (scope as? ShareScope.Ayahs)?.ayahs?.firstOrNull()
        // A page carries its own printed surah headers; only a cut-out ayah needs a title.
        val title = first?.let { "سورة ${it.surahNameAr}" }
        val (reference, detail) = labels(scope, markersFor)

        val titlePaint = textPaint(naskh, 44 * u, accent)
        val refPaint = textPaint(plexBold, 30 * u, ink)
        val detailPaint = textPaint(plex, 25 * u, muted)
        val brandPaint = textPaint(plexBold, 28 * u, muted)

        val inset = 26 * u
        val padX = 52 * u
        val padTop = (if (title != null) 44 else 34) * u
        val titleBlock = if (title != null) 70 * u + 26 * u else 0f
        val footBlock = 26 * u + 22 * u + 50 * u
        val padBottom = 34 * u
        val width = (CARD_UNITS * u).roundToInt()
        val height = (inset * 2 + padTop + titleBlock + text.height + footBlock + padBottom).roundToInt()

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(palette.paper.toArgb())

        // Double hairline frame.
        val outer = RectF(inset, inset, width - inset, height - inset)
        c.drawRoundRect(outer, 6 * u, 6 * u, strokePaint(2 * u, accent.copy(alpha = 0.55f)))
        val inner = RectF(outer.left + 7 * u, outer.top + 7 * u, outer.right - 7 * u, outer.bottom - 7 * u)
        c.drawRoundRect(inner, 3 * u, 3 * u, strokePaint(1 * u, ink.copy(alpha = 0.18f)))

        val left = inset + padX
        val right = width - inset - padX
        var y = inset + padTop

        if (title != null) {
            val baseline = y + 50 * u
            val cx = width / 2f
            titlePaint.textAlign = Paint.Align.CENTER
            c.drawText(title, cx, baseline, titlePaint)
            val half = titlePaint.measureText(title) / 2 + 22 * u
            val rule = strokePaint(2 * u, accent.copy(alpha = 0.4f))
            val ruleY = baseline - 14 * u
            c.drawLine(cx + half, ruleY, cx + half + 120 * u, ruleY, rule)
            c.drawLine(cx - half - 120 * u, ruleY, cx - half, ruleY, rule)
            y += titleBlock
        }

        c.drawBitmap(text, (width - text.width) / 2f, y, Paint(Paint.FILTER_BITMAP_FLAG))
        y += text.height + 26 * u

        c.drawLine(left, y, right, y, strokePaint(1.5f * u, ink.copy(alpha = 0.18f)))
        y += 22 * u
        val baseline = y + 36 * u

        // Right to left: the reference first, its detail after it, the app's name on the far side.
        refPaint.textAlign = Paint.Align.RIGHT
        c.drawText(reference, right, baseline, refPaint)
        if (detail != null) {
            detailPaint.textAlign = Paint.Align.RIGHT
            c.drawText(detail, right - refPaint.measureText(reference) - 14 * u, baseline, detailPaint)
        }
        val logo = 46 * u
        val logoTop = y + 25 * u - logo / 2
        ContextCompat.getDrawable(app, R.mipmap.ic_launcher_round)?.let { d ->
            d.setBounds(left.roundToInt(), logoTop.roundToInt(), (left + logo).roundToInt(), (logoTop + logo).roundToInt())
            d.draw(c)
        }
        brandPaint.textAlign = Paint.Align.LEFT
        c.drawText(app.getString(R.string.app_name), left + logo + 12 * u, baseline, brandPaint)
        return out
    }

    private fun textPaint(face: Typeface, size: Float, color: Color) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color.toArgb()
        }

    private fun strokePaint(width: Float, color: Color) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            this.color = color.toArgb()
        }

    companion object {
        /** Card width in layout units. */
        private const val CARD_UNITS = 1080f

        /** Width the text takes inside the card: the card less the frame inset and padding. */
        private const val TEXT_WIDTH_UNITS = CARD_UNITS - 2 * (26f + 52f)

        /** Rows above the first line and below the last kept for the tashkeel that rides outside
         *  the measured line bands. */
        private const val CUT_MARGIN = 26

        /** Rows read beyond the cut on each side: one printed line. */
        private const val LOOK_AROUND = 118

        /** Paper kept either side of a one-line cut, for marks that overhang the ayah's band. */
        private const val SIDE_PAD = 24

        /** Corner of a lit ayah's band, in page pixels: the reader's 8dp at its usual scale. */
        private const val HIGHLIGHT_CORNER = 12f

        /** Anything darker than this in any channel counts as ink. High on purpose: it takes in the
         *  anti-aliased rim of every letter, which would otherwise be left behind as a halo. */
        private const val INK_BELOW = 246

        /** Unframed images: paper around the text, as a fraction of the text's width. */
        private const val PLAIN_PAD = 0.045f

        private const val WHITE = -0x1

        /**
         * The footer's two pieces: a bold reference and the quieter detail beside it.
         * Ayah: "البقرة ٢٥٥" · "الجزء ٣ • صفحة ٤٢". A run over pages: "البقرة ٢٥٥–٢٥٧" ·
         * "الجزء ٣ • صفحة ٤٢–٤٣". Page: "صفحة ٤٢" · "البقرة • الجزء ٣".
         */
        internal fun labels(scope: ShareScope, markersFor: (Int) -> List<AyahMarker>): Pair<String, String?> =
            when (scope) {
                is ShareScope.Ayahs -> {
                    val a = scope.ayahs.first()
                    val z = scope.ayahs.last()
                    "${a.surahNameAr} ${from(a.ayahNumber, z.ayahNumber)}" to
                        "الجزء ${from(a.juz, z.juz)} • صفحة ${from(a.page, z.page)}"
                }
                is ShareScope.Page -> {
                    val markers = markersFor(scope.page)
                    val surahs = markers.map { it.surahNameAr }.distinct().joinToString("، ")
                    val juz = markers.firstOrNull()?.juz?.toArabicDigits()
                    val detail = listOfNotNull(surahs.ifBlank { null }, juz?.let { "الجزء $it" })
                        .joinToString(" • ").ifBlank { null }
                    "صفحة ${scope.page.toArabicDigits()}" to detail
                }
            }

        /** "٤٢", or "٤٢–٤٣" when the two differ. */
        private fun from(a: Int, z: Int): String =
            if (a == z) a.toArabicDigits() else "${a.toArabicDigits()}–${z.toArabicDigits()}"
    }
}
