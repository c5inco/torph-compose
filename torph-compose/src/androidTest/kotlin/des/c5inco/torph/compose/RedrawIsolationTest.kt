package des.c5inco.torph.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A sibling redrawing must not re-record the morphing text. With no container layer around them
 * (the demo's clipped `Stage` would hide this), both used to draw into the same layer, so every
 * frame of a neighbour's animation redrew every segment.
 */
@RunWith(AndroidJUnit4::class)
class RedrawIsolationTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun siblingRedrawDoesNotRedrawTheText() {
        var tick by mutableIntStateOf(0)
        var textDraws = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val state = rememberTextMorphState()
            state.style = TextStyle(fontSize = 24.sp)
            state.text = "Hello world"
            Column {
                // Reads tick only in draw, so changing it invalidates this sibling's drawing alone.
                Spacer(Modifier.size(10.dp).drawBehind { drawRect(if (tick % 2 == 0) Color.Red else Color.Blue) })
                // Placed after textMorph's own modifiers, so it draws inside the text's layer when it has one.
                Spacer(Modifier.textMorph(state).drawBehind { textDraws++ })
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        val settled = textDraws

        repeat(5) {
            tick++
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
        assertEquals("text redrawn by its sibling's redraws", settled, textDraws)
    }
}
