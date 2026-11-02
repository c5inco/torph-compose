package des.c5inco.torph.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import des.c5inco.torph.core.Segmentation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeferredPreparationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun initialLayoutDoesNotPrepareSegmentsAndDrawsTheMeasuredParagraph() {
        lateinit var state: TextMorphState
        rule.setContent { state = rememberTextMorphState() }
        rule.runOnIdle {
            state.deferInitialPreparation = true
            state.style = TextStyle(color = Color.Black, fontSize = 24.sp)
            state.text = "12,345 steps"
            state.measure(Constraints(maxWidth = 600), LayoutDirection.Ltr)
            assertNotNull(state.deferredLayout)
            assertEquals(0, state.preparedSegmentCount)
            assertEquals(state.segments.size, state.liveCount)
            assertTrue(state.segments.isNotEmpty())
            assertArrayEquals(raster(state, paragraph = true), raster(state))
        }
    }

    @Test fun firstUpdatePreservesInkAtTimeZero() {
        val states = ArrayList<TextMorphState>()
        val fixtures = listOf("12,345" to Segmentation.GRAPHEME, "AV" to Segmentation.GRAPHEME,
            "office ffi" to Segmentation.GRAPHEME, "مرحبا بالعالم" to Segmentation.GRAPHEME,
            "שלום 123 (abc)" to Segmentation.GRAPHEME,
            "Supercalifragilisticexpialidocious" to Segmentation.WORD)
        rule.mainClock.autoAdvance = false
        rule.setContent { repeat(fixtures.size) { states.add(rememberTextMorphState()) } }
        rule.runOnIdle {
            val failures = ArrayList<String>()
            for ((index, fixture) in fixtures.withIndex()) {
                val state = states[index]
                state.deferInitialPreparation = true
                state.style = TextStyle(color = Color.Black, fontSize = 24.sp)
                state.options = MorphOptions(segmentation = fixture.second, numbers = false)
                state.text = fixture.first
                val constraints = Constraints(maxWidth = if (fixture.second == Segmentation.WORD) 200 else 900)
                state.measure(constraints, LayoutDirection.Ltr)
                val before = raster(state)
                state.text = fixture.first + "!"
                state.measure(constraints, LayoutDirection.Ltr)
                assertNull(state.deferredLayout)
                assertTrue(state.isAnimating)
                val after = raster(state)
                val changed = before.indices.count { before[it] != after[it] }
                android.util.Log.i("DeferredPreparation", "text=${fixture.first} changedAtTimeZero=$changed")
                if (changed != 0) failures += "${fixture.first}: $changed pixels"
            }
            assertTrue("First-update renderer handoff changed ink: ${failures.joinToString()}", failures.isEmpty())
        }
    }


    @Test fun handoffSurvivesInterruptionAndConvergesToTheNativeParagraph() {
        lateinit var lazy: TextMorphState
        lateinit var eager: TextMorphState
        rule.mainClock.autoAdvance = false
        rule.setContent { lazy = rememberTextMorphState(); eager = rememberTextMorphState() }
        val constraints = Constraints(maxWidth = 900)
        rule.runOnIdle {
            lazy.deferInitialPreparation = true
            eager.deferInitialPreparation = false
            for (state in listOf(lazy, eager)) {
                state.style = TextStyle(color = Color.Black, fontSize = 24.sp)
                state.options = MorphOptions(segmentation = Segmentation.GRAPHEME, numbers = false)
                state.text = "AV office"
                state.measure(constraints, LayoutDirection.Ltr)
                state.text = "AV office!"
                state.measure(constraints, LayoutDirection.Ltr)
            }
        }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle {
            assertNotNull(lazy.handoffSource)
            val progress = lazy.handoffProgress.value
            val before = raster(lazy)
            for (state in listOf(lazy, eager)) {
                state.text = "AV office!?"
                state.measure(constraints, LayoutDirection.Ltr)
            }
            assertEquals(progress, lazy.handoffProgress.value, 0f)
            assertArrayEquals("Retarget must not restart or jump the handoff", before, raster(lazy))
        }
        rule.mainClock.advanceTimeBy(96)
        rule.runOnIdle {
            assertNull(lazy.handoffSource)
            assertTrue(lazy.live.filter { !it.exiting }.all { it.paragraphSlice != null })
        }
        rule.mainClock.advanceTimeBy(1000)
        rule.runOnIdle {
            assertFalse(lazy.isAnimating)
            assertArrayEquals(raster(lazy, paragraph = true), raster(lazy))
            assertArrayEquals(raster(lazy, paragraph = true), raster(lazy, prepared = true))
        }
    }

    @Test fun preparedGraphemeRegionsReconstructTheNativeParagraphWithoutSeams() {
        val states = ArrayList<TextMorphState>()
        val cases = typographyCases().flatMap { case ->
            Segmentation.entries.map { case.copy(id = "${case.id}-$it", mode = it) }
        }
        rule.setContent {
            for (case in cases) CompositionLocalProvider(LocalDensity provides case.density) {
                states.add(rememberTextMorphState())
            }
        }
        rule.runOnIdle {
            var checked = 0
            for ((index, case) in cases.withIndex()) {
                val state = states[index]
                state.deferInitialPreparation = true
                state.style = case.style
                state.options = MorphOptions(segmentation = case.mode)
                state.text = case.text
                state.measure(Constraints(maxWidth = 900), case.direction)
                if (!state.useParagraphFragments) continue
                assertTrue(case.id, state.live.all { it.paragraphSlice?.let { slice -> slice.clipRect != null || slice.clipIntervals != null } == true })
                assertEquals(case.id, state.segments.size, state.preparedSegmentCount)
                assertArrayEquals(case.id, raster(state, paragraph = true), raster(state, prepared = true))
                checked++
            }
            android.util.Log.i("DeferredPreparation", "nativeRegionCases=$checked")
            assertTrue("Nonvacuous partition coverage: $checked", checked >= 35)
        }
    }

    @Test fun firstStartCallbackSeesTheOriginalInkAndSnapCancelsExactlyOnce() {
        lateinit var state: TextMorphState
        val events = ArrayList<String>()
        var startImage: IntArray? = null
        lateinit var original: IntArray
        rule.mainClock.autoAdvance = false
        rule.setContent { state = rememberTextMorphState() }
        rule.runOnIdle {
            state.deferInitialPreparation = true
            state.style = TextStyle(color = Color.Black, fontSize = 24.sp)
            state.options = MorphOptions(segmentation = Segmentation.GRAPHEME, numbers = false)
            state.text = "AV"
            state.measure(Constraints(maxWidth = 900), LayoutDirection.Ltr)
            original = raster(state)
            state.onAnimationStart = { events += "start"; startImage = raster(state) }
            state.onAnimationCancel = { events += "cancel" }
            state.onAnimationComplete = { events += "complete" }
            state.text = "AV!"
            state.measure(Constraints(maxWidth = 900), LayoutDirection.Ltr)
        }
        rule.runOnIdle {
            assertEquals(listOf("start"), events)
            assertArrayEquals(original, startImage)
        }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle {
            state.options = state.options.copy(snap = true)
            state.text = "AV?"
            state.measure(Constraints(maxWidth = 900), LayoutDirection.Ltr)
        }
        rule.runOnIdle {
            assertEquals(listOf("start", "cancel", "start", "complete"), events)
            assertFalse(state.isAnimating)
            assertNull(state.handoffSource)
            assertArrayEquals(raster(state, paragraph = true), raster(state))
        }
    }

    @Test fun rollingEndpointsUseTheNativeTargetGlyphWithoutAnAlignmentJump() {
        val states = ArrayList<TextMorphState>()
        rule.mainClock.autoAdvance = false
        rule.setContent { repeat(3) { states.add(rememberTextMorphState()) } }
        rule.runOnIdle {
            for ((i, state) in states.withIndex()) {
                state.deferInitialPreparation = true
                state.style = TextStyle(color = Color.Black, fontSize = 24.sp,
                    fontFamily = if (i == 1) FontFamily.Serif else FontFamily.Default,
                    fontFeatureSettings = "'pnum' 1")
                state.options = MorphOptions(segmentation = Segmentation.GRAPHEME, clip = MorphClip.lineBox(inset = 0f))
                state.text = if (i == 2) "١٢٣" else "12,345"
                state.measure(Constraints(maxWidth = 900), LayoutDirection.Ltr)
                state.text = if (i == 2) "٢٣٤" else "23,456"
                state.measure(Constraints(maxWidth = 900), LayoutDirection.Ltr)
            }
        }
        rule.mainClock.advanceTimeBy(96)
        rule.runOnIdle {
            for (state in states) {
                assertTrue(state.useParagraphFragments)
                state.live.removeAll { it.exiting }
                assertTrue(state.live.any { it.strip != null })
                for (live in state.live) {
                    live.x.snapTo(live.cell.left)
                    live.y.snapTo(live.cell.top)
                    live.alpha.snapTo(1f)
                    live.scale.snapTo(1f)
                    live.strip?.let { live.stripProgress.snapTo(it.lastIndex.toFloat()) }
                }
                val stripTarget = raster(state, prepared = true)
                state.live.forEach { it.strip = null; it.clip = null }
                assertArrayEquals(state.text, raster(state, paragraph = true), stripTarget)
                assertArrayEquals(state.text, stripTarget, raster(state, prepared = true))
            }
        }
    }

    @Test fun boundedFallbacksPreserveTheExistingRenderer() {
        val pairs = ArrayList<Pair<TextMorphState, TextMorphState>>()
        val normal = TextStyle(color = Color.Black, fontSize = 24.sp)
        val fixtures = listOf(
            Triple("words wrapping across lines", normal, 100),
            Triple("AV", normal.copy(letterSpacing = (-1).sp), 900),
            Triple("AV", normal.copy(textGeometricTransform = TextGeometricTransform(scaleX = -1f)), 900),
            Triple("x".repeat(128), normal.copy(fontSize = 1.sp), 900),
        )
        rule.mainClock.autoAdvance = false
        rule.setContent { repeat(fixtures.size) { pairs.add(rememberTextMorphState() to rememberTextMorphState()) } }
        rule.runOnIdle {
            for ((i, pair) in pairs.withIndex()) {
                val (text, style, width) = fixtures[i]
                pair.first.deferInitialPreparation = true
                pair.second.deferInitialPreparation = false
                for (state in listOf(pair.first, pair.second)) {
                    state.style = style
                    state.options = MorphOptions(segmentation = Segmentation.GRAPHEME)
                    state.text = text
                    state.measure(Constraints(maxWidth = width), LayoutDirection.Ltr)
                }
                assertNull(pair.first.deferredLayout)
                assertFalse(pair.first.useParagraphFragments)
                assertEquals(pair.first.segments.size, pair.first.preparedSegmentCount)
                assertArrayEquals(text, raster(pair.second), raster(pair.first))
                for (state in listOf(pair.first, pair.second)) {
                    state.text += "!"
                    state.measure(Constraints(maxWidth = width), LayoutDirection.Ltr)
                }
                assertArrayEquals(text, raster(pair.second), raster(pair.first))
            }
        }
        rule.mainClock.advanceTimeBy(96)
        rule.runOnIdle { for ((a, b) in pairs) assertArrayEquals(raster(b), raster(a)) }
    }

    @Test fun handoffDoesNotCropOpaqueInkWithTightLineHeight() {
        lateinit var state: TextMorphState
        lateinit var before: IntArray
        var oldWidth = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { state = rememberTextMorphState() }
        rule.runOnIdle {
            state.deferInitialPreparation = true
            state.style = TextStyle(color = Color.Black, fontSize = 24.sp, lineHeight = 1.sp, fontFamily = FontFamily.Monospace)
            state.options = MorphOptions(segmentation = Segmentation.GRAPHEME, numbers = false)
            state.text = "1"
            state.measure(Constraints(maxWidth = 900), LayoutDirection.Ltr)
            oldWidth = state.layoutResult!!.size.width
            before = raster(state)
            state.text = "12"
            state.measure(Constraints(maxWidth = 900), LayoutDirection.Ltr)
        }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle {
            assertNotNull(state.handoffSource)
            val after = raster(state)
            var checked = 0
            for (y in 0 until 800) for (x in 100 until 100 + oldWidth) {
                val i = y * 1400 + x
                if (before[i] == android.graphics.Color.BLACK) {
                    assertEquals("Stationary glyph ink at $x,$y", before[i], after[i])
                    checked++
                }
            }
            assertTrue("Must inspect real opaque glyph ink", checked > 20)
        }
    }

    private fun raster(state: TextMorphState, paragraph: Boolean = false, prepared: Boolean = false): IntArray {
        val image = ImageBitmap(1400, 800)
        CanvasDrawScope().draw(state.layoutResult!!.layoutInput.density, LayoutDirection.Ltr,
            Canvas(image), Size(1400f, 800f)) {
            translate(100f, 100f) {
                if (paragraph) drawText(state.layoutResult!!) else if (prepared) state.drawLiveSegments(this) else state.draw(this)
            }
        }
        return IntArray(1400 * 800).also { image.asAndroidBitmap().getPixels(it, 0, 1400, 0, 0, 1400, 800) }
    }
}
