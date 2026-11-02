package des.c5inco.torph.compose

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import org.json.JSONArray
import org.json.JSONObject
import des.c5inco.torph.core.Segmentation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Whole-paragraph selection geometry is independent of character-box edge selection. */
class BidiGeometryTest {
    @get:Rule val rule = createComposeRule()

    @Before fun prepare() = prepareTypographyDevice(rule)

    @Test fun rollingRetargetPreservesOriginsAndSettlesToIndependentReference() {
        val h = TypographyReferenceHarness(rule, "bidi-animation")
        h.mount()
        for (mode in Segmentation.entries) {
            var case = TypographyCase("rest-$mode", "שלום 123 (abc)", mode)
            h.compare(case)
            h.captureAnimationFrame("$mode-rest")
            for ((step, text) in listOf("שלום 456 (abc)", "שלום (567) abc").withIndex()) {
                val before = HashMap<Long, List<Float>>()
                rule.runOnIdle { h.state.live.filter { !it.exiting }.forEach {
                    before[it.id] = listOf(it.x.value + it.layout.getLineLeft(0), it.y.value + it.layout.firstBaseline, it.alpha.value, it.scale.value)
                } }
                case = case.copy(id = "final-$mode-$step", text = text)
                h.retargetForAnimation(case)
                rule.runOnIdle {
                    assertTrue(h.state.isAnimating)
                    var checked = 0
                    h.state.live.filter { !it.exiting }.forEach { live -> before[live.id]?.let { old ->
                        val values = listOf(live.x.value + live.layout.getLineLeft(0), live.y.value + live.layout.firstBaseline, live.alpha.value, live.scale.value)
                        for (i in old.indices) assertEquals("$mode step=$step id=${live.id} channel=$i", old[i], values[i], 0.0001f)
                        checked++
                    } }
                    assertTrue("Persistent glyph origins must be checked", checked >= 2)
                }
                h.captureAnimationFrame("$mode-$step-retarget")
                rule.mainClock.advanceTimeBy(96)
                rule.waitForIdle()
                rule.runOnIdle {
                    assertTrue(h.state.isAnimating)
                    assertTrue(h.state.live.any { it.strip != null })
                    for (live in h.state.live) {
                        assertTrue(live.cell.toString(), live.cell.width >= 0f)
                        live.strip?.let { assertTrue(live.stripProgress.value > -1 && live.stripProgress.value < it.size) }
                    }
                }
                h.captureAnimationFrame("$mode-$step-mid")
            }
            rule.mainClock.advanceTimeBy(1000)
            rule.waitForIdle()
            h.compare(case) // Hard software/hardware BasicText equality after interruption.
            rule.runOnIdle {
                assertFalse(h.state.isAnimating)
                assertFalse(h.state.live.any { it.exiting || it.strip != null })
            }
        }
    }

    @Test fun placementMatchesSelectionGeometryAndBasicTextAcrossDirections() {
        val h = TypographyReferenceHarness(rule, "bidi-geometry")
        h.mount()
        for (direction in LayoutDirection.entries) for (mode in Segmentation.entries) for ((id, text) in listOf(
            "mixed" to "שלום 123 (abc)", "isolate" to "abc \u2067שלום ١٢٣\u2069 xyz",
            "brackets" to "abc (שלום) xyz", "number-brackets" to "שלום (123) abc",
            "both-bracket-runs" to "שלום (123) abc (xyz)",
        )) {
            val case = TypographyCase("$id-$direction-$mode", text, mode = mode, direction = direction,
                reason = "Bidi geometry and rendering must match the independent paragraph")
            h.compare(case)
            rule.runOnIdle {
                val full = h.plain
                for (live in h.state.live) {
                    assertTrue(live.cell.toString(), live.cell.width >= 0f)
                    val expected = full.getPathForRange(live.segment.index, live.segment.end).getBounds()
                    assertEquals(case.id, expected.left, live.cell.left, 0f)
                    assertEquals(case.id, expected.right, live.cell.right, 0f)
                }
                val records = JSONArray()
                for (i in text.indices) {
                    val box = full.getBoundingBox(i)
                    val selection = full.getPathForRange(i, i + 1).getBounds()
                    records.put(JSONObject().put("index", i).put("char", text.substring(i, i + 1))
                        .put("box", box.toString()).put("selection", selection.toString())
                        .put("primaryStart", full.getHorizontalPosition(i, true))
                        .put("primaryEnd", full.getHorizontalPosition(i + 1, true))
                        .put("secondaryStart", full.getHorizontalPosition(i, false))
                        .put("secondaryEnd", full.getHorizontalPosition(i + 1, false))
                        .put("run", full.getBidiRunDirection(i).name))
                }
                File(h.output, "${case.id}-edges.json").writeText(records.toString(2))
            }
        }
    }
}
