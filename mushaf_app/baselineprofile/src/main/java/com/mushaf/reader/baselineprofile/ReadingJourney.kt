package com.mushaf.reader.baselineprofile

import android.graphics.Point
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val PACKAGE = "com.mushaf.reader"

/** An action on the ayah card; seeing it means the card is open. */
private const val CARD_ACTION = "التفسير"

/**
 * How long a turned page is left to come to rest. waitForIdle returns while the pager is still
 * settling, and a touch that lands then is taken by the pager as the start of a drag: the next
 * swipe still turns a page, but a long press never reaches the ayah under it.
 */
private const val PAGE_SETTLE_MS = 800L

/** Longer than any device's long-press timeout (400 ms by default). */
private const val LONG_PRESS_MS = 700L

/**
 * What a reader does in the first minute: the mushaf opens, a few pages are turned forward and
 * back, and an ayah is pressed to bring up its card. The profile generator runs it, so the
 * profile covers this and nothing it did not actually do: a step that fails stops the run.
 */
internal fun MacrobenchmarkScope.turnPages(count: Int = 4) {
    // By coordinates, not through the pager's UI object: each page turn recomposes the pager,
    // and a held object goes stale. The swipe stays clear of the edges, where the system's back
    // gesture lives. In this right-to-left mushaf a swipe to the right turns to the next page.
    val y = device.displayHeight / 2
    val left = device.displayWidth / 5
    val right = device.displayWidth * 4 / 5
    repeat(count) {
        device.swipe(left, y, right, y, 20)
        settle()
    }
    repeat(count) {
        device.swipe(right, y, left, y, 20)
        settle()
    }
}

/**
 * Presses an ayah, waits for its card, and closes it again. The middle of the screen is tried
 * first, then points above and below it, in case the middle falls on a gap in the page.
 */
internal fun MacrobenchmarkScope.openAyahCard() {
    val window = checkNotNull(device.findObject(By.pkg(PACKAGE).depth(0))) {
        "The reader's window was not found."
    }
    val x = device.displayWidth / 2
    val opened = listOf(0.5f, 0.4f, 0.6f, 0.3f).any { part ->
        window.click(Point(x, (device.displayHeight * part).toInt()), LONG_PRESS_MS)
        device.wait(Until.hasObject(By.text(CARD_ACTION)), 2_000)
    }
    // Without this the run would go on and write a profile that silently lacks the card.
    check(opened) { "No ayah card opened after a long press; the profile would not cover it." }
    device.pressBack()
    device.wait(Until.gone(By.text(CARD_ACTION)), 2_000)
}

private fun MacrobenchmarkScope.settle() {
    device.waitForIdle()
    SystemClock.sleep(PAGE_SETTLE_MS)
}
