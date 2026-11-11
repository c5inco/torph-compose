package des.c5inco.torph.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import des.c5inco.torph.core.SegmentKind
import des.c5inco.torph.core.Segmentation
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.text.Bidi
import java.util.Locale
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

class DesktopTextMorphTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun desktopReducedMotionReadsGtkAndNetAnimationProperties() {
        assertTrue(
            isDesktopReducedMotion(
                isHeadless = false,
                desktopProperty = { key -> if (key == "gnome.Gtk/EnableAnimations") 0 else null },
            ),
        )
        assertTrue(
            isDesktopReducedMotion(
                isHeadless = false,
                desktopProperty = { key -> if (key == "gnome.Net/EnableAnimations") false else null },
            ),
        )
        assertEquals(
            false,
            isDesktopReducedMotion(
                isHeadless = false,
                desktopProperty = { key -> if (key == "gnome.Gtk/EnableAnimations") 1 else null },
            ),
        )
    }

    @Test
    fun icuSegmenterHandlesGraphemeClustersAndComplexScripts() {
        val segmenter = IcuSegmenter()
        assertEquals(
            listOf(0..0, 1..1, 2..2, 3..4, 5..5, 6..12),
            segmenter.graphemes("Cafe\u0301 👩🏽‍💻", Locale.US),
        )
        assertEquals(
            listOf(0..4, 5..5, 6..12),
            segmenter.words("Cafe\u0301 👩🏽‍💻", Locale.US),
        )
        val thaiWords = segmenter.words("ภาษาไทย", Locale.forLanguageTag("th"))
        assertTrue("ICU dictionary word breaker should split Thai compound: $thaiWords", thaiWords.size >= 2)
    }

    @Test
    fun persistingSegmentsKeepIdsAndAnimateToNewPositions() {
        var text by mutableStateOf("Hello world")
        lateinit var state: TextMorphState
        val events = ArrayList<String>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            state = rememberTextMorphState()
            TextMorph(
                text = text,
                state = state,
                style = TextStyle(fontSize = 24.sp),
                duration = 400.milliseconds,
                respectReducedMotion = false,
                onAnimationStart = { events += "start" },
                onAnimationComplete = { events += "complete" },
                onAnimationCancel = { events += "cancel" },
            )
        }
        rule.mainClock.advanceTimeByFrame()
        val before = state.segments
        assertEquals("Hello world", before.joinToString("") { it.text })
        val idsHello = before.take(2).map { it.id }

        text = "Hello there"
        repeat(2) { rule.mainClock.advanceTimeByFrame() }
        val after = state.segments
        assertEquals(idsHello, after.take(2).map { it.id })
        assertTrue(state.liveCount > after.size)
        assertEquals(listOf("start"), events)

        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(after.size, state.liveCount)
        assertEquals(listOf("start", "complete"), events)
    }

    @Test
    fun digitRetargetedMidRollKeepsAVisibleDigitInItsStrip() {
        var hundredths by mutableStateOf(0)
        lateinit var state: TextMorphState
        rule.mainClock.autoAdvance = false
        rule.setContent {
            state = rememberTextMorphState()
            TextMorph(
                text = "00.%02d".format(hundredths % 100),
                state = state,
                style = TextStyle(fontSize = 24.sp),
                ease = MorphEase.Spring(stiffness = 220f, damping = 22f),
                respectReducedMotion = false,
            )
        }
        rule.mainClock.advanceTimeByFrame()
        repeat(20) {
            hundredths += 8
            repeat(5) {
                rule.mainClock.advanceTimeByFrame()
                for (ls in state.live) {
                    val strip = ls.strip ?: continue
                    val p = ls.stripProgress.value
                    assertTrue("progress $p outside strip of ${strip.size}", p > -1f && p < strip.size)
                }
            }
        }
    }

    @Test
    fun placedCellsMatchParagraphGeometryAcrossWrapsAndScriptsOnDesktop() {
        lateinit var state: TextMorphState
        var target by mutableStateOf("ASCII words that wrap with numbers 12345 and punctuation!\nnext line")
        var targetStyle by mutableStateOf(TextStyle(fontSize = 18.sp))
        rule.setContent {
            state = rememberTextMorphState()
            TextMorph(
                text = target,
                state = state,
                modifier = Modifier.width(120.dp),
                style = targetStyle,
                disabled = true,
            )
        }
        for (style in listOf(
            TextStyle(fontSize = 18.sp),
            TextStyle(fontSize = 18.sp, letterSpacing = (-1).sp),
            TextStyle(fontSize = 18.sp, textGeometricTransform = TextGeometricTransform(scaleX = -1f)),
        )) {
            for (text in listOf(
                "ASCII words that wrap with numbers 12345 and punctuation!\nnext line",
                "ASCII words that wrap with numbers 12345 and punctuation!\nnext line edited",
                "مرحبا بالعالم mixed English 123 שלום 👩‍👩‍👧",
                "Back to ASCII 9876 with a verylongwordthatwrapsovermorethanoneline",
            )) {
                rule.runOnIdle { target = text; targetStyle = style }
                rule.waitForIdle()
                rule.runOnIdle {
                    val layout = state.layoutResult!!
                    val boxes = FloatArray(text.length * 4)
                    fillCharacterBoundingBoxes(layout, TextRange(0, text.length), boxes, 0)
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
                        if ((style.textGeometricTransform?.scaleX ?: 1f) > 0f &&
                            Bidi.requiresBidi(text.toCharArray(), 0, text.length)
                        ) {
                            val selection = layout.getPathForRange(segment.index, segment.end).getBounds()
                            left = selection.left
                            right = selection.right
                            assertTrue(right >= left)
                        }
                        val line = layout.getLineForOffset(segment.index)
                        assertEquals(Rect(left, layout.getLineTop(line), right, layout.getLineBottom(line)), live.cell)
                        assertTrue("placed segment should have positive width for '${segment.text}'", live.cell.width >= 0f)
                    }
                }
            }
        }
    }

    @Test
    fun bidiRangeBoundsMatchesNativeSelectionOnDesktop() {
        lateinit var measurer: TextMeasurer
        rule.setContent { measurer = rememberTextMeasurer(cacheSize = 0) }
        val ranges = BidiRangeBounds()
        val texts = listOf(
            "שלום 123 (abc)",
            "abc \u2067שלום ١٢٣\u2069 xyz",
            "abc (שלום) xyz",
            "אב 👩🏽‍💻 e\u0301 xyz",
            "אב 123\nabc שלום\n",
        )
        rule.runOnIdle {
            var checked = 0
            for (text in texts) {
                for (direction in LayoutDirection.entries) {
                    val layout = measurer.measure(
                        AnnotatedString(text),
                        TextStyle(fontSize = 20.sp),
                        constraints = Constraints(maxWidth = 600),
                        layoutDirection = direction,
                    )
                    for (start in text.indices) {
                        for (end in start + 1..text.length) {
                            val expected = layout.getPathForRange(start, end).getBounds()
                            val actual = ranges.bounds(layout, start, end)
                            val label = "text=$text dir=$direction [$start,$end)"
                            assertEquals(label, expected.left, actual.left, 0f)
                            assertEquals(label, expected.right, actual.right, 0f)
                            assertEquals(label, expected.top, actual.top, 0f)
                            assertEquals(label, expected.bottom, actual.bottom, 0f)
                            checked++
                        }
                    }
                }
            }
            assertTrue("Expected comprehensive bidi range checks", checked > 500)
        }
    }

    @Test
    fun sharedMeasurerPoolsMatchingEnvironmentsAndMatchesIsolatedPixels() {
        val states = ArrayList<TextMorphState>()
        val otherResolver = createFontFamilyResolver()
        lateinit var shared: TextMeasurer
        lateinit var isolated: TextMeasurer
        lateinit var scope: CoroutineScope
        rule.setContent {
            val base = LocalDensity.current
            shared = rememberSharedTextMeasurer()
            isolated = rememberTextMeasurer(cacheSize = 0)
            scope = rememberCoroutineScope()
            Column {
                states.clear()
                states.add(rememberTextMorphState())
                states.add(rememberTextMorphState())
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale + 0.5f)) {
                    states.add(rememberTextMorphState())
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    states.add(rememberTextMorphState())
                }
                CompositionLocalProvider(LocalFontFamilyResolver provides otherResolver) {
                    states.add(rememberTextMorphState())
                }
            }
        }
        rule.runOnIdle {
            assertSame(states[0].textMeasurer, states[1].textMeasurer)
            for (state in states.drop(2)) assertNotSame(states[0].textMeasurer, state.textMeasurer)

            fun render(measurer: TextMeasurer, text: String, style: TextStyle, mode: Segmentation): IntArray {
                val state = TextMorphState(measurer, scope, IcuSegmenter())
                state.text = text
                state.style = style
                state.options = MorphOptions(segmentation = mode)
                state.measure(Constraints(maxWidth = 240), LayoutDirection.Ltr)
                val full = state.layoutResult!!
                val w = full.size.width + 40
                val h = full.size.height + 40
                val bitmap = ImageBitmap(w, h)
                CanvasDrawScope().draw(
                    full.layoutInput.density,
                    LayoutDirection.Ltr,
                    Canvas(bitmap),
                    Size(w.toFloat(), h.toFloat()),
                ) { state.draw(this) }
                val pixelMap = bitmap.toPixelMap()
                return IntArray(w * h) { idx -> pixelMap[idx % w, idx / w].hashCode() }
            }

            for (mode in listOf(Segmentation.AUTO, Segmentation.GRAPHEME)) {
                for (text in listOf("12,345", "AVATAR office ffi", "مرحبا بالعالم", "नमस्ते दुनिया", "ภาษาไทย", "e\u0301 👩‍👩‍👧‍👦", "words wrapping here\nnext line")) {
                    val style = TextStyle(fontSize = 24.sp, color = Color.Black)
                    render(shared, text, style, mode)
                    assertArrayEquals(text, render(isolated, text, style, mode), render(shared, text, style, mode))
                }
            }
        }
    }

    @Test
    fun morphClipBoundsRollingDigitInkOnDesktop() {
        fun inkOverflow(clip: MorphClip): Float {
            var text by mutableStateOf("1")
            rule.mainClock.autoAdvance = false
            rule.setContent {
                Box(Modifier.testTag("stage").background(Color.White).padding(48.dp)) {
                    TextMorph(
                        text = text,
                        modifier = Modifier.testTag("text"),
                        style = TextStyle(fontSize = 48.sp, color = Color.Black),
                        ease = MorphEase.Curve(MorphEasings.Linear),
                        duration = 600.milliseconds,
                        clip = clip,
                        respectReducedMotion = false,
                    )
                }
            }
            rule.mainClock.advanceTimeBy(1000)
            val stage = rule.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
            val box = rule.onNodeWithTag("text").fetchSemanticsNode().boundsInRoot
            val top = box.top - stage.top
            val bottom = box.bottom - stage.top
            val lineHeight = box.height

            text = "8"
            var inkTop = Int.MAX_VALUE
            var inkBottom = -1
            repeat(40) {
                rule.mainClock.advanceTimeByFrame()
                val pixels = rule.onNodeWithTag("stage").captureToImage().toPixelMap()
                for (y in 0 until pixels.height) {
                    for (x in 0 until pixels.width) {
                        if (pixels[x, y].red < 0.97f) {
                            inkTop = minOf(inkTop, y)
                            inkBottom = maxOf(inkBottom, y)
                            break
                        }
                    }
                }
            }
            return max(top - inkTop, inkBottom + 1 - bottom) / lineHeight
        }

        val lineBoxOverflow = inkOverflow(MorphClip.lineBox())
        assertTrue("lineBox should keep rolling digits within the line box: $lineBoxOverflow", lineBoxOverflow <= 0.03f)
    }
}
