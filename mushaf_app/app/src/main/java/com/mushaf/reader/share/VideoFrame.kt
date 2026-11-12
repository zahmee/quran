package com.mushaf.reader.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

/**
 * The picture a shared video holds: the share card, centred on a portrait frame of the card's own
 * paper — the 9:16 that status updates and short-video apps show full screen.
 *
 * The card is never cropped, only scaled to fit. A tall run (three pages) comes out narrow; that
 * is the cost of keeping all of it on one screen.
 */
internal object VideoFrame {
    const val WIDTH = 1080
    const val HEIGHT = 1920

    /** Breathing room around the card, so the frame lines never touch the screen's edge. */
    private const val MARGIN = 48f

    fun compose(card: Bitmap, paper: Int): Bitmap {
        val out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(paper)
        val scale = min((WIDTH - 2 * MARGIN) / card.width, (HEIGHT - 2 * MARGIN) / card.height)
        val w = card.width * scale
        val h = card.height * scale
        val left = (WIDTH - w) / 2
        val top = (HEIGHT - h) / 2
        canvas.drawBitmap(card, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
