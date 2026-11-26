package com.mushaf.reader.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile: the classes and methods the reading journey runs, which
 * the device then compiles ahead of time instead of interpreting them on first use.
 *
 *   ./gradlew :app:generateReleaseBaselineProfile
 *
 * writes app/src/release/generated/baselineProfiles/, which is kept in git.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        turnPages()
        openAyahCard()
    }
}
