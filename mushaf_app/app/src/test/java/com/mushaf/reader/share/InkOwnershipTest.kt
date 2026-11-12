package com.mushaf.reader.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InkOwnershipTest {

    /** A strip drawn with '#' for ink, one string per row. */
    private fun strip(vararg rows: String): Triple<BooleanArray, Int, Int> {
        val w = rows.first().length
        val ink = BooleanArray(w * rows.size) { i -> rows[i / w][i % w] == '#' }
        return Triple(ink, w, rows.size)
    }

    private fun kept(mask: BooleanArray, w: Int, x: Int, y: Int) = mask[y * w + x]

    @Test
    fun `labels diagonal neighbours as one blob and separate blobs apart`() {
        val (ink, w, h) = strip(
            "#...#",
            ".#..#",
            "..#..",
        )
        val (labels, count) = InkOwnership.label(ink, w, h)
        assertEquals(2, count)
        assertEquals(labels[0], labels[1 * w + 1])
        assertEquals(labels[0], labels[2 * w + 2])
        assertTrue(labels[4] != labels[0])
    }

    @Test
    fun `merges a U shape whose arms meet only at the bottom`() {
        val (ink, w, h) = strip(
            "#...#",
            "#...#",
            "#####",
        )
        assertEquals(1, InkOwnership.label(ink, w, h).second)
    }

    @Test
    fun `keeps only the selected ayah on a shared line`() {
        // One line: ayah 1 on the right half, ayah 2 on the left half (RTL).
        val (ink, w, h) = strip(
            "##..##",
            "##..##",
        )
        val rects = listOf(InkRect(owner = 2, x = 0, y = 0, w = 3, h = 2), InkRect(owner = 1, x = 3, y = 0, w = 3, h = 2))
        val mask = InkOwnership.keepMask(ink, w, h, top = 0, rects = rects, keep = setOf(1))
        assertFalse(kept(mask, w, 0, 0))
        assertTrue(kept(mask, w, 5, 0))
        assertTrue(kept(mask, w, 4, 1))
    }

    @Test
    fun `a tall letter rising into the line above stays with its own ayah`() {
        // Rows 0-2: line A (ayah 1). Rows 3-5: line B (ayah 2). The blob at column 1 starts in row 2,
        // inside line A's band, but most of it lies in line B.
        val (ink, w, h) = strip(
            "...##",
            "...##",
            ".#...",
            ".#...",
            ".#...",
            ".#...",
        )
        val rects = listOf(InkRect(1, 0, 0, 5, 3), InkRect(2, 0, 3, 5, 3))
        val mask = InkOwnership.keepMask(ink, w, h, top = 0, rects = rects, keep = setOf(1))
        assertTrue(kept(mask, w, 4, 0))
        assertFalse("the rising stroke belongs to ayah 2", kept(mask, w, 1, 2))
    }

    @Test
    fun `a mark in the gap between lines goes to the nearer line`() {
        // Line A rows 0-1, gap rows 2-4, line B rows 5-6. A mark at row 4 is nearer line B.
        val (ink, w, h) = strip(
            "#....",
            "#....",
            ".....",
            ".....",
            "...#.",
            "...##",
            "...##",
        )
        // The mark at (3,4) touches the blob below it, so take a free-floating one instead:
        val floating = ink.copyOf().also { it[4 * w + 3] = false; it[4 * w + 1] = true }
        val rects = listOf(InkRect(1, 0, 0, 5, 2), InkRect(2, 0, 5, 5, 2))
        val mask = InkOwnership.keepMask(floating, w, h, top = 0, rects = rects, keep = setOf(2))
        assertTrue(kept(mask, w, 1, 4))
        assertFalse(kept(mask, w, 0, 0))
    }

    @Test
    fun `rects are read in page coordinates when the strip starts lower on the page`() {
        val (ink, w, h) = strip(
            "#..#",
        )
        val rects = listOf(InkRect(7, 0, 100, 2, 1), InkRect(8, 2, 100, 2, 1))
        val mask = InkOwnership.keepMask(ink, w, h, top = 100, rects = rects, keep = setOf(8))
        assertFalse(kept(mask, w, 0, 0))
        assertTrue(kept(mask, w, 3, 0))
    }

    @Test
    fun `ink far from every ayah belongs to none, however near the kept one is`() {
        // A surah header's ornament: a blob between lines, further from either than a mark can be.
        val (ink, w, h) = strip(
            "##...",
            ".....",
            ".....",
            "..#..",
            ".....",
            ".....",
            "...##",
        )
        val rects = listOf(InkRect(1, 0, 0, 5, 1), InkRect(2, 0, 6, 5, 1))
        val mask = InkOwnership.keepMask(ink, w, h, top = 0, rects = rects, keep = setOf(1, 2), maxGap = 1)
        assertTrue(kept(mask, w, 0, 0))
        assertTrue(kept(mask, w, 4, 6))
        assertFalse("two rows past the nearer line, one allowed", kept(mask, w, 2, 3))
    }

    @Test
    fun `background is never kept`() {
        val (ink, w, h) = strip("....")
        val mask = InkOwnership.keepMask(ink, w, h, 0, listOf(InkRect(1, 0, 0, 4, 1)), setOf(1))
        assertTrue(mask.none { it })
    }
}
