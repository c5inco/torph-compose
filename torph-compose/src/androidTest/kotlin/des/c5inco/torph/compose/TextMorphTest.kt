package des.c5inco.torph.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class TextMorphTest {
    @get:Rule
    val rule = createComposeRule()

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
        val idsHello = before.take(5).map { it.id }

        text = "Hello there"
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val after = state.segments
        assertEquals(idsHello, after.take(5).map { it.id })
        assertTrue(state.liveCount > after.size) // exiting segments still drawn mid-animation
        assertEquals(listOf("start"), events)

        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(after.size, state.liveCount)
        assertEquals(listOf("start", "complete"), events)
    }

    @Test
    fun disabledSnapsAndStillFiresCallbacks() {
        var text by mutableStateOf("1")
        lateinit var state: TextMorphState
        val events = ArrayList<String>()
        rule.setContent {
            state = rememberTextMorphState()
            TextMorph(
                text = text,
                state = state,
                disabled = true,
                onAnimationStart = { events += "start" },
                onAnimationComplete = { events += "complete" },
            )
        }
        rule.waitForIdle()
        text = "2"
        rule.waitForIdle()
        assertEquals(1, state.liveCount)
        assertEquals(listOf("start", "complete"), events)
    }
}
