package des.c5inco.torph.benchmark

import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@OptIn(androidx.benchmark.macro.ExperimentalMetricApi::class, androidx.benchmark.macro.ExperimentalMacrobenchmarkApi::class)
@RunWith(AndroidJUnit4::class)
class FirstLayoutBenchmark {
    companion object {
        @ClassRule @JvmField val deviceState: TestRule = BenchmarkDeviceState()
    }
    // API 33 resets profiles via uninstall/reinstall, which can block on package verification.
    // Compile every method with speed explicitly and retain the install on older Android.
    @Before fun compileWithoutReinstallOnOlderAndroid() {
        if (Build.VERSION.SDK_INT < 34) {
            val output = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand("cmd package compile -f -m speed des.c5inco.torph.demo.benchmark")
            check(output.trim() == "Success") { "Full compilation failed: $output" }
        }
    }

    @get:Rule val rule = MacrobenchmarkRule()
    @FirstAppearanceWarm
    @FirstAppearanceEight
    @BidiPlacementWorkload
    @AsciiPlainControl
    @Test fun warmPlain8() = first("plain", 8, warm = true)
    @FirstAppearanceWarm
    @FirstAppearanceEight
    @BidiPlacementWorkload
    @BidiAsciiMorphControl
    @Test fun warmMorph8() = first("morph", 8, warm = true)
    @FirstAppearanceWarm
    @FirstAppearanceEight
    @FirstAppearanceAutoEight
    @Test fun warmMorph8Auto() = first("morphAuto", 8, warm = true)
    @FirstAppearanceWarm
    @Test fun warmPlain16() = first("plain", 16, warm = true)
    @FirstAppearanceWarm
    @Test fun warmMorph16() = first("morph", 16, warm = true)
    @FirstAppearanceWarm
    @Test fun warmMorph16Auto() = first("morphAuto", 16, warm = true)
    @FirstAppearanceWarm
    @Test fun warmPlain8Repeated() = first("plain", 8, repeated = true, warm = true)
    @FirstAppearanceWarm
    @Test fun warmMorph8Repeated() = first("morph", 8, repeated = true, warm = true)
    @BidiPlacementWorkload
    @BidiMorphControl
    @BidiAsciiMorphControl
    @Test fun warmBidi8() = first("morphBidi", 8, warm = true)
    @BidiPlacementWorkload
    @BidiMorphControl
    @Test fun warmBidi8Auto() = first("morphAutoBidi", 8, warm = true)
    @BidiPlacementWorkload
    @BidiPlainControl
    @Test fun warmPlainBidi8() = first("plainBidi", 8, warm = true)
    @Test fun morph8Auto() = first("morphAuto", 8)
    @Test fun morph16Auto() = first("morphAuto", 16)
    @Test fun plain8() = first("plain", 8)
    @Test fun morph8() = first("morph", 8)
    @Test fun plain16() = first("plain", 16)
    @Test fun morph16() = first("morph", 16)
    @Test fun plain8Repeated() = first("plain", 8, true)
    @Test fun morph8Repeated() = first("morph", 8, true)

    @Test fun firstUpdate8() = first("morphChange", 8, warm = true)
    @Test fun firstUpdate8Auto() = first("morphAutoChange", 8, warm = true)

    @Test fun firstUpdate8AutoBidi() = first("morphAutoChangeBidi", 8, warm = true)

    @Test fun warmPlain16Distinct() = first("plainUnique", 16, warm = true)
    @Test fun warmMorph16Distinct() = first("morphUnique", 16, warm = true)
    @Test fun warmMorph16AutoDistinct() = first("morphAutoUnique", 16, warm = true)

    private fun first(mode: String, count: Int, repeated: Boolean = false, warm: Boolean = false) = rule.measureRepeated(
        packageName = "des.c5inco.torph.demo.benchmark",
        metrics = listOf(FrameTimingMetric()) + listOf("Labels:firstMeasure", "Labels:firstUpdate", "Morph:firstApply", "Morph:apply", "Morph:fullMeasure",
            "Morph:segment", "Morph:prepareSnap", "Morph:segmentMeasure", "TextLayout:initLayout").map {
            TraceSectionMetric(it, TraceSectionMetric.Mode.Sum)
        },
        compilationMode = if (Build.VERSION.SDK_INT < 34) CompilationMode.Ignore() else CompilationMode.Full(),
        startupMode = if (warm) StartupMode.WARM else StartupMode.COLD,
        iterations = if (warm) 5 else 10,
        setupBlock = {
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            pressHome()
            device.waitForIdle()
            if (warm) startActivityAndWait(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(packageName, "des.c5inco.torph.demo.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
        },
    ) {
        startActivityAndWait(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            setClassName(packageName, "des.c5inco.torph.demo.MainActivity")
            putExtra("firstLayout", mode)
            putExtra("labels", count)
            putExtra("repeated", repeated)
        })
        if (mode.contains("Change")) {
            check(device.wait(Until.hasObject(By.desc("first-update-complete:$mode:$count")), 5_000))
            device.waitForIdle()
        } else {
            check(device.wait(Until.hasObject(By.desc("first-layout-ready:$mode:$count")), 5_000))
        }
    }
}

/** Fresh labels after the normal demo has warmed the process. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class FirstAppearanceWarm

/** Eight-label subset for a second-device comparison. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class FirstAppearanceEight

/** Select the eight-label AUTO workload for targeted captures. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class FirstAppearanceAutoEight

/** Matched ASCII/bidi eight-label workloads for placement correctness follow-ups. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BidiPlacementWorkload

/** Plain-bidi subset for a whole-case recheck when trace markers are missing. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BidiPlainControl

/** ASCII plain-Text subset for trace-marker validity rechecks. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AsciiPlainControl

/** Bidi morph subset for focused placement-cost experiments. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BidiMorphControl

/** Complete GRAPHEME cases for a recheck when first-layout markers are missing. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BidiAsciiMorphControl
