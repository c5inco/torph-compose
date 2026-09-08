package io.torph.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "io.torph.demo"

/**
 * Frame timing while the Perf screen cycles 50 / 200 / 1000-character strings every 1.5 s.
 * Run with an emulator or device attached:
 *
 *     ./gradlew :benchmark:connectedBenchmarkAndroidTest
 *
 * Results land in benchmark/build/outputs/connected_android_test_additional_output (JSON per test);
 * frameDurationCpuMs P50/P90/P99 and frameOverrunMs are the numbers that matter.
 */
@RunWith(AndroidJUnit4::class)
class PerfScreenBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test fun startup() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(),
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test fun morph50() = morph(50)

    @Test fun morph200() = morph(200)

    @Test fun morph1000() = morph(1000)

    /** Word segmentation at 1000 chars: what the >300-segment guard falls back to. */
    @Test fun morph1000Word() = morph(1000, mode = "WORD")

    private fun morph(chars: Int, mode: String? = null) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            pressHome()
            startActivityAndWait()
            openPerf(chars, mode)
        },
    ) {
        // Three cycles of the 1.5 s rotation: ~4.5 s of continuous morphing.
        Thread.sleep(4_800)
    }

    private fun MacrobenchmarkScope.openPerf(chars: Int, mode: String?) {
        device.wait(Until.hasObject(By.text("Perf")), 5_000)
        device.findObject(By.text("Perf")).click()
        device.wait(Until.hasObject(By.text("$chars chars")), 5_000)
        device.findObject(By.text("$chars chars")).click()
        if (mode != null) device.findObject(By.text(mode))?.click()
        // Let the first layout and cycle settle before measuring.
        Thread.sleep(1_600)
    }
}
