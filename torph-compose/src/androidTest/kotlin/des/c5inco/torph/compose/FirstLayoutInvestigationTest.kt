package des.c5inco.torph.compose

import android.util.Log
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import des.c5inco.torph.core.Segmentation
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Feasibility probe, not a golden test: quantify the renderer switch a lazy path would require. */
@RunWith(AndroidJUnit4::class)
class FirstLayoutInvestigationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sharedMeasurerPreservesSegmentsAndSeparatesLayoutInputs() {
        lateinit var measurer: androidx.compose.ui.text.TextMeasurer
        lateinit var scope: CoroutineScope
        rule.setContent {
            measurer = rememberTextMeasurer(cacheSize = 128)
            scope = rememberCoroutineScope()
        }
        rule.runOnIdle {
            fun state(text: String, style: TextStyle = TextStyle(fontSize = 24.sp)) =
                TextMorphState(measurer, scope, IcuSegmenter()).also {
                    it.text = text; it.style = style
                    it.options = MorphOptions(segmentation = Segmentation.GRAPHEME)
                    it.measure(Constraints(maxWidth = 300), LayoutDirection.Ltr)
                }
            val a = state("12,345")
            val b = state("12,345")
            // TextMeasurer can return copies on a hit; paragraph identity reveals reuse.
            for ((left, right) in a.live.zip(b.live)) {
                assertTrue(left.layout.multiParagraph === right.layout.multiParagraph)
                assertEquals(left.x.value, right.x.value)
                assertEquals(left.y.value, right.y.value)
            }
            val changedStyle = state("12,345", TextStyle(fontSize = 30.sp))
            assertNotSame(a.live.first().layout.multiParagraph, changedStyle.live.first().layout.multiParagraph)
            val wrapped = state("words wrapping across lines words wrapping")
            wrapped.measure(Constraints(maxWidth = 80), LayoutDirection.Ltr)
            assertTrue(wrapped.layoutResult!!.lineCount > 1)
        }
    }

    @Test fun compareSegmentRendererWithFullString() {
        lateinit var measurer: androidx.compose.ui.text.TextMeasurer
        lateinit var scope: CoroutineScope
        rule.setContent {
            measurer = rememberTextMeasurer(cacheSize = 0)
            scope = rememberCoroutineScope()
        }
        rule.runOnIdle {
            var differences = 0
            for (mode in listOf(Segmentation.AUTO, Segmentation.GRAPHEME)) {
                for (text in listOf("12,345", "AVATAR To Wa", "office affinity ffi", "مرحبا بالعالم", "नमस्ते दुनिया", "ภาษาไทย", "e\u0301 👩‍👩‍👧‍👦", "words wrap here\nnext line")) {
                    val state = TextMorphState(measurer, scope, IcuSegmenter())
                    state.text = text
                    state.style = TextStyle(fontSize = 24.sp, color = Color.Black)
                    state.options = MorphOptions(segmentation = mode, numbers = true)
                    state.measure(Constraints(maxWidth = 300), LayoutDirection.Ltr)
                    val full = state.layoutResult!!
                    val w = full.size.width + 40
                    val h = full.size.height + 40
                    fun render(segments: Boolean): IntArray {
                        val bitmap = ImageBitmap(w, h)
                        CanvasDrawScope().draw(full.layoutInput.density, LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
                            if (segments) state.draw(this) else drawText(full)
                        }
                        return IntArray(w * h).also { bitmap.asAndroidBitmap().getPixels(it, 0, w, 0, 0, w, h) }
                    }
                    val a = render(true)
                    val b = render(false)
                    val changed = a.indices.count { a[it] != b[it] }
                    if (changed > 0) differences++
                    Log.i("FirstLayoutPixels", "mode=$mode text=$text changed=$changed pixels=${a.size}")
                    assertTrue(full.size.width > 0 && full.lineCount > 0)
                }
            }
            // A renderer switch needs a continuity design, even if full-string shaping is preferable.
            assertTrue("Expected shaping/placement differences in the probe corpus", differences > 0)
        }
    }
}
