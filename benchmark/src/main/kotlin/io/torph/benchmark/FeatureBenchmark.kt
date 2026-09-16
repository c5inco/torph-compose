package io.torph.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "io.torph.demo.benchmark"

/**
 * Frame timing for the features [PerfScreenBenchmark] does not cover: place-value digit rolling,
 * and retargeting under rapid updates. Both are the cases most likely to behave differently from
 * plain text morphing, since rolling builds a layout strip per changed digit and interruption
 * rebuilds animation state mid-flight.
 */
@RunWith(AndroidJUnit4::class)
class FeatureBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Currency counter jumping to random values: many digits change at once, each rolling. */
    @Test
    fun numberRolling() = frameTiming("Numbers") {
        repeat(8) {
            device.findObject(By.text("Random"))?.click()
            Thread.sleep(600)
        }
    }

    /** Single-digit steps, the common counter case: one or two digits roll per change. */
    @Test
    fun numberIncrement() = frameTiming("Numbers") {
        repeat(16) {
            device.findObject(By.text("+1"))?.click()
            Thread.sleep(300)
        }
    }

    /**
     * Ticking timer at 80 ms plus a live-typing simulation, so almost every morph is interrupted
     * before it settles. Exercises velocity-preserving retargeting.
     */
    @Test
    fun interruption() = frameTiming("Interruption") {
        Thread.sleep(4_800)
    }

    /** Multi-line paragraph swapped repeatedly, so wrapped lines re-flow. */
    @Test
    fun multiLine() = frameTiming("Multi-line") {
        repeat(6) {
            device.findObject(By.text("Swap paragraph"))?.click()
            Thread.sleep(800)
        }
    }

    private fun frameTiming(screen: String, measure: MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            pressHome()
            startActivityAndWait()
            open(screen)
        },
        measureBlock = measure,
    )

    private fun MacrobenchmarkScope.open(screen: String) {
        device.wait(Until.hasObject(By.text(screen)), 5_000)
        device.findObject(By.text(screen))?.click()
        // Let the first layout and any entry animation settle before measuring.
        Thread.sleep(1_200)
    }
}
