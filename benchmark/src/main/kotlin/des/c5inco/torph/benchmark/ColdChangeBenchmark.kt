package des.c5inco.torph.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The demo's `benchmark` build type carries an applicationIdSuffix so it coexists with the debug install. */
private const val PACKAGE = "des.c5inco.torph.demo.benchmark"

/**
 * The first text changes after a cold start, before the JIT has compiled anything: the case a
 * Baseline Profile is for. [PerfScreenBenchmark] restarts warm, so by its measured frames the hot
 * code is JIT-compiled with or without a profile.
 *
 * Each iteration kills the process, opens the Perf screen (cycling 200 characters), lets two
 * changes happen, then switches to 1000 characters for two more. Frame timing covers all of it;
 * the per-change cost is in logcat (`adb logcat -s TextMorphPerf`), where the first changes after
 * each launch are the cold ones.
 */
@RunWith(AndroidJUnit4::class)
class ColdChangeBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun firstChangesAfterColdStart() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(),
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Perf")), 5_000)
        device.findObject(By.text("Perf")).click()
        Thread.sleep(3_200) // two 200-character changes
        device.wait(Until.hasObject(By.text("1000 chars")), 5_000)
        device.findObject(By.text("1000 chars")).click()
        Thread.sleep(3_200) // the switch itself, then two 1000-character changes
    }
}
