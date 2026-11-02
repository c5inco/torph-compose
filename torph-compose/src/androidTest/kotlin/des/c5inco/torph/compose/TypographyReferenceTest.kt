package des.c5inco.torph.compose

import android.app.KeyguardManager
import android.os.PowerManager
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.key
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.EmojiSupportMatch
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import des.c5inco.torph.core.Segmentation
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit policies: characterization is not a passing typography-equality assertion. */
internal enum class TypographyPolicy { Exact, Characterize }
internal data class TypographyCase(
    val id: String,
    val text: String,
    val mode: Segmentation = Segmentation.AUTO,
    val width: Int = 300,
    val direction: LayoutDirection = LayoutDirection.Ltr,
    val density: Density = Density(1f),
    val style: TextStyle = TextStyle(color = Color.Black, fontSize = 24.sp,
        // Test platform shaping deterministically; async-font behavior has its own suite.
        platformStyle = PlatformTextStyle(emojiSupportMatch = EmojiSupportMatch.None)),
    val policy: TypographyPolicy = TypographyPolicy.Exact,
    val reason: String = "Require exact independent reference parity for this input",
)

/** Reusable independent reference. No morph geometry or segment layout feeds BasicText. */
internal class TypographyReferenceHarness(private val rule: androidx.compose.ui.test.junit4.ComposeContentTestRule, reportName: String = "corpus", private val freshInitial: Boolean = false, private val subject: MorphSubject = sharedSubject) {
    private var scene by mutableStateOf(TypographyCase("initial", "12345", policy = TypographyPolicy.Exact))
    lateinit var state: TextMorphState
        private set
    lateinit var plain: TextLayoutResult
        private set
    private var animate by mutableStateOf(false)
    private var morphSize = IntSize.Zero
    private var plainSize = IntSize.Zero
    private var accelerated = false
    private val results = JSONArray()
    val output: File = File(typographyOutput(), reportName).apply { mkdirs() }

