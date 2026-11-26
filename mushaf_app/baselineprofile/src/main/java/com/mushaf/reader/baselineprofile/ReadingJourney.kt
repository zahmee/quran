package com.mushaf.reader.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val PACKAGE = "com.mushaf.reader"

/**
 * What a reader does in the first minute: the mushaf opens, a few pages are turned forward and
 * back, and an ayah is pressed to bring up its card. Shared by the profile generator and the
 * benchmarks, so the profile covers exactly what is measured.
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
        device.waitForIdle()
    }
    repeat(count) {
        device.swipe(right, y, left, y, 20)
        device.waitForIdle()
    }
}

/** Presses an ayah in the middle of the page, waits for its card, and closes it again. */
internal fun MacrobenchmarkScope.openAyahCard() {
    val x = device.displayWidth / 2
    val y = device.displayHeight / 2
    device.swipe(x, y, x, y, 120) // a press held about 0.6 s: a long press
    if (device.wait(Until.hasObject(By.text("التفسير")), 3_000)) {
        device.pressBack()
        device.waitForIdle()
    }
}
