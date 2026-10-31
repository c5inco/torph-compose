package des.c5inco.torph.compose

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import des.c5inco.torph.core.SegmentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Boundary shortcuts must preserve the old per-character union, including soft wraps. */
@RunWith(AndroidJUnit4::class)
class SegmentBoundsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun asciiInteriorBoundariesMatchCharacterBoxes() {
        lateinit var measurer: TextMeasurer
        rule.setContent { measurer = rememberTextMeasurer(cacheSize = 0) }
        rule.waitForIdle()
        var checked = 0
        for (text in listOf(
            "office affinity AVATAR 123,456.78 !? spaces   at end  ",
            "first line\n\nsecond paragraph with a verylongwordthatwraps and digits 1234",
        )) for (width in listOf(90, 240, 600)) for (style in listOf(
            TextStyle(fontSize = 18.sp),
            TextStyle(fontSize = 18.sp, letterSpacing = 2.sp),
            TextStyle(fontSize = 18.sp, textAlign = TextAlign.Justify),
            TextStyle(fontSize = 18.sp, textDirection = TextDirection.Rtl),
        )) {
            val layout = measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = width))
            val boxes = FloatArray(text.length * 4)
            layout.multiParagraph.fillBoundingBoxes(TextRange(0, text.length), boxes, 0)
            val positions = FloatArray(text.length + 1) { Float.NaN }
            for (start in text.indices) for (end in start + 1..minOf(text.length, start + 12)) {
                val line = layout.getLineForOffset(start)
                val bounds = asciiSegmentBounds(layout, start, end, line, positions) ?: continue
                var left = Float.POSITIVE_INFINITY
                var right = Float.NEGATIVE_INFINITY
                for (i in start until end) {
                    val l = boxes[4 * i]
                    val r = boxes[4 * i + 2]
                    if (r - l > 0f || l != 0f) {
                        left = minOf(left, l)
                        right = maxOf(right, r)
                    }
                }
                assertEquals("left $text [$start,$end) width=$width style=$style", left, bounds.left, 0.01f)
                assertEquals("right $text [$start,$end) width=$width style=$style", right, bounds.right, 0.01f)
                checked++
            }
        }
        assertTrue(checked > 1000)
    }

    @Test
    fun placedCellsMatchCharacterUnionsAcrossWrapsAndScripts() {
        lateinit var state: TextMorphState
        var target by mutableStateOf("ASCII words that wrap with numbers 12345 and punctuation!\nnext line")
        var targetStyle by mutableStateOf(TextStyle(fontSize = 18.sp))
        rule.setContent {
            state = rememberTextMorphState()
            TextMorph(
                text = target,
                state = state,
                modifier = Modifier.width(100.dp),
                style = targetStyle,
                disabled = true,
            )
        }
        for (style in listOf(
            TextStyle(fontSize = 18.sp),
            TextStyle(fontSize = 18.sp, letterSpacing = (-1).sp),
            TextStyle(fontSize = 18.sp, textGeometricTransform = TextGeometricTransform(scaleX = -1f)),
        )) for (text in listOf(
            "ASCII words that wrap with numbers 12345 and punctuation!\nnext line",
            "ASCII words that wrap with numbers 12345 and punctuation!\nnext line edited",
            "ASCII words that wrap with numbers 12345 and punctuation!\nnext line edited appended",
            "ASCII words that wrap with numbers 12345 and punctuation!\nnext",
            "short",
            "مرحبا بالعالم mixed English 123 שלום 👩‍👩‍👧",
            "Back to ASCII 9876 with a verylongwordthatwrapsovermorethanoneline",
        )) {
            rule.runOnIdle { target = text; targetStyle = style }
            rule.waitForIdle()
            rule.runOnIdle {
                val layout = state.layoutResult!!
                val boxes = FloatArray(text.length * 4)
                layout.multiParagraph.fillBoundingBoxes(TextRange(0, text.length), boxes, 0)
                for (live in state.live) {
                    val segment = live.segment
                    if (segment.kind == SegmentKind.NEWLINE) continue
                    var left = Float.POSITIVE_INFINITY
                    var right = Float.NEGATIVE_INFINITY
                    for (i in segment.index until segment.end) {
                        val l = boxes[4 * i]
                        val r = boxes[4 * i + 2]
                        if (r - l > 0f || l != 0f) {
                            left = minOf(left, l)
                            right = maxOf(right, r)
                        }
                    }
                    if (left == Float.POSITIVE_INFINITY) {
                        left = layout.getCursorRect(segment.index).left
                        right = left
                    }
                    val line = layout.getLineForOffset(segment.index)
                    assertEquals(Rect(left, layout.getLineTop(line), right, layout.getLineBottom(line)), live.cell)
                }
            }
        }
    }
}
