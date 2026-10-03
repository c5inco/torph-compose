package des.c5inco.torph.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile shipped in `:torph-compose` by driving every demo screen, so
 * segmentation, the diff, placement, number rolling and the frame loop are all exercised.
 * Run with one device attached (not an emulator):
 *
 *     ./gradlew :torph-compose:generateBaselineProfile
 *
 * The rules are filtered to `des.c5inco.torph.**` and copied into
 * torph-compose/src/main/generated/baselineProfiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        // The plugin runs against :demo's non-minified release build, which has no id suffix.
        packageName = "des.c5inco.torph.demo",
    ) {
        pressHome()
        startActivityAndWait()

        visit("Perf") {
            for (chars in listOf(50, 200, 1000)) {
                device.findObject(By.text("$chars chars"))?.click()
                Thread.sleep(4_600) // three 1.5 s cycles
            }
        }
        visit("Numbers") {
            repeat(6) {
                device.findObject(By.text("Random"))?.click()
                Thread.sleep(500)
                device.findObject(By.text("+1"))?.click()
                Thread.sleep(300)
            }
        }
        visit("Interruption") { Thread.sleep(3_000) }
        visit("Multi-line") {
            repeat(4) {
                device.findObject(By.text("Swap paragraph"))?.click()
                Thread.sleep(700)
            }
        }
        visit("Typing") {
            device.findObject(By.clazz("android.widget.EditText"))?.text = "Total 1,234.56 today"
            Thread.sleep(700)
            device.findObject(By.clazz("android.widget.EditText"))?.text = "Total 1,299.56 tomorrow"
            Thread.sleep(700)
        }
        visit("Scripts") { Thread.sleep(3_000) }
    }

    private fun MacrobenchmarkScope.visit(screen: String, block: MacrobenchmarkScope.() -> Unit) {
        device.wait(Until.hasObject(By.text(screen)), 5_000)
        device.findObject(By.text(screen))?.click() ?: return
        Thread.sleep(800)
        block()
        device.pressBack()
        device.waitForIdle()
    }
}
