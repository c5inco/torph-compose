package des.c5inco.torph.benchmark

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

/** The demo's `benchmark` build type carries an applicationIdSuffix so it coexists with the debug install. */
private const val PACKAGE = "des.c5inco.torph.demo.benchmark"

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

    // The default: AUTO, which morphs text with spaces (all of these) word by word, as torph does.

    @Test fun morph50() = morph(50)

    @Test fun morph200() = morph(200)

    /** Over the 300-segment cap, where every mode morphs by word, so this is also the worst case. */
    @Test fun morph1000() = morph(1000)

    /** One word of the same 1000-character paragraph changes per cycle, 40-80% of the way through. */
    @Test fun morph1000Edit() = morph(1000, edit = true)

    // The worst case: GRAPHEME, one segment per character.

    @Test fun morph50Grapheme() = morph(50, segmentation = "GRAPHEME")

    @Test fun morph200Grapheme() = morph(200, segmentation = "GRAPHEME")

    private fun morph(chars: Int, edit: Boolean = false, segmentation: String = "AUTO") = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            pressHome()
            startActivityAndWait()
            openPerf(chars, edit, segmentation)
        },
    ) {
        // Three cycles of the 1.5 s rotation: ~4.5 s of continuous morphing.
        Thread.sleep(4_800)
    }

    private fun MacrobenchmarkScope.openPerf(chars: Int, edit: Boolean, segmentation: String) {
        device.wait(Until.hasObject(By.text("Perf")), 5_000)
        device.findObject(By.text("Perf")).click()
        device.wait(Until.hasObject(By.text("$chars chars")), 5_000)
        device.findObject(By.text("$chars chars")).click()
        device.findObject(By.text(if (edit) "Edit one word" else "Replace text")).click()
        // Set explicitly: the screen remembers its last choice across launches.
        device.findObject(By.text(segmentation)).click()
        // Let the first layout and cycle settle before measuring.
        Thread.sleep(1_600)
    }
}
