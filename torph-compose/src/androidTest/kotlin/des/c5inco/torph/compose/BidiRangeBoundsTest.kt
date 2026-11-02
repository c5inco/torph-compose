package des.c5inco.torph.compose

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.EmojiSupportMatch
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test

class BidiRangeBoundsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyRangeMatchesNativeSelectionAcrossBidiLevelsClustersAndWraps() {
        lateinit var measurer: TextMeasurer
        rule.setContent { measurer = rememberTextMeasurer(cacheSize = 0) }
        val ranges = BidiRangeBounds()
        var checked = 0
        var direct = 0
        var fastLayouts = 0
        var singleLineLayouts = 0
        val random = Random(86149)
        val tokens = listOf("אב", "abc", "12", " ", "\n", "\u2067", "\u2069", "\u202B", "\u202C", "e\u0301", "👩🏽‍💻")
        val texts = listOf(
            "שלום 123 (abc)", "abc \u2067שלום ١٢٣\u2069 xyz", "שלום (123) abc (xyz)",
            "abc (שלום) xyz", "abc\u202Bשלום 123 def\u202Cghi",
            "אב\u202Aabc שלום123\u202Cגד", "abc\u202D123 אב\u202Cxyz",
            "abc\u2066אב\u2067123 abc\u2069גד\u2069xyz",
            "שָׁלוֹם 123 مرحبا", "لا 123 office ffi", "אב 👩🏽‍💻 e\u0301 xyz",
            "אב 123\nabc שלום\n", "אב 12   ", "\u2067\u2069", "אב\u200Fabc\u200E12",
            "אב\u200Dגד abc\u200Cdef", "שלום\n\n123 xyz", "אב Supercalifragilistic 123",
        ) + List(16) { List(6) { tokens[random.nextInt(tokens.size)] }.joinToString("") }
        rule.runOnIdle {
            for (text in texts) for (direction in LayoutDirection.entries) for (width in listOf(80, 300, 1200))
                for ((styleIndex, style) in listOf(
                    TextStyle(fontSize = 20.sp, letterSpacing = 0.sp, platformStyle = PlatformTextStyle(emojiSupportMatch = EmojiSupportMatch.None)),
                    TextStyle(fontSize = 20.sp, letterSpacing = (-1).sp, platformStyle = PlatformTextStyle(emojiSupportMatch = EmojiSupportMatch.None)),
                    TextStyle(fontSize = 20.sp, letterSpacing = 1.sp, textAlign = TextAlign.Justify, platformStyle = PlatformTextStyle(emojiSupportMatch = EmojiSupportMatch.None)),
                    TextStyle(fontSize = 20.sp, fontFamily = FontFamily.Serif, lineHeight = 40.sp, textAlign = TextAlign.Center, platformStyle = PlatformTextStyle(includeFontPadding = true)),
                ).withIndex()) {
                    val layout = measurer.measure(AnnotatedString(text), style,
                        constraints = Constraints(maxWidth = width), layoutDirection = direction)
                    var calls = 0
                    val queries = text.indices.flatMap { start -> (start + 1..text.length).map { end -> start to end } }.shuffled(random)
                    for ((start, end) in queries) {
                        val expected = layout.getPathForRange(start, end).getBounds()
                        val actual = ranges.bounds(layout, start, end)
                        val label = "text=$text dir=$direction width=$width style=$styleIndex [$start,$end)"
                        assertEquals(label, expected.left, actual.left, 0f)
                        assertEquals(label, expected.right, actual.right, 0f)
                        assertEquals(label, expected.top, actual.top, 0f)
                        assertEquals(label, expected.bottom, actual.bottom, 0f)
                        checked++; calls++
                    }
                    direct += calls - ranges.pathQueries
                    if (layout.lineCount == 1) singleLineLayouts++
                    if (ranges.usesFastGeometry) fastLayouts++
                    if (text == "שלום 123 (abc)" && width == 1200 && styleIndex == 0) {
                        assertTrue("The primary bidi fixture must exercise the fast path", ranges.usesFastGeometry)
                    }
                }
        }
        android.util.Log.i("BidiBounds", "checked=$checked direct=$direct nativePaths=${checked - direct} fastLayouts=$fastLayouts singleLineLayouts=$singleLineLayouts")
        assertTrue("The shortcut must exercise real ranges", direct > 100)
        assertTrue(checked.toString(), checked > 10_000)
    }
}
