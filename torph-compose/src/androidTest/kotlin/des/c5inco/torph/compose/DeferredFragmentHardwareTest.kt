package des.c5inco.torph.compose

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.rememberTextMeasurer
import des.c5inco.torph.core.Segmenter
import des.c5inco.torph.core.SimpleSegmenter
import java.util.Locale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import des.c5inco.torph.core.Segmentation
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeferredFragmentHardwareTest {
    @get:Rule val rule = createComposeRule()
    @Before fun prepare() = prepareTypographyDevice(rule)

    @Test fun disjointVisualRunsRemainDisjointInHardware() {
        val segmenter = object : Segmenter {
            override fun graphemes(text: String, locale: Locale) = SimpleSegmenter.graphemes(text, locale)
            override fun words(text: String, locale: Locale) = when {
                text.startsWith("HEAD") -> listOf(0 until 4, 4 until 11, 11 until text.length)
                text.length > 6 -> listOf(0 until 6, 6 until text.length)
                else -> listOf(text.indices)
            }
        }
        val subject = MorphSubject {
            val measurer = rememberTextMeasurer(cacheSize = 0)
            val scope = rememberCoroutineScope()
            val state = remember(measurer, scope) { TextMorphState(measurer, scope, segmenter) }
            DisposableEffect(state) { onDispose { state.dispose() } }
            state
        }
        val h = TypographyReferenceHarness(rule, "deferred-disjoint-runs", deferInitial = true, subject = subject)
        h.mount()
        for (case in listOf(
            TypographyCase("disjoint-bidi", "abc שלוםwxy xyz", Segmentation.WORD),
            TypographyCase("interior-path", "HEAD fffאבג xyz TAIL", Segmentation.WORD,
                style = TypographyCase("", "").style.copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)),
        )) {
            h.compare(case)
            val before = h.captureMorphForContinuity()
            h.retargetForAnimation(case.copy(text = case.text + "!"))
            h.assertCurrentMorphMatches(case.id, before)
            rule.mainClock.advanceTimeBy(96)
            h.assertPreparedFragmentTarget(case.id)
            h.assertPreparedFragmentTarget("${case.id}-negative-control", perturb = 2f)
            rule.mainClock.advanceTimeBy(1000)
        }
    }

    @Test fun preparedFragmentsMatchBasicTextAndTheOracleDetectsDisplacement() {
        val h = TypographyReferenceHarness(rule, "deferred-fragment-targets", deferInitial = true)
        h.mount()
        val base = TypographyCase("", "", Segmentation.GRAPHEME)
        val cases = listOf(
            base.copy(id = "kerning", text = "AVATAR To Wa"),
            base.copy(id = "ligature", text = "office ffi"),
            base.copy(id = "word-kerning", text = "AVATAR To Wa", mode = Segmentation.WORD),
            base.copy(id = "auto-ligature", text = "office affinity ffi", mode = Segmentation.AUTO),
            base.copy(id = "arabic", text = "مرحبا 123"),
            base.copy(id = "arabic-auto", text = "مرحبا 123", mode = Segmentation.AUTO),
            base.copy(id = "bidi-word", text = "אב\u202Aabc שלום123\u202Cגד", mode = Segmentation.WORD),
            base.copy(id = "bidi", text = "שלום (123) abc"),
            base.copy(id = "isolate", text = "abc \u2067שלום ١٢٣\u2069 xyz"),
            base.copy(id = "combining", text = "a\u0301 e\u0308 n\u0303"),
            base.copy(id = "emoji", text = "👩🏽‍💻"),
            base.copy(id = "italic-overhang", text = "fjTA", style = base.style.copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)),
            base.copy(id = "rtl-density", text = "12,345", direction = LayoutDirection.Rtl, density = Density(1.5f, 1.2f)),
        )
        for (case in cases) {
            h.compare(case)
            val before = h.captureMorphForContinuity()
            h.retargetForAnimation(case.copy(text = case.text + "!"))
            h.assertCurrentMorphMatches(case.id, before)
            if (case.id in listOf("kerning", "arabic", "bidi")) {
                h.captureAnimationFrame("${case.id}-0")
                repeat(5) { frame ->
                    rule.mainClock.advanceTimeBy(16)
                    h.captureAnimationFrame("${case.id}-${(frame + 1) * 16}")
                }
                rule.mainClock.advanceTimeBy(16)
            } else rule.mainClock.advanceTimeBy(96)
            h.assertPreparedFragmentTarget(case.id)
            if (case.id == "kerning") h.assertPreparedFragmentTarget("negative-control", perturb = 2f)
            rule.mainClock.advanceTimeBy(1000)
        }
    }
}