    fun mount() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val view = LocalView.current
            accelerated = view.isHardwareAccelerated
            DisposableEffect(view) {
                val old = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = old }
            }
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Column {
                    Pane("typography-morph") {
                        key(if (freshInitial) scene.id else "retained") {
                        state = subject.rememberState()
                        TextMorph(scene.text, state = state, style = scene.style,
                            segmentation = scene.mode, disabled = !animate, respectReducedMotion = false,
                            modifier = Modifier.onSizeChanged { morphSize = it })
                        }
                    }
                    Pane("typography-plain") {
                        BasicText(scene.text, style = scene.style, softWrap = true, maxLines = Int.MAX_VALUE,
                            onTextLayout = { plain = it }, modifier = Modifier.onSizeChanged { plainSize = it })
                    }
                }
            }
        }
        frames()
    }

    @Composable private fun Pane(tag: String, content: @Composable () -> Unit) {
        // Absolute physical origins, including RTL. Padding is outside the measured text constraints.
        Layout(modifier = Modifier.requiredSize(1024.dp, 640.dp).background(Color.White).testTag(tag), content = {
            CompositionLocalProvider(LocalDensity provides scene.density, LocalLayoutDirection provides scene.direction) { content() }
        }) { children, _ ->
            val child = children.single().measure(Constraints(maxWidth = scene.width))
            layout(1024, 640) { child.place(256, 256) }
        }
    }

    fun compare(case: TypographyCase): JSONObject {
        rule.runOnIdle { animate = false; scene = case }
        frames()
        lateinit var softwarePlain: Bitmap
        lateinit var softwareMorph: Bitmap
        lateinit var metadata: JSONObject
        rule.runOnIdle {
            assertFalse(case.id, state.isAnimating)
            val morph = state.layoutResult!!
            // Shape the complete string through separate public UI/layout paths.
            assertNotSame(case.id, morph.multiParagraph, plain.multiParagraph)
            assertEquals(case.id, plain.layoutInput.text, morph.layoutInput.text)
            assertEquals(case.id, plain.layoutInput.style, morph.layoutInput.style)
            assertEquals(case.id, plain.layoutInput.density.density, morph.layoutInput.density.density, 0f)
            assertEquals(case.id, plain.layoutInput.density.fontScale, morph.layoutInput.density.fontScale, 0f)
            assertEquals(case.id, plain.layoutInput.layoutDirection, morph.layoutInput.layoutDirection)
            assertSame(case.id, plain.layoutInput.fontFamilyResolver, morph.layoutInput.fontFamilyResolver)
            assertEquals(case.id, plain.layoutInput.constraints, morph.layoutInput.constraints)
            assertEquals(case.id, plain.layoutInput.softWrap, morph.layoutInput.softWrap)
            assertEquals(case.id, plain.layoutInput.maxLines, morph.layoutInput.maxLines)
            assertEquals(case.id, plain.layoutInput.overflow, morph.layoutInput.overflow)
            assertEquals(case.id, plainSize, morphSize)
            assertEquals(case.id, plain.size, morph.size)
            assertEquals(case.id, lineMetadata(plain).toString(), lineMetadata(morph).toString())
            assertEquals(case.id, plain.firstBaseline, morph.firstBaseline, 0f)
            assertEquals(case.id, plain.lastBaseline, morph.lastBaseline, 0f)
            assertTrue("${case.id}: reference exceeds capture height", plain.size.height + 256 < 639)
            val diagnosticLive = if (freshInitial) emptyList() else state.live
            for (live in diagnosticLive.filter { it.segment.text.isNotBlank() }) {
                assertTrue("${case.id}: segment exceeds capture area; enlarge the fixture",
                    live.x.value + 256 > 0 && live.x.value + live.layout.size.width + 256 < 1023 &&
                        live.y.value + 256 > 0 && live.y.value + live.layout.size.height + 256 < 639)
            }
            softwarePlain = raster { drawText(plain) }
            softwareMorph = raster { state.draw(this) }
            metadata = JSONObject().put("id", case.id).put("text", case.text).put("mode", case.mode.name)
                .put("policy", case.policy.name).put("reason", case.reason).put("style", case.style.toString())
                .put("width", case.width).put("direction", case.direction.name)
                .put("density", case.density.density).put("fontScale", case.density.fontScale)
                .put("referenceSize", "$plainSize").put("morphSize", "$morphSize")
                .put("firstBaseline", plain.firstBaseline).put("lastBaseline", plain.lastBaseline)
                .put("lines", lineMetadata(plain)).put("characterBoxes", characterMetadata(plain))
                .put("segments", JSONArray().also { array -> diagnosticLive.forEach {
                    array.put(JSONObject().put("text", it.segment.text).put("index", it.segment.index)
                        .put("x", it.x.value).put("y", it.y.value).put("cell", it.cell.toString()))
                } })
        }
        assertTrue("Reference captures require hardware acceleration", accelerated)
        val hardwarePlain = rule.onNodeWithTag("typography-plain").captureToImage().asAndroidBitmap()
        val hardwareMorph = rule.onNodeWithTag("typography-morph").captureToImage().asAndroidBitmap()
        val softwareDifference = difference(softwarePlain, softwareMorph)
        val hardwareDifference = difference(hardwarePlain, hardwareMorph)
        val matches = softwareDifference.first == 0 && hardwareDifference.first == 0
        metadata.put("status", if (matches) "MATCH" else if (case.policy == TypographyPolicy.Exact) "UNEXPECTED_DIFFERENCE" else "KNOWN_DIFFERENCE")
        metadata.put("softwareChangedPixels", softwareDifference.first)
            .put("hardwareChangedPixels", hardwareDifference.first)
            .put("softwareInkPixels", inkPixels(softwarePlain)).put("hardwareInkPixels", inkPixels(hardwarePlain))
        // Retain each finding, including known differences, not only JUnit failures.
        val directory = File(output, case.id).apply { mkdirs() }
        save(directory, "software-reference", softwarePlain)
        save(directory, "software-morph", softwareMorph)
        save(directory, "software-diff", softwareDifference.second)
        save(directory, "hardware-reference", hardwarePlain)
        save(directory, "hardware-morph", hardwareMorph)
        save(directory, "hardware-diff", hardwareDifference.second)
        File(directory, "metadata.json").writeText(metadata.toString(2))
        results.put(metadata)
        writeReport()
        assertNoEdgeInk(case.id, softwarePlain)
        assertNoEdgeInk(case.id, softwareMorph)
        assertNoEdgeInk(case.id, hardwarePlain)
        assertNoEdgeInk(case.id, hardwareMorph)
        if (case.text.isNotBlank()) {
            assertTrue(case.id, inkPixels(softwarePlain) > 0)
            assertTrue(case.id, inkPixels(hardwarePlain) > 0)
        }
        if (case.policy == TypographyPolicy.Exact) {
            assertEquals("${case.id}: software typography differs; see $directory", 0, softwareDifference.first)
            assertEquals("${case.id}: hardware typography differs; see $directory", 0, hardwareDifference.first)
        }
        return metadata
    }

    /** Apply at the current test-clock instant, before any channels can advance. */
    fun retargetForAnimation(case: TypographyCase) {
        require(case.direction == scene.direction && case.density == scene.density)
        rule.runOnIdle {
            scene = case
            animate = true
            state.text = case.text
            state.style = case.style
            state.options = state.options.copy(segmentation = case.mode, snap = false)
            state.measure(Constraints(maxWidth = case.width), case.direction)
        }
    }

    fun captureAnimationFrame(label: String) {
        rule.runOnIdle {
            val directory = File(output, label).apply { mkdirs() }
            save(directory, "software-morph", raster { state.draw(this) })
            File(directory, "channels.json").writeText(JSONObject().put("target", state.text)
                .put("clockMillis", rule.mainClock.currentTime).put("animating", state.isAnimating)
                .put("live", JSONArray().also { a -> state.live.forEach {
                    a.put(JSONObject().put("id", it.id).put("text", it.segment.text)
                        .put("x", it.x.value).put("y", it.y.value).put("alpha", it.alpha.value)
                        .put("stripProgress", it.stripProgress.value).put("cell", it.cell.toString()))
                } }).toString(2))
        }
        save(File(output, label), "hardware-morph", rule.onNodeWithTag("typography-morph").captureToImage().asAndroidBitmap())
    }

    fun captureMorphForContinuity(): Pair<Bitmap, Bitmap> {
        lateinit var software: Bitmap
        rule.runOnIdle { software = raster { state.draw(this) } }
        return software to rule.onNodeWithTag("typography-morph").captureToImage().asAndroidBitmap()
    }

    fun assertCurrentMorphMatches(label: String, previous: Pair<Bitmap, Bitmap>) {
        val current = captureMorphForContinuity()
        assertEquals("$label software handoff", 0, difference(previous.first, current.first).first)
        assertEquals("$label hardware handoff", 0, difference(previous.second, current.second).first)
    }

    private fun frames() {
        repeat(2) { rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
    }
    private fun raster(draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit): Bitmap {
        val image = ImageBitmap(1024, 640)
        CanvasDrawScope().draw(scene.density, scene.direction, Canvas(image), Size(1024f, 640f)) {
            drawRect(Color.White)
            translate(256f, 256f, draw)
        }
        return image.asAndroidBitmap()
    }
    private fun writeReport() {
        File(output, "results.json").writeText(JSONObject().put("model", Build.MODEL).put("api", Build.VERSION.SDK_INT)
            .put("renderer", "settled segmented TextMorph versus independent BasicText")
            .put("cases", results).toString(2))
    }
}

