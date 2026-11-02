package des.c5inco.torph.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import des.c5inco.torph.core.Segmentation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class MorphRegressionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fadeScaleRollingAndClipFramesMatchOnSoftwareAndHardware() {
        val h = MorphParityHarness(rule)
        h.mount(MorphScene(text = "12,345"))
        h.check("hardware-initial", settled = true, hardware = true)
        val clips = listOf(MorphClip.Default, MorphClip.lineBox(), MorphClip.window())
        for ((i, clip) in clips.withIndex()) {
            h.update(h.scene.copy(text = "${13 + i},${456 + i}", options = h.scene.options.copy(clip = clip, scale = i != 1)))
            h.check("hardware-$i-applied", hardware = true)
            h.advance(96)
            rule.runOnIdle { assertTrue(h.actual.isAnimating); assertTrue(h.actual.live.any { it.strip != null }) }
            h.check("hardware-$i-mid", hardware = true)
            h.update(h.scene.copy(text = "${14 + i},${567 + i}"))
            h.check("hardware-$i-interrupted", hardware = true)
            h.advance(1000)
            h.check("hardware-$i-settled", settled = true, hardware = true)
        }
        h.update(h.scene.copy(text = "New label", options = h.scene.options.copy(numbers = false)))
        h.advance(96)
        rule.runOnIdle {
            assertTrue(h.actual.live.any { it.entering && it.alpha.value > 0f && it.alpha.value < 1f })
            assertTrue(h.actual.live.any { it.exiting && it.scale.value < 1f })
        }
        h.check("hardware-fade-scale", hardware = true)
        h.advance(1000)
        h.check("hardware-fade-settled", settled = true, hardware = true)
    }

    @Test fun unicodeShapingCorpusMatchesAcrossModesAndWrapping() {
        val h = MorphParityHarness(rule)
        h.mount(MorphScene(text = ""))
        for (mode in Segmentation.entries) for ((index, text) in corpus.withIndex()) {
            val style = TextStyle(color = if (index % 2 == 0) Color.Black else Color(0xFF2255AA),
                fontSize = 22.sp, fontFeatureSettings = if (index % 2 == 0) "'kern' 1, 'liga' 1" else "'kern' 0, 'liga' 0",
                localeList = LocaleList(if (index % 2 == 0) "en-US" else "ar-EG"))
            h.update(MorphScene(text, style, MorphOptions(segmentation = mode), maxWidth = if (index % 2 == 0) 260 else 110))
            h.check("corpus-$mode-$index-applied")
            h.advance(128)
            h.check("corpus-$mode-$index-mid")
            h.advance(1000)
            h.check("corpus-$mode-$index-settled", settled = true)
        }
    }

    @Test fun seededTextStyleAndOptionSequencesHaveDeterministicParity() {
        val h = MorphParityHarness(rule)
        h.mount()
        for (seed in listOf(73013, 99173)) {
            val random = Random(seed)
            repeat(18) { step ->
                val next = h.scene.copy(
                    text = corpus[random.nextInt(corpus.size)],
                    style = h.scene.style.copy(fontSize = (18 + random.nextInt(8)).sp,
                        fontFamily = if (random.nextBoolean()) FontFamily.Serif else FontFamily.SansSerif),
                    maxWidth = listOf(100, 180, 260)[random.nextInt(3)],
                    options = h.scene.options.copy(
                        scale = random.nextBoolean(), numbers = random.nextBoolean(),
                        segmentation = Segmentation.entries[random.nextInt(Segmentation.entries.size)],
                        clip = if (random.nextBoolean()) MorphClip.lineBox() else MorphClip.Default,
                    ),
                )
                h.update(next)
                h.check("seed-$seed-step-$step-applied")
                h.advance(listOf(48L, 112L, 192L)[random.nextInt(3)])
                h.check("seed-$seed-step-$step-mid")
                // Some generations settle; others are deliberately superseded by the next step.
                if (step % 3 == 2) {
                    h.advance(1000)
                    h.check("seed-$seed-step-$step-settled", settled = true)
                }
            }
        }
        h.advance(1000)
        h.check("seed-final", settled = true)
        rule.runOnIdle {
            assertEquals(h.actualEvents.count { it == "start" }, h.actualEvents.count { it == "cancel" || it == "complete" })
        }
    }

    @Test fun callbacksIdsAndDisabledTransitionsSatisfyIndependentContracts() {
        val h = MorphParityHarness(rule)
        h.mount(MorphScene(text = "Hello world", options = MorphOptions(segmentation = Segmentation.WORD)))
        val hello = h.actual.segments.first().id
        h.update(h.scene.copy(text = "Hello there"))
        rule.runOnIdle {
            assertEquals(hello, h.actual.segments.first().id)
            assertTrue(h.actual.live.any { it.exiting })
            assertEquals(listOf("start"), h.actualEvents)
        }
        h.advance(96)
        h.update(h.scene.copy(text = "Hello again"))
        h.check("contracts-interrupt")
        h.advance(1000)
        h.check("contracts-settle", settled = true)
        rule.runOnIdle { assertEquals(listOf("start", "cancel", "start", "complete"), h.actualEvents) }
        h.update(h.scene.copy(text = "Hello disabled", options = h.scene.options.copy(snap = true)))
        h.check("contracts-disabled", settled = true)
        rule.runOnIdle { assertEquals(listOf("start", "cancel", "start", "complete", "start", "complete"), h.actualEvents) }
    }

    @Test fun retargetPreservesPersistingChannelValuesBeforeNextFrame() {
        val h = MorphParityHarness(rule)
        h.mount(MorphScene(text = "Hello world", options = MorphOptions(segmentation = Segmentation.WORD)))
        h.update(h.scene.copy(text = "prefix Hello world"))
        h.advance(96)
        val next = h.scene.copy(text = "longer prefix Hello world")
        rule.runOnIdle {
            for (state in listOf(h.actual, h.expected)) {
                val hello = state.live.single { it.segment.text == "Hello" }
                val values = listOf(hello.x.value, hello.y.value, hello.alpha.value, hello.scale.value)
                val id = hello.id
                assertTrue(hello.x.active)
                state.text = next.text
                state.measure(Constraints(maxWidth = next.maxWidth), LayoutDirection.Ltr)
                val retargeted = state.live.single { it.id == id }
                assertSame(hello, retargeted)
                assertEquals(values, listOf(retargeted.x.value, retargeted.y.value, retargeted.alpha.value, retargeted.scale.value))
            }
        }
        h.update(next)
        h.check("continuity-retargeted", hardware = true)
        h.advance(1000)
        h.check("continuity-settled", settled = true, hardware = true)
        assertEquals(listOf("start", "cancel", "start", "complete"), h.actualEvents)
    }

    private companion object {
        val corpus = listOf(
            "", " ", "  trailing  ", "AVATAR To Wa", "office affinity ffi fi fl", "line one\nline two",
            "\n\n", "a\u0301 e\u0308 n\u0303", "á ë ñ", "مرحبا بالعالم", "שלום 123 (abc)",
            "abc \u2067שלום ١٢٣\u2069 xyz", "कर्म क्षि हिन्दी", "ภาษาไทย", "中文 日本語 한글",
            "👩🏽‍💻 👨‍👩‍👧‍👦", "🇺🇸 🇯🇵 1️⃣", "123,456.78", "١٢٣٬٤٥٦", "999→1000",
            "Supercalifragilisticexpialidocious", "tabs\tand\tspaces",
        )
    }
}
