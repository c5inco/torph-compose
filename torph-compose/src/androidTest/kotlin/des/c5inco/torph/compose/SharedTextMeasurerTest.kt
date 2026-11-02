package des.c5inco.torph.compose

import android.graphics.Typeface
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import des.c5inco.torph.core.Segmentation
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedTextMeasurerTest {
    @get:Rule val rule = createComposeRule()

    @Test fun defaultStatesShareOnlyMatchingEnvironments() {
        val states = ArrayList<TextMorphState>()
        val otherResolver = createFontFamilyResolver(InstrumentationRegistry.getInstrumentation().targetContext)
        rule.setContent {
            val base = LocalDensity.current
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
                // Identity matters even when a resolver delegates to the same implementation.
                CompositionLocalProvider(LocalFontFamilyResolver provides otherResolver) {
                    states.add(rememberTextMorphState())
                }
            }
        }
        rule.runOnIdle {
            assertSame(states[0].textMeasurer, states[1].textMeasurer)
            for (state in states.drop(2)) assertNotSame(states[0].textMeasurer, state.textMeasurer)
            for (state in states) {
                state.text = "12,345"
                state.style = TextStyle(fontSize = 24.sp)
                state.measure(Constraints(maxWidth = 300), LayoutDirection.Ltr)
            }
            assertNotSame(states[0], states[1])
            assertNotSame(states[0].live.first(), states[1].live.first())
            assertSame(states[0].live.first().layout.multiParagraph, states[1].live.first().layout.multiParagraph)
        }
    }

    @Test fun poolSeparatesOwnersLocalesAndDensityAndEvictsEnvironments() {
        lateinit var resolver: FontFamily.Resolver
        rule.setContent { resolver = LocalFontFamilyResolver.current }
        rule.runOnIdle {
            val pool = TextMeasurerPool(capacity = 2)
            val owner = Any()
            val density = Density(1f, 1f)
            fun get(ownerKey: Any = owner, d: Density = density, locales: LocaleList = LocaleList("en-US")) =
                pool.get(ownerKey, resolver, d, LayoutDirection.Ltr, locales)
            val first = get()
            assertSame(first, get())
            val french = get(locales = LocaleList("fr-FR"))
            assertNotSame(first, french)
            val scaled = get(d = Density(2f, 1f))
            assertNotSame(first, scaled)
            assertNotSame(first, get()) // en-US was evicted, even while first is still strongly held.
            assertNotSame(get(), get(ownerKey = Any()))
        }
    }

    @Test fun layoutCacheIsBoundedAndKeysStyleWrappingAndDirection() {
        lateinit var resolver: FontFamily.Resolver
        rule.setContent { resolver = LocalFontFamilyResolver.current }
        rule.runOnIdle {
            val measurer = TextMeasurerPool().get(Any(), resolver, Density(1f), LayoutDirection.Ltr, LocaleList("en-US"))
            val style = TextStyle(fontSize = 18.sp)
            fun measure(text: String, s: TextStyle = style, width: Int = 400, dir: LayoutDirection = LayoutDirection.Ltr) =
                measurer.measure(AnnotatedString(text), s, constraints = Constraints(maxWidth = width), layoutDirection = dir)
            val first = measure("office affinity AVATAR مرحبا")
            assertSame(first.multiParagraph, measure(first.layoutInput.text.text).multiParagraph)
            assertNotSame(first.multiParagraph, measure(first.layoutInput.text.text, style.copy(fontSize = 20.sp)).multiParagraph)
            assertNotSame(first.multiParagraph, measure(first.layoutInput.text.text, width = 80).multiParagraph)
            assertNotSame(first.multiParagraph, measure(first.layoutInput.text.text, dir = LayoutDirection.Rtl).multiParagraph)
            repeat(SHARED_LAYOUT_CAPACITY + 1) { measure("unique-$it") }
            assertNotSame(first.multiParagraph, measure(first.layoutInput.text.text).multiParagraph)
        }
    }

    @Test fun resolvedFontUpdatesRefreshFullAndLocalSegmentCachesWithoutMorphing() = verifyAsyncFontUpdate(false)

    @Test fun resolvedFontUpdatesRefreshPersistingSegmentsDuringTextChange() = verifyAsyncFontUpdate(true)

    @Test fun fontCompletionDoesNotCancelAnInFlightMorph() {
        rule.mainClock.autoAdvance = false
        val loaded = CompletableDeferred<Typeface>()
        val font = deferredTestFont(loaded)
        val resolver = createFontFamilyResolver(InstrumentationRegistry.getInstrumentation().targetContext)
        var text by mutableStateOf("WiWi")
        lateinit var state: TextMorphState
        var cancels = 0
        var completes = 0
        rule.setContent {
            CompositionLocalProvider(LocalFontFamilyResolver provides resolver) {
                state = rememberTextMorphState()
                TextMorph(text, state = state, style = TextStyle(fontSize = 24.sp, fontFamily = FontFamily(font)),
                    respectReducedMotion = false, onAnimationCancel = { cancels++ }, onAnimationComplete = { completes++ })
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.runOnIdle { text = "WiWii" }
        repeat(2) { rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
        lateinit var old: androidx.compose.ui.text.TextLayoutResult
        rule.runOnIdle { assertTrue(state.isAnimating); old = state.layoutResult!! }
        loaded.complete(Typeface.MONOSPACE)
        rule.waitUntil(5_000) { old.multiParagraph.intrinsics.hasStaleResolvedFonts }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(state.isAnimating); assertSame(old, state.layoutResult) }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        // No manual measure: font refresh must be scheduled by the layout observation itself.
        rule.waitUntil(5_000) { !state.isAnimating && !state.layoutResult!!.multiParagraph.intrinsics.hasStaleResolvedFonts }
        rule.runOnIdle {
            assertNotSame(old.multiParagraph, state.layoutResult!!.multiParagraph)
            assertEquals(0, cancels)
            assertEquals(1, completes)
        }
    }

    private fun verifyAsyncFontUpdate(changeText: Boolean) {
        var cancels = 0
        lateinit var measurer: TextMeasurer
        lateinit var scope: CoroutineScope
        val loaded = CompletableDeferred<Typeface>()
        val font = deferredTestFont(loaded)
        val resolver = createFontFamilyResolver(InstrumentationRegistry.getInstrumentation().targetContext)
        rule.setContent {
            CompositionLocalProvider(LocalFontFamilyResolver provides resolver) {
                measurer = rememberSharedTextMeasurer()
                scope = rememberCoroutineScope()
            }
        }
        lateinit var state: TextMorphState
        lateinit var oldFull: androidx.compose.ui.text.TextLayoutResult
        lateinit var oldSegment: androidx.compose.ui.text.TextLayoutResult
        rule.runOnIdle {
            state = TextMorphState(measurer, scope, IcuSegmenter())
            state.onAnimationCancel = { cancels++ }
            state.text = "WiWi"
            state.style = TextStyle(fontSize = 24.sp, fontFamily = FontFamily(font))
            state.measure(Constraints(maxWidth = 500), LayoutDirection.Ltr)
            oldFull = state.layoutResult!!
            oldSegment = state.live.first().layout
        }
        loaded.complete(Typeface.MONOSPACE)
        rule.waitUntil(5_000) { oldFull.multiParagraph.intrinsics.hasStaleResolvedFonts }
        rule.runOnIdle {
            assertEquals(0, cancels)
            assertTrue(oldSegment.multiParagraph.intrinsics.hasStaleResolvedFonts)
            if (changeText) state.text = "WiWii"
            state.measure(Constraints(maxWidth = 500), LayoutDirection.Ltr)
            assertNotSame(oldFull.multiParagraph, state.layoutResult!!.multiParagraph)
            assertEquals(0, reusableBoxPrefix(oldFull, state.layoutResult!!))
            assertNotSame(oldSegment.multiParagraph, state.live.first().layout.multiParagraph)
            assertEquals(changeText, state.isAnimating)
            assertFalse(state.layoutResult!!.multiParagraph.intrinsics.hasStaleResolvedFonts)
            val second = TextMorphState(measurer, scope, IcuSegmenter())
            second.text = state.text; second.style = state.style
            second.measure(Constraints(maxWidth = 500), LayoutDirection.Ltr)
            assertSame(state.layoutResult!!.multiParagraph, second.layoutResult!!.multiParagraph)
        }
    }

    @Test fun sharedAndIsolatedCachesRenderIdenticalPixelsAcrossScriptsAndStyles() {
        lateinit var shared: TextMeasurer
        lateinit var isolated: TextMeasurer
        lateinit var scope: CoroutineScope
        rule.setContent {
            shared = rememberSharedTextMeasurer()
            isolated = rememberTextMeasurer(cacheSize = 0)
            scope = rememberCoroutineScope()
        }
        rule.runOnIdle {
            fun render(measurer: TextMeasurer, text: String, style: TextStyle, mode: Segmentation): IntArray {
                val state = TextMorphState(measurer, scope, IcuSegmenter())
                state.text = text; state.style = style
                state.options = MorphOptions(segmentation = mode)
                state.measure(Constraints(maxWidth = 240), LayoutDirection.Ltr)
                val full = state.layoutResult!!
                val w = full.size.width + 40; val h = full.size.height + 40
                val bitmap = ImageBitmap(w, h)
                CanvasDrawScope().draw(full.layoutInput.density, LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) { state.draw(this) }
                return IntArray(w * h).also { bitmap.asAndroidBitmap().getPixels(it, 0, w, 0, 0, w, h) }
            }
            for (mode in listOf(Segmentation.AUTO, Segmentation.GRAPHEME)) for (text in listOf("12,345", "AVATAR To Wa office ffi", "مرحبا بالعالم", "नमस्ते दुनिया", "ภาษาไทย", "e\u0301 👩‍👩‍👧‍👦", "words wrapping here\nnext line")) {
                for (style in listOf(
                    TextStyle(fontSize = 24.sp, color = Color.Black, fontFeatureSettings = "liga"),
                    TextStyle(fontSize = 24.sp, color = Color.Red, fontFeatureSettings = "liga"),
                    TextStyle(fontSize = 24.sp, color = Color.Blue, fontFeatureSettings = "'liga' 0"),
                )) {
                    render(shared, text, style, mode) // prime the shared cache, including draw-only attributes
                    assertArrayEquals(text, render(isolated, text, style, mode), render(shared, text, style, mode))
                }
            }
        }
    }
}
