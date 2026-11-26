package com.mushaf.reader.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start, measured twice: as a first install runs before any compilation
 * ([CompilationMode.None]), and with the Baseline Profile compiled in. The gap between the two is
 * what the profile is worth. (Page turning is in the profile but not measured here: on the
 * emulator FrameTimingMetric records no frames for the pager, so its numbers need a phone.)
 *
 *   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ReaderBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}
