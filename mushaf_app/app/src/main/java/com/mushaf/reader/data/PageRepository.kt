package com.mushaf.reader.data

import android.content.Context

/**
 * Provides access to the mushaf page images bundled in assets.
 *
 * One set of pages ships, scanned on white paper. Every reading theme — including the dark ones —
 * is produced from it at draw time by the palette's page recolor (see ui/theme/Palette.kt), so no
 * theme needs its own copy of the 604 page images.
 */
class PageRepository(private val context: Context) {

    /** Number of pages available, derived from the asset folder. */
    fun pageCount(): Int = context.assets.list("pages")?.size ?: 0

    /** Coil-compatible asset URI for a 1-based page number. */
    fun assetUri(pageNumber: Int): String = "file:///android_asset/pages/$pageNumber.webp"
}
