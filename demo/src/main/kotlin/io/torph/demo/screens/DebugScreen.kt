package io.torph.demo.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.torph.compose.TextMorph
import io.torph.compose.rememberTextMorphState
import io.torph.demo.Caption
import io.torph.demo.CodeBlock
import io.torph.demo.Gap
import io.torph.demo.LabeledSlider
import io.torph.demo.Stage
import io.torph.demo.ToggleRow
import kotlin.time.Duration.Companion.milliseconds

private val steps = listOf("Balance $1,234.56", "Balance $1,334.56", "Balance $998.10", "New balance $998.10", "Balance $1,234.56")

@Composable
fun DebugScreen() {
    var i by rememberSaveable { mutableIntStateOf(0) }
    var debug by rememberSaveable { mutableStateOf(true) }
    var slow by rememberSaveable { mutableIntStateOf(1200) }
    val state = rememberTextMorphState()
    Caption("Blue = persisting, green = entering, red = exiting, orange = digit. Labels show segment id and place value.")
    Gap(12)
    Stage(120) {
        TextMorph(
            text = steps[i],
            state = state,
            debug = debug,
            duration = slow.milliseconds,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Gap(4)
    Caption("segments: ${state.segments.size} · live: ${state.liveCount} · animating: ${state.isAnimating}")
    Gap(8)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { i = (i + 1) % steps.size }) { Text("Next") }
    }
    ToggleRow("debug overlay", debug) { debug = it }
    LabeledSlider("duration", slow.toFloat(), 100f..3000f, { "${it.toInt()} ms" }) { slow = it.toInt() }
    CodeBlock(
        """
        val state = rememberTextMorphState()
        TextMorph(text = text, state = state, debug = true)
        // state.segments, state.liveCount, state.isAnimating, state.layoutResult
        """,
    )
}
