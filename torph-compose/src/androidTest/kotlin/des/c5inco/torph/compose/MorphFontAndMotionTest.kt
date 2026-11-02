package des.c5inco.torph.compose

import android.Manifest
import android.graphics.Typeface
import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MorphFontAndMotionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun deferredFontsPreserveEntryExitRollingAndInterruptedFrameParity() {
        val h = MorphParityHarness(rule)
        h.mount(MorphScene(text = "Wi"))
        val cases = listOf("Wi" to "Wi wide", "Wi wide" to "Wi", "123" to "456")
        for ((index, pair) in cases.withIndex()) {
            val loaded = CompletableDeferred<Typeface>()
            val family = FontFamily(deferredTestFont(loaded))
            val resolver = createFontFamilyResolver(InstrumentationRegistry.getInstrumentation().targetContext)
            h.update(h.scene.copy(text = pair.first, style = h.scene.style.copy(fontFamily = family)),
                MorphEnvironment(resolver = resolver))
            h.advance(1000)
            h.update(h.scene.copy(text = pair.second))
            h.advance(96)
            val oldActual = h.actual.layoutResult!!
            val oldExpected = h.expected.layoutResult!!
            rule.runOnIdle {
                assertTrue(h.actual.isAnimating)
                if (index == 0) assertTrue(h.actual.live.any { it.entering })
                if (index == 1) assertTrue(h.actual.live.any { it.exiting })
                if (index == 2) assertTrue(h.actual.live.any { it.strip != null })
            }
            val cancels = h.actualEvents.count { it == "cancel" }
            loaded.complete(Typeface.MONOSPACE)
            rule.waitUntil(5000) {
                oldActual.multiParagraph.intrinsics.hasStaleResolvedFonts && oldExpected.multiParagraph.intrinsics.hasStaleResolvedFonts
            }
            rule.runOnIdle {
                assertTrue(h.actual.isAnimating)
                assertSame(oldActual, h.actual.layoutResult)
                assertEquals(cancels, h.actualEvents.count { it == "cancel" })
            }
            h.check("font-$index-completed-mid-morph", hardware = true)
            // A new target after loading must refresh persisting segments and cancel only once.
            h.update(h.scene.copy(text = if (index == 2) "567" else pair.second + "i"))
            h.check("font-$index-retargeted", hardware = true)
            h.advance(1000)
            rule.waitUntil(5000) {
                !h.actual.layoutResult!!.multiParagraph.intrinsics.hasStaleResolvedFonts &&
                    !h.expected.layoutResult!!.multiParagraph.intrinsics.hasStaleResolvedFonts
            }
            h.check("font-$index-settled", settled = true, hardware = true)
            rule.runOnIdle {
                assertEquals(cancels + 1, h.actualEvents.count { it == "cancel" })
                assertFalse(h.actual.live.any { it.layout.multiParagraph.intrinsics.hasStaleResolvedFonts })
                assertNotSame(oldActual.multiParagraph, h.actual.layoutResult!!.multiParagraph)
            }
        }
    }

    @Test fun systemReducedMotionSnapsRealComposableAndRestoresSetting() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val key = Settings.Global.ANIMATOR_DURATION_SCALE
        val previous = Settings.Global.getString(resolver, key)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
        try {
            assertTrue(Settings.Global.putString(resolver, key, "0"))
            assertEquals("0", Settings.Global.getString(resolver, key))
            val h = MorphParityHarness(rule, respectReducedMotion = true)
            h.mount(MorphScene(text = "123"))
            rule.runOnIdle { assertTrue(h.actual.options.snap) }
            h.update(h.scene.copy(text = "456"))
            h.check("reduced-motion-digits", settled = true, hardware = true)
            h.update(h.scene.copy(text = "wide label"))
            h.check("reduced-motion-entry-exit", settled = true, hardware = true)
            rule.runOnIdle { assertEquals(listOf("start", "complete", "start", "complete"), h.actualEvents) }
        } finally {
            try {
                assertTrue(Settings.Global.putString(resolver, key, previous))
                assertEquals("Animator setting must be restored", previous, Settings.Global.getString(resolver, key))
            } finally {
                instrumentation.uiAutomation.dropShellPermissionIdentity()
            }
        }
    }
}
