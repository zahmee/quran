package com.mushaf.reader.data

/**
 * The text sizes the tafsir / word-meanings sheet steps through, in percent of the reading size.
 * Fixed steps rather than a free value, so each tap of «تكبير» or «تصغير» is a visible change and
 * the two ends are reachable.
 */
object ExplanationTextScale {
    val STEPS = listOf(85, 100, 115, 130, 150, 175)
    const val DEFAULT = 100

    /** A stored value that is missing or no longer a step falls back to the nearest step. */
    fun normalize(value: Int?): Int =
        if (value == null) DEFAULT else STEPS.minBy { kotlin.math.abs(it - value) }

    /** The next step up ([direction] > 0) or down; stays put at either end. */
    fun step(current: Int, direction: Int): Int {
        val index = STEPS.indexOf(normalize(current))
        val next = (index + direction.coerceIn(-1, 1)).coerceIn(0, STEPS.lastIndex)
        return STEPS[next]
    }

    fun canGrow(current: Int): Boolean = normalize(current) < STEPS.last()

    fun canShrink(current: Int): Boolean = normalize(current) > STEPS.first()
}
