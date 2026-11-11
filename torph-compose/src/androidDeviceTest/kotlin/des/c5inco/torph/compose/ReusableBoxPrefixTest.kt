package des.c5inco.torph.compose

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Boxes kept for [reusableBoxPrefix]'s lines must equal a fresh fill of the new layout. */
@RunWith(AndroidJUnit4::class)
class ReusableBoxPrefixTest {
    @get:Rule
    val rule = createComposeRule()

    private val lorem = "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor " +
        "incididunt ut labore et dolore magna aliqua ut enim ad minim veniam quis nostrud exercitation " +
        "ullamco laboris nisi aliquip ex ea commodo consequat duis aute irure dolor in reprehenderit"

    private val pairs = listOf(
        lorem to lorem.replace("reprehenderit", "voluptate"), // edit near the end
        lorem to lorem.replace("magna", "extraordinarily"), // middle, longer word: later lines reflow
        lorem to lorem.replace("lorem", "x"), // first word: nothing reusable
        lorem to "$lorem velit esse", // append, as streaming text does
        "$lorem velit esse" to lorem, // delete from the end
        lorem to lorem, // unchanged
        "first paragraph here\nsecond one\nthird $lorem" to "first paragraph here\nsecond one\nthird $lorem!",
        "مرحبا بالعالم هذا نص طويل للاختبار مرحبا بالعالم هذا نص طويل" to "مرحبا بالعالم هذا نص طويل للاختبار مرحبا بالعالم هذا نص قصير",
        "shalom שלום עולם and then English words keep going" to "shalom שלום עולם and then English words stop here",
        // No strong character before the edit: the appended Hebrew decides the paragraph direction.
        "123 456 789 012 345 678 901 234 567 890" to "123 456 789 012 345 678 901 234 567 890 שלום",
        "emoji 👩‍👩‍👧 and flags 🇯🇵🇫🇷 in a line that wraps around" to "emoji 👩‍👩‍👧 and flags 🇯🇵🇫🇷 in a line that wraps over",
    )

    @Test
    fun reusedBoxesMatchAFreshFill() {
        lateinit var measurer: TextMeasurer
        rule.setContent { measurer = rememberTextMeasurer(cacheSize = 0) }
        rule.waitForIdle()
        var reusedAny = false
        for (width in listOf(240, 600, Constraints.Infinity)) {
            for ((before, after) in pairs) {
                val old = measure(measurer, before, width)
                val new = measure(measurer, after, width)
                val reused = reusableBoxPrefix(old, new)
                if (reused > 0) reusedAny = true

                val incremental = fill(old, FloatArray(maxOf(before.length, after.length) * 4))
                if (reused < after.length) new.multiParagraph.fillBoundingBoxes(TextRange(reused, after.length), incremental, reused * 4)
                val fresh = fill(new, FloatArray(after.length * 4))
                assertArrayEquals("width=$width, \"$after\" reused $reused", fresh, incremental.copyOf(after.length * 4), 0f)
            }
        }
        assertTrue("an edit late in a long paragraph should reuse its leading lines", reusedAny)
    }

    @Test
    fun differentWidthReusesNothing() {
        lateinit var measurer: TextMeasurer
        rule.setContent { measurer = rememberTextMeasurer(cacheSize = 0) }
        rule.waitForIdle()
        assertEquals(0, reusableBoxPrefix(measure(measurer, lorem, 240), measure(measurer, lorem, 600)))
    }

    private fun measure(measurer: TextMeasurer, text: String, width: Int): TextLayoutResult = measurer.measure(
        text = AnnotatedString(text),
        style = TextStyle(fontSize = 16.sp),
        softWrap = true,
        constraints = if (width == Constraints.Infinity) Constraints() else Constraints(maxWidth = width),
    )

    private fun fill(layout: TextLayoutResult, into: FloatArray): FloatArray {
        val n = layout.layoutInput.text.length
        layout.multiParagraph.fillBoundingBoxes(TextRange(0, n), into, 0)
        return into
    }
}
