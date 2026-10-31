package des.c5inco.torph.benchmark

import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.rules.ExternalResource

/** Keep animations on and avoid refresh-mode switches, restoring the phone's settings afterward. */
internal class BenchmarkDeviceState : ExternalResource() {
    private lateinit var device: UiDevice
    private val originals = LinkedHashMap<Pair<String, String>, String>()

    override fun before() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        val display = instrumentation.context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        val refreshRate = display.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .maxOf { it.refreshRate }
        val settings = listOf(
            Triple("global", "animator_duration_scale", "1"),
            Triple("system", "peak_refresh_rate", refreshRate.toString()),
            Triple("system", "min_refresh_rate", refreshRate.toString()),
        )
        // Read every original before making any change, including absent settings ("null").
        for ((namespace, key) in settings) {
            val original = device.executeShellCommand("settings get $namespace $key").trim()
            require(original == "null" || original.toFloatOrNull() != null) { "Unexpected $key: $original" }
            originals[namespace to key] = original
        }
        try {
            for ((namespace, key, value) in settings) {
                device.executeShellCommand("settings put $namespace $key $value")
                check(device.executeShellCommand("settings get $namespace $key").trim().toFloatOrNull() == value.toFloat()) {
                    "Could not set $key to $value"
                }
            }
            Log.i(
                "TorphBenchmarkDevice",
                "model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} originals=$originals " +
                    "refreshRates=${display.supportedModes.map { it.refreshRate }.distinct()} " +
                    "requestedRefreshRate=$refreshRate",
            )
        } catch (error: Throwable) {
            restore()
            throw error
        }
    }

    override fun after() = restore()

    private fun restore() {
        // Reverse order restores the minimum before the peak.
        for ((setting, original) in originals.entries.toList().asReversed()) {
            val (namespace, key) = setting
            val command = if (original == "null") {
                "settings delete $namespace $key"
            } else {
                require(original.toFloatOrNull() != null) { "Unexpected $key: $original" }
                "settings put $namespace $key $original"
            }
            device.executeShellCommand(command)
            check(device.executeShellCommand("settings get $namespace $key").trim() == original) {
                "Could not restore $key to $original"
            }
        }
        Log.i("TorphBenchmarkDevice", "restored=$originals")
        originals.clear()
    }
}
