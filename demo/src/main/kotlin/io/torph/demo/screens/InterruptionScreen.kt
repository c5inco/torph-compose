package io.torph.demo.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.torph.compose.MorphEase
import io.torph.compose.TextMorph
import io.torph.demo.Caption
import io.torph.demo.CodeBlock
import io.torph.demo.Gap
import io.torph.demo.LabeledSlider
import io.torph.demo.SectionTitle
import io.torph.demo.Stage
import io.torph.demo.ToggleRow
import kotlinx.coroutines.delay
import java.util.Locale

private const val SCRIPT = "Typing simulation: each keystroke retargets the morph mid-flight. Velocity carries over, so nothing snaps."

@Composable
fun InterruptionScreen() {
    var running by rememberSaveable { mutableStateOf(true) }
    var tickMs by rememberSaveable { mutableIntStateOf(80) }
    var elapsed by rememberSaveable { mutableLongStateOf(0L) }
    var typed by rememberSaveable { mutableIntStateOf(0) }
    var starts by rememberSaveable { mutableIntStateOf(0) }
    var completes by rememberSaveable { mutableIntStateOf(0) }
    var cancels by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(running, tickMs) {
        while (running) {
            delay(tickMs.toLong())
            elapsed += tickMs
            typed = if (typed >= SCRIPT.length + 12) 0 else typed + 1
        }
    }
    val minutes = (elapsed / 60000)
    val seconds = (elapsed / 1000) % 60
    val hundredths = (elapsed / 10) % 100
    val timer = String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)

    SectionTitle("Ticking timer (every $tickMs ms)")
    Stage(88) {
        TextMorph(
            text = timer,
            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            ease = MorphEase.Spring(stiffness = 220f, damping = 22f),
            onAnimationStart = { starts++ },
            onAnimationComplete = { completes++ },
            onAnimationCancel = { cancels++ },
        )
    }
    Gap(4)
    Caption("callbacks: start $starts · complete $completes · cancel $cancels — exactly one of complete/cancel per morph")

    SectionTitle("Live typing")
    Stage(120) {
        TextMorph(
            text = SCRIPT.take(typed.coerceAtMost(SCRIPT.length)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Gap(12)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { running = !running }) { Text(if (running) "Pause" else "Resume") }
        Button(onClick = { elapsed = 0; typed = 0 }) { Text("Reset") }
    }
    LabeledSlider("tick", tickMs.toFloat(), 16f..500f, { "${it.toInt()} ms" }) { tickMs = it.toInt() }
    ToggleRow("running", running) { running = it }
    CodeBlock(
        """
        // Every value change retargets in place; animatables keep their velocity.
        TextMorph(text = timer, ease = MorphEase.Spring(stiffness = 220f, damping = 22f))
        """,
    )
}
