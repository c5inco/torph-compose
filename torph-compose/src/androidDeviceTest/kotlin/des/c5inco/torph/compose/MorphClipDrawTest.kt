package des.c5inco.torph.compose

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

/**
 * Where rolling digits draw relative to their line box under each [MorphClip]: every frame of a roll
 * is captured over a white stage with room around the text, and the ink furthest past the line box's
 * top or bottom edge is measured as a fraction of the line height (negative: it stayed inside).
 */
@RunWith(AndroidJUnit4::class)
class MorphClipDrawTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun defaultLetsRollingDigitsDrawAQuarterLinePastTheLineBox() {
        val overflow = inkOverflow(MorphClip.Default, from = "1", to = "8")
        assertTrue("neighbours peek out of the line box: $overflow", overflow > 0.05f)
        assertTrue("but no further than the quarter-line margin: $overflow", overflow <= 0.25f + EDGE)
    }

    @Test
    fun lineBoxKeepsRollingDigitsInsideTheLineBox() {
        val overflow = inkOverflow(MorphClip.lineBox(), from = "1", to = "8")
        assertTrue("drew $overflow of a line past the line box", overflow <= EDGE)
    }

    @Test
    fun windowClipsInsideTheLineBox() {
        val overflow = inkOverflow(MorphClip.window(), from = "1", to = "8")
        assertTrue("drew to $overflow of a line from the line box, not inside its 6% margin", overflow <= -0.06f + EDGE)
    }

    @Test
    fun negativeOverflowClipsThatFarInside() {
        val overflow = inkOverflow(MorphClip(overflow = -0.15f, inset = 0.2f), from = "1", to = "8")
        assertTrue("drew to $overflow of a line from the line box, not inside its 15% margin", overflow <= -0.15f + EDGE)
    }

    @Test
    fun positiveOverflowAllowsThatMuchMargin() {
        val overflow = inkOverflow(MorphClip(overflow = 0.1f, inset = 0.1f), from = "1", to = "8")
        assertTrue("neighbours peek out of the line box: $overflow", overflow > 0.02f)
        assertTrue("but no further than 10%: $overflow", overflow <= 0.1f + EDGE)
    }

    // A tens digit rolling in or out has no digit at its place to roll from or to, so it slides
    // through its cell's clip instead of an odometer strip.
    @Test
    fun enteringDigitsAreClipped() {
        assertTrue(inkOverflow(MorphClip.lineBox(), from = "5", to = "15") <= EDGE)
    }

    @Test
    fun exitingDigitsAreClipped() {
        assertTrue(inkOverflow(MorphClip.lineBox(), from = "15", to = "5") <= EDGE)
    }

    @Test
    fun enteringDigitsKeepTheDefaultMargin() {
        assertTrue(inkOverflow(MorphClip.Default, from = "5", to = "15") > 0.05f)
    }

    @Test
    fun numericOverloadPassesTheClipThrough() {
        assertTrue(inkOverflow(MorphClip.lineBox(), from = "1", to = "8", numeric = true) <= EDGE)
    }

    /** How far past the line box, in line heights, any frame of the roll from [from] to [to] inked. */
    private fun inkOverflow(clip: MorphClip, from: String, to: String, numeric: Boolean = false): Float {
        var text by mutableStateOf(from)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.testTag(STAGE).background(Color.White).padding(48.dp)) {
                val style = TextStyle(fontSize = 48.sp, color = Color.Black)
                val ease = MorphEase.Curve(MorphEasings.Linear)
                val modifier = Modifier.testTag(TEXT)
                if (numeric) {
                    TextMorph(value = text.toInt(), modifier = modifier, style = style, ease = ease,
                        duration = 600.milliseconds, clip = clip, respectReducedMotion = false)
                } else {
                    TextMorph(text = text, modifier = modifier, style = style, ease = ease,
                        duration = 600.milliseconds, clip = clip, respectReducedMotion = false)
                }
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        val stage = rule.onNodeWithTag(STAGE).fetchSemanticsNode().boundsInRoot
        val box = rule.onNodeWithTag(TEXT).fetchSemanticsNode().boundsInRoot
        val top = box.top - stage.top
        val bottom = box.bottom - stage.top
        val lineHeight = box.height

        text = to
        var inkTop = Int.MAX_VALUE
        var inkBottom = -1
        repeat(45) { // the whole 600 ms roll, a frame at a time
            rule.mainClock.advanceTimeByFrame()
            val pixels = rule.onNodeWithTag(STAGE).captureToImage().toPixelMap()
            var y = 0
            while (y < pixels.height) {
                var x = 0
                while (x < pixels.width) {
                    if (pixels[x, y].red < INK) {
                        inkTop = minOf(inkTop, y)
                        inkBottom = maxOf(inkBottom, y)
                        break
                    }
                    x++
                }
                y++
            }
        }
        val overflow = max(top - inkTop, inkBottom + 1 - bottom) / lineHeight
        Log.i("MorphClipDrawTest", "$clip $from->$to numeric=$numeric: line=$lineHeight px, line box $top..$bottom, ink $inkTop..$inkBottom, overflow=$overflow")
        return overflow
    }

    private companion object {
        const val STAGE = "stage"
        const val TEXT = "text"
        /** Any pixel visibly darker than the white stage. */
        const val INK = 0.97f
        /** Antialiasing at the clip's edge: a pixel row, as a fraction of a 48sp line. */
        const val EDGE = 0.02f
    }
}
