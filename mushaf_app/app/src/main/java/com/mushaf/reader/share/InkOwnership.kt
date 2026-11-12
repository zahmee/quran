package com.mushaf.reader.share

/** One ayah's rectangle on the page, in page-image pixels. [owner] says which ayah it belongs to. */
internal data class InkRect(val owner: Int, val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * Which ink in a strip of the page belongs to which ayah.
 *
 * The ayah rectangles alone are not enough to cut an ayah out cleanly. The printed lines sit close,
 * so a tall alif or a shadda of the line below rises into the band of the line above, and a crop by
 * rectangles drags those fragments of the neighbouring ayah along. Here every connected blob of ink
 * is given whole to one ayah: the one whose rectangles hold most of its pixels, or — for a mark
 * floating in the gap between two lines, inside no rectangle — the rectangle nearest it vertically
 * among those spanning it horizontally.
 *
 * Pure Kotlin on plain arrays, so it runs in unit tests.
 */
internal object InkOwnership {

    /**
     * @param ink row-major [width] x [height], true where the strip holds ink.
     * @param top the strip's first row in page coordinates; [rects] are in page coordinates.
     * @param rects every ayah rectangle on the page, not only the kept ones: they decide who owns
     *   the ink at the edges.
     * @return row-major mask, true where the ink belongs to an owner in [keep].
     */
    fun keepMask(
        ink: BooleanArray,
        width: Int,
        height: Int,
        top: Int,
        rects: List<InkRect>,
        keep: Set<Int>,
        maxGap: Int = MAX_GAP,
    ): BooleanArray {
        require(ink.size == width * height) { "ink is ${ink.size}, expected ${width * height}" }
        val (labels, count) = label(ink, width, height)
        if (count == 0) return BooleanArray(ink.size)

        val owners = rects.map { it.owner }.distinct()
        val ownerIndex = owners.withIndex().associate { (i, o) -> o to i }
        val hits = IntArray(count * owners.size)
        val sumX = LongArray(count)
        val sumY = LongArray(count)
        val size = IntArray(count)

        // Rectangles crossing each row, so the per-pixel lookup scans a line's few, not the page's.
        val rowRects = Array(height) { y -> rects.filter { y + top >= it.y && y + top < it.y + it.h } }
        for (y in 0 until height) {
            val row = rowRects[y]
            for (x in 0 until width) {
                val l = labels[y * width + x]
                if (l == 0) continue
                val c = l - 1
                sumX[c] += x.toLong()
                sumY[c] += y.toLong()
                size[c]++
                for (r in row) {
                    if (x >= r.x && x < r.x + r.w) {
                        hits[c * owners.size + ownerIndex.getValue(r.owner)]++
                        break
                    }
                }
            }
        }

        val kept = BooleanArray(count) { c ->
            var best = -1
            var bestHits = 0
            for (o in owners.indices) {
                val h = hits[c * owners.size + o]
                if (h > bestHits) { best = o; bestHits = h }
            }
            val owner = if (best >= 0) owners[best] else nearestOwner(
                cx = sumX[c].toFloat() / size[c],
                cy = sumY[c].toFloat() / size[c] + top,
                rects = rects,
                maxGap = maxGap,
            )
            owner != null && owner in keep
        }
        return BooleanArray(ink.size) { i -> labels[i] != 0 && kept[labels[i] - 1] }
    }

    /**
     * Owner of the rectangle vertically nearest ([cx], [cy]) among those spanning [cx] — if it is
     * within [maxGap]. Further off, the ink is no ayah's: a surah header, the basmala, the page's
     * ornament. A tashkeel mark between two lines never is that far from either.
     */
    private fun nearestOwner(cx: Float, cy: Float, rects: List<InkRect>, maxGap: Int): Int? {
        var best: InkRect? = null
        var bestGap = Float.MAX_VALUE
        for (r in rects) {
            if (cx < r.x || cx >= r.x + r.w) continue
            val gap = when {
                cy < r.y -> r.y - cy
                cy > r.y + r.h -> cy - (r.y + r.h)
                else -> 0f
            }
            if (gap < bestGap) { best = r; bestGap = gap }
        }
        return best?.takeIf { bestGap <= maxGap }?.owner
    }

    /**
     * How far, in page pixels, ink outside every rectangle may lie from one and still belong to it.
     * The printed lines are 118 px apart with bands 101.5 px tall, so a mark in a gap is at most
     * ~17 px from a band; marks riding above a first line sit a little higher.
     */
    const val MAX_GAP = 28

    /**
     * 8-connected components, two passes with union-find. Labels are 1..count, 0 is background.
     */
    internal fun label(ink: BooleanArray, width: Int, height: Int): Pair<IntArray, Int> {
        val labels = IntArray(ink.size)
        var parent = IntArray(1024)
        var next = 1

        fun find(a: Int): Int {
            var r = a
            while (parent[r] != r) r = parent[r]
            var n = a
            while (parent[n] != r) { val up = parent[n]; parent[n] = r; n = up }
            return r
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) { if (ra < rb) parent[rb] = ra else parent[ra] = rb }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (!ink[i]) continue
                var l = 0
                // Neighbours already visited: west, north-west, north, north-east.
                if (x > 0 && labels[i - 1] != 0) l = labels[i - 1]
                if (y > 0) {
                    val up = i - width
                    if (x > 0 && labels[up - 1] != 0) { if (l == 0) l = labels[up - 1] else union(l, labels[up - 1]) }
                    if (labels[up] != 0) { if (l == 0) l = labels[up] else union(l, labels[up]) }
                    if (x < width - 1 && labels[up + 1] != 0) { if (l == 0) l = labels[up + 1] else union(l, labels[up + 1]) }
                }
                if (l == 0) {
                    if (next == parent.size) parent = parent.copyOf(parent.size * 2)
                    parent[next] = next
                    l = next++
                }
                labels[i] = l
            }
        }

        // Second pass: every provisional label to its root, roots renumbered 1..count.
        val compact = IntArray(next)
        var count = 0
        for (l in 1 until next) {
            val r = find(l)
            if (compact[r] == 0) compact[r] = ++count
            compact[l] = compact[r]
        }
        for (i in labels.indices) if (labels[i] != 0) labels[i] = compact[labels[i]]
        return labels to count
    }
}