private fun typographyOutput(): File {
    val root = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
        ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
    return File(root, "typography-reference").apply { mkdirs() }
}
private fun save(directory: File, name: String, image: Bitmap) {
    File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
private fun pixels(image: Bitmap): IntArray = IntArray(image.width * image.height).also {
    image.getPixels(it, 0, image.width, 0, 0, image.width, image.height)
}
private fun difference(a: Bitmap, b: Bitmap): Pair<Int, Bitmap> {
    require(a.width == b.width && a.height == b.height)
    val left = pixels(a); val right = pixels(b)
    var count = 0
    val diff = IntArray(left.size) { if (left[it] == right[it]) android.graphics.Color.WHITE else { count++; android.graphics.Color.MAGENTA } }
    return count to Bitmap.createBitmap(diff, a.width, a.height, Bitmap.Config.ARGB_8888)
}
private fun inkPixels(image: Bitmap): Int = pixels(image).count { it != android.graphics.Color.WHITE }
private fun assertNoEdgeInk(id: String, image: Bitmap) {
    val p = pixels(image); val w = image.width; val h = image.height
    for (x in 0 until w) {
        assertEquals("$id: top cropped", android.graphics.Color.WHITE, p[x])
        assertEquals("$id: bottom cropped", android.graphics.Color.WHITE, p[(h - 1) * w + x])
    }
    for (y in 0 until h) {
        assertEquals("$id: left cropped", android.graphics.Color.WHITE, p[y * w])
        assertEquals("$id: right cropped", android.graphics.Color.WHITE, p[y * w + w - 1])
    }
}
private fun lineMetadata(layout: TextLayoutResult): JSONArray = JSONArray().also { a ->
    repeat(layout.lineCount) { i -> a.put(JSONObject().put("start", layout.getLineStart(i))
        .put("end", layout.getLineEnd(i)).put("left", layout.getLineLeft(i)).put("right", layout.getLineRight(i))
        .put("top", layout.getLineTop(i)).put("bottom", layout.getLineBottom(i)).put("baseline", layout.getLineBaseline(i))) }
}
private fun characterMetadata(layout: TextLayoutResult): JSONArray = JSONArray().also { a ->
    repeat(layout.layoutInput.text.length) { i -> a.put(JSONObject().put("index", i)
        .put("box", layout.getBoundingBox(i).toString())
        .put("left", layout.getBoundingBox(i).left).put("right", layout.getBoundingBox(i).right)
        .put("width", layout.getBoundingBox(i).width).put("bidiRun", layout.getBidiRunDirection(i).name)) }
}

@RunWith(AndroidJUnit4::class)
class TypographyReferenceTest {
    @get:Rule val rule = createComposeRule()

    @Before fun prepareVisibleTestActivity() = prepareTypographyDevice(rule)

    @Test fun settledRendererComparedWithIndependentBasicText() {
        val h = TypographyReferenceHarness(rule)
        h.mount()
        val failures = ArrayList<String>()
        for (case in typographyCases()) {
            try { h.compare(case) } catch (e: AssertionError) { failures += "${case.id}: ${e.message}" }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun independentReferenceDetectsDeliberatelyDisplacedGlyph() {
        val h = TypographyReferenceHarness(rule, "oracle-self-check")
        h.mount()
        val control = TypographyCase("control", "H", policy = TypographyPolicy.Exact)
        h.compare(control)
        rule.runOnIdle {
            val glyph = h.state.live.single()
            glyph.x.snapTo(glyph.x.value + 2f)
            h.state.frameTick.intValue++
        }
        val result = h.compare(control.copy(id = "deliberate-two-pixel-displacement",
            policy = TypographyPolicy.Characterize, reason = "Negative control: the reference must detect displaced morph ink"))
        assertTrue(result.toString(), result.getInt("softwareChangedPixels") > 0)
        assertTrue(result.toString(), result.getInt("hardwareChangedPixels") > 0)
    }
}

internal fun prepareTypographyDevice(rule: androidx.compose.ui.test.junit4.ComposeContentTestRule) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Hardware node capture needs a resumed, visible host; keepScreenOn alone cannot wake it.
        for (command in listOf("input keyevent KEYCODE_WAKEUP", "wm dismiss-keyguard")) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        val context = instrumentation.targetContext
        rule.waitUntil(5000) {
            context.getSystemService(PowerManager::class.java).isInteractive &&
                !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        }
    }

internal fun typographyCases(): List<TypographyCase> {
    val cases = ArrayList<TypographyCase>()
    for (mode in Segmentation.entries) {
        for ((id, text) in listOf("digits" to "12,345", "single-glyph" to "H", "empty" to "", "spaces" to "   ")) {
            cases += TypographyCase("$id-$mode", text, mode, policy = TypographyPolicy.Exact,
                reason = "Independent control: require identical settled pixels")
        }
        for ((id, text) in listOf(
            "kerning" to "AVATAR To Wa", "ligatures" to "office affinity ffi",
            "arabic" to "مرحبا بالعالم", "indic" to "नमस्ते दुनिया", "thai" to "ภาษาไทย",
            "combining" to "a\u0301 e\u0308 n\u0303", "emoji" to "👩🏽‍💻 👨‍👩‍👧‍👦",
            "bidi" to "שלום 123 (abc)", "bidi-isolates" to "abc \u2067שלום ١٢٣\u2069 xyz",
            "newlines" to "words wrap here\nnext line", "wrapped-lines" to "words wrapping across lines\nnext line", "wrapped-word" to "Supercalifragilisticexpialidocious",
        )) {
            val knownDifference = when {
                id in listOf("bidi", "bidi-isolates") -> "Legacy segment placement differs at bidi boundaries; independently recorded finding"
                id == "kerning" -> "Inter-segment kerning/word placement differs from the full paragraph"
                mode == Segmentation.GRAPHEME && id in listOf("ligatures", "arabic") -> "Grapheme splitting loses full-paragraph shaping context"
                mode == Segmentation.WORD && id == "wrapped-word" -> "A word crossing full-paragraph lines is drawn as one unwrapped segment"
                mode != Segmentation.GRAPHEME && id == "wrapped-lines" -> "Word-mode placement at wrapped line boundaries differs from the full paragraph"
                else -> null
            }
            cases += TypographyCase("$id-$mode", text, mode,
                width = if (id in listOf("wrapped-word", "wrapped-lines")) 100 else 300,
                policy = if (knownDifference == null) TypographyPolicy.Exact else TypographyPolicy.Characterize,
                reason = knownDifference ?: "Require exact parity for this text, font, width and mode")
        }
    }
    cases += TypographyCase("digits-rtl-scaled", "12,345", direction = LayoutDirection.Rtl, density = Density(1.5f, 1.2f))
    cases += TypographyCase("ligatures-off", "office affinity ffi", mode = Segmentation.GRAPHEME,
        style = TypographyCase("", "").style.copy(fontFeatureSettings = "'liga' 0, 'kern' 0"))
    cases += TypographyCase("serif-arabic-rtl", "مرحبا 123", direction = LayoutDirection.Rtl,
        style = TypographyCase("", "").style.copy(fontFamily = FontFamily.Serif, localeList = LocaleList("ar-EG")))
    return cases
}
