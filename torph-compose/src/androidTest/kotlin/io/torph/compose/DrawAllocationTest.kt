package io.torph.compose

import android.os.Debug
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.torph.core.Segmentation
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

/**
 * Steady-state per-frame allocation while ~200 segments animate. Measures process-wide
 * ART allocation across real frames, so the number includes Compose's own per-frame work;
 * the assertion is a generous ceiling meant to catch regressions like per-segment lambdas or
 * string building in the draw lambda, not to prove zero.
 */
@RunWith(AndroidJUnit4::class)
class DrawAllocationTest {
    @get:Rule
    val rule = createComposeRule()

    private val a = "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua ut enim ad minim veniam quis nostrud exercitation ullamco laboris nisi 123 aliquip"
    private val b = "lorem ipsum dolor sat amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua ut enim ad minim veniam quis nostrud exercitation ullamco laboris nisi 987 aliquip!"

    @Test
    fun steadyStateDrawAllocatesLittle() {
        var text by mutableStateOf(a)
        lateinit var state: TextMorphState
        rule.mainClock.autoAdvance = false
        rule.setContent {
            state = rememberTextMorphState()
            TextMorph(
                text = text,
                state = state,
                style = TextStyle(fontSize = 16.sp),
                segmentation = Segmentation.GRAPHEME,
                duration = 4000.milliseconds,
                respectReducedMotion = false,
            )
        }
        rule.mainClock.advanceTimeByFrame()
        text = b
        // Warm-up: the diff frame plus a few animated frames (JIT, caches).
        repeat(10) { rule.mainClock.advanceTimeByFrame() }
        Runtime.getRuntime().gc()
        val frames = 60
        val before = allocatedBytes()
        repeat(frames) { rule.mainClock.advanceTimeByFrame() }
        val perFrame = (allocatedBytes() - before) / frames
        Log.i("DrawAllocationTest", "live=${state.liveCount} bytes/frame=$perFrame")
        assertTrue("segments should still be animating", state.isAnimating)
        assertTrue("allocated $perFrame bytes/frame for ${state.liveCount} live segments", perFrame < 16 * 1024)
    }

    private fun allocatedBytes(): Long = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: 0L
}
