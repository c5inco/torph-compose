package des.c5inco.torph.compose

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import java.io.File

/** Inputs are data so a failed seeded sequence can print its complete replay history. */
internal data class MorphScene(
    val text: String = "Hello world",
    val style: TextStyle = TextStyle(color = Color.Black, fontSize = 22.sp),
    val options: MorphOptions = MorphOptions(),
    val maxWidth: Int = 260,
)

internal data class MorphEnvironment(
    val density: Density = Density(1f),
    val direction: LayoutDirection = LayoutDirection.Ltr,
    val resolver: FontFamily.Resolver? = null,
)

/** Replace either factory to exercise future implementations against the same scenes and clock. */
internal fun interface MorphSubject {
    @Composable fun rememberState(): TextMorphState
}

internal val sharedSubject = MorphSubject { rememberTextMorphState() }
internal val uncachedSubject = MorphSubject {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    rememberTextMorphState(measurer)
}

/**
 * Both real composables run on one frame clock. Software capture isolates text rendering;
 * node capture also exercises the hardware canvas, layers and masks. Neither is a typography
 * oracle: structural/callback invariants must be checked separately from differential parity.
 */
internal class MorphParityHarness(
    val rule: ComposeContentTestRule,
    private val candidate: MorphSubject = sharedSubject,
    private val reference: MorphSubject = uncachedSubject,
    private val respectReducedMotion: Boolean = false,
) {
    var scene by mutableStateOf(MorphScene())
        private set
    var environment by mutableStateOf(MorphEnvironment())
        private set
    var visible by mutableStateOf(true)
        private set
    lateinit var actual: TextMorphState
        private set
    lateinit var expected: TextMorphState
        private set
    val actualEvents = ArrayList<String>()
    val expectedEvents = ArrayList<String>()
    private val history = ArrayList<String>()
    private var hardwareAccelerated = false

    fun mount(initial: MorphScene = MorphScene(), env: MorphEnvironment = MorphEnvironment()) {
        scene = initial
        environment = env
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            hardwareAccelerated = view.isHardwareAccelerated
            // Integer physical coordinates and equal pane dimensions avoid pixel phase differences.
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Column {
                    if (visible) {
                        Pane("candidate", candidate, actualEvents) { actual = it }
                        Pane("reference", reference, expectedEvents) { expected = it }
                    }
                }
            }
        }
        frames()
        rule.runOnIdle {
            assertNotSame(actual, expected)
            assertNotSame(actual.textMeasurer, expected.textMeasurer)
            assertNotSame(actual.layoutResult!!.multiParagraph, expected.layoutResult!!.multiParagraph)
        }
        history += "mount $initial $env"
    }

    @Composable private fun Pane(tag: String, subject: MorphSubject, events: MutableList<String>, export: (TextMorphState) -> Unit) {
        Box(Modifier.requiredSize(388.dp, 240.dp).background(Color.White).testTag(tag).padding(64.dp)) {
            CompositionLocalProvider(
                LocalDensity provides environment.density,
                LocalLayoutDirection provides environment.direction,
                LocalFontFamilyResolver provides (environment.resolver ?: LocalFontFamilyResolver.current),
            ) {
                val state = subject.rememberState()
                export(state)
                val o = scene.options
                TextMorph(
                    scene.text, state = state, style = scene.style,
                    modifier = Modifier.widthIn(max = (scene.maxWidth / environment.density.density).dp),
                    ease = o.ease, duration = o.duration, scale = o.scale, numbers = o.numbers,
                    locale = Locale(o.locale.toLanguageTag()), cursorIndex = o.cursorIndex,
                    segmentation = o.segmentation, sizeMode = o.sizeMode, clip = o.clip,
                    disabled = o.snap, respectReducedMotion = respectReducedMotion,
                    onAnimationStart = { events += "start" },
                    onAnimationComplete = { events += "complete" },
                    onAnimationCancel = { events += "cancel" },
                )
            }
        }
    }

    fun update(next: MorphScene, env: MorphEnvironment = environment) {
        history += "update $next $env"
        rule.runOnIdle { scene = next; environment = env }
        frames()
    }

    fun show(value: Boolean) {
        history += "visible=$value"
        rule.runOnIdle { visible = value }
        frames()
    }

    fun frames(count: Int = 2) {
        repeat(count) { rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
    }

    fun advance(ms: Long) {
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    fun check(label: String, settled: Boolean = false, hardware: Boolean = false) {
        val context = "$label\n${history.joinToString("\n")}"
        rule.runOnIdle {
            assertEquals(context, expectedEvents, actualEvents)
            for (state in listOf(actual, expected)) {
                assertEquals(context, scene.text, state.segments.joinToString("") { it.text })
                assertEquals(context, state.segments.size, state.segments.map { it.id }.distinct().size)
                assertEquals(context, state.live.size, state.live.map { it.id }.distinct().size)
                for (live in state.live) {
                    for (v in listOf(live.x.value, live.y.value, live.alpha.value, live.scale.value,
                        live.cell.left, live.cell.top, live.cell.right, live.cell.bottom)) {
                        assertTrue("Non-finite channel/bounds: $context", v.isFinite())
                    }
                    // Compose itself can return signed horizontal extents at BiDi run boundaries.
                    // Do not assume their sign; typography correctness needs a separate oracle.
                    assertTrue(context, live.cell.height >= 0f)
                    live.strip?.let { assertTrue("Blank digit strip: $context", live.stripProgress.value > -1f && live.stripProgress.value < it.size) }
                }
                if (settled) {
                    assertFalse(context, state.isAnimating)
                    assertEquals(context, state.segments.size, state.liveCount)
                    assertFalse(context, state.live.any { it.exiting || it.strip != null })
                }
            }
            assertEquals(context, expected.layoutResult!!.size, actual.layoutResult!!.size)
            val expectedImage = raster(expected)
            val actualImage = raster(actual)
            assertPixelsEqual(expectedImage, actualImage, label, context)
            if (settled && scene.text.isNotBlank()) assertHasInk(actualImage, context)
        }
        if (hardware) {
            assertTrue("Hardware rendering must be enabled", hardwareAccelerated)
            val referenceImage = rule.onNodeWithTag("reference").captureToImage().asAndroidBitmap()
            val candidateImage = rule.onNodeWithTag("candidate").captureToImage().asAndroidBitmap()
            assertPixelsEqual(referenceImage, candidateImage, "$label-hardware", context)
            if (scene.text.isNotBlank()) assertHasInk(candidateImage, context)
        }
    }

    private fun raster(state: TextMorphState): Bitmap {
        val image = ImageBitmap(640, 512)
        CanvasDrawScope().draw(environment.density, environment.direction, Canvas(image), Size(640f, 512f)) {
            drawRect(Color.White)
            translate(64f, 64f) { state.draw(this) }
        }
        return image.asAndroidBitmap()
    }
}

/** PNGs and replay text are written only on failure, into Gradle's pulled test artifacts. */
internal fun assertPixelsEqual(expected: Bitmap, actual: Bitmap, label: String, context: String) {
    val w = expected.width
    val h = expected.height
    assertEquals(context, w, actual.width)
    assertEquals(context, h, actual.height)
    val a = IntArray(w * h).also { expected.getPixels(it, 0, w, 0, 0, w, h) }
    val b = IntArray(w * h).also { actual.getPixels(it, 0, w, 0, 0, w, h) }
    var differences = 0
    val diff = IntArray(a.size) { if (a[it] == b[it]) android.graphics.Color.WHITE else { differences++; android.graphics.Color.MAGENTA } }
    if (differences == 0) return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val root = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        ?.let(::File) ?: File(instrumentation.targetContext.getExternalFilesDir(null), "morph-parity")
    val directory = File(root, label.replace(Regex("[^A-Za-z0-9_.-]"), "_")).apply { mkdirs() }
    fun save(name: String, bitmap: Bitmap) { File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    save("expected.png", expected)
    save("actual.png", actual)
    save("diff.png", Bitmap.createBitmap(diff, w, h, Bitmap.Config.ARGB_8888))
    File(directory, "replay.txt").writeText(context)
    fail("$differences differing pixels; artifacts: $directory\n$context")
}

private fun assertHasInk(bitmap: Bitmap, context: String) {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    assertTrue("Both subjects must not pass by drawing blank frames: $context", pixels.any { it != android.graphics.Color.WHITE })
}
