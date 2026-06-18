package com.mushaf.reader.data

import android.content.Context

/**
 * Provides access to the mushaf page images bundled in assets.
 * Phase A: a small subset of pages is shipped under assets/pages/{light,dark}.
 */
class PageRepository(private val context: Context) {

    /** Number of pages available, derived from the light asset folder. */
    fun pageCount(): Int = context.assets.list("pages/light")?.size ?: 0

    /** Coil-compatible asset URI for a 1-based page number in the requested theme. */
    fun assetUri(pageNumber: Int, dark: Boolean): String {
        val folder = if (dark) "dark" else "light"
        return "file:///android_asset/pages/$folder/$pageNumber.webp"
    }
}
