package des.c5inco.torph.demo.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import des.c5inco.torph.compose.MorphEase
import des.c5inco.torph.compose.MorphEasings
import des.c5inco.torph.compose.MorphSizeMode
import des.c5inco.torph.compose.TextMorph
import des.c5inco.torph.demo.Caption
import des.c5inco.torph.demo.Choice
import des.c5inco.torph.demo.CodeBlock
import des.c5inco.torph.demo.Gap
import des.c5inco.torph.demo.HGap
import des.c5inco.torph.demo.LabeledSlider
import des.c5inco.torph.demo.SectionTitle
import des.c5inco.torph.demo.Stage
import des.c5inco.torph.demo.ToggleRow
import kotlin.time.Duration.Companion.milliseconds

private val canned = listOf("Hello world", "Hello there", "Goodbye world", "Hello, world 42", "Hello world")

@Composable
fun PlaygroundScreen() {
    var input by rememberSaveable { mutableStateOf("Hello world") }
    var cycle by rememberSaveable { mutableIntStateOf(0) }
    var duration by rememberSaveable { mutableIntStateOf(400) }
    var easingIndex by rememberSaveable { mutableIntStateOf(0) }
    var useSpring by rememberSaveable { mutableStateOf(false) }
    var stiffness by rememberSaveable { mutableStateOf(100f) }
    var damping by rememberSaveable { mutableStateOf(10f) }
    var mass by rememberSaveable { mutableStateOf(1f) }
    var scale by rememberSaveable { mutableStateOf(true) }
    var numbers by rememberSaveable { mutableStateOf(true) }
    var reduced by rememberSaveable { mutableStateOf(true) }
    var sizeMode by rememberSaveable { mutableStateOf(MorphSizeMode.Animate) }
    var status by rememberSaveable { mutableStateOf("idle") }

    val ease: MorphEase = if (useSpring) MorphEase.Spring(stiffness, damping, mass) else MorphEase.Curve(MorphEasings.presets[easingIndex].second)

    Stage {
        TextMorph(
            text = input,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            ease = ease,
            duration = duration.milliseconds,
            scale = scale,
            numbers = numbers,
            respectReducedMotion = reduced,
            sizeMode = sizeMode,
            onAnimationStart = { status = "start" },
            onAnimationComplete = { status = "complete" },
            onAnimationCancel = { status = "cancel" },
        )
    }
    Gap(4)
    Caption("last callback: $status")
    Gap(12)
    OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Gap()
    Row {
        Button(onClick = { cycle = (cycle + 1) % canned.size; input = canned[cycle] }) { Text("Cycle") }
        HGap()
        OutlinedButton(onClick = { input = input.reversed() }) { Text("Reverse") }
        HGap()
        OutlinedButton(onClick = { input = input.split(" ").shuffled().joinToString(" ") }) { Text("Shuffle words") }
    }

    SectionTitle("Easing")
    ToggleRow("Spring instead of curve", useSpring) { useSpring = it }
    if (useSpring) {
        LabeledSlider("stiffness", stiffness, 10f..600f) { stiffness = it }
        LabeledSlider("damping", damping, 1f..60f) { damping = it }
        LabeledSlider("mass", mass, 0.2f..5f, { "%.1f".format(it) }) { mass = it }
    } else {
        Choice(MorphEasings.presets.indices.toList(), easingIndex, { MorphEasings.presets[it].first }) { easingIndex = it }
        LabeledSlider("duration", duration.toFloat(), 0f..2000f, { "${it.toInt()} ms" }) { duration = it.toInt() }
    }

    SectionTitle("Options")
    ToggleRow("scale exiting/entering", scale) { scale = it }
    ToggleRow("numbers (place-value)", numbers) { numbers = it }
    ToggleRow("respectReducedMotion", reduced) { reduced = it }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("sizeMode", style = MaterialTheme.typography.bodyMedium)
        Choice(MorphSizeMode.entries, sizeMode, { it.name }) { sizeMode = it }
    }

    CodeBlock(
        """
        TextMorph(
            text = input,
            style = MaterialTheme.typography.headlineMedium,
            ease = ${if (useSpring) "MorphEase.Spring(stiffness = ${stiffness.toInt()}f, damping = ${damping.toInt()}f, mass = ${"%.1f".format(mass)}f)" else "MorphEase.Curve(MorphEasings.${easingName(easingIndex)})"},
            duration = $duration.milliseconds,
            scale = $scale,
            numbers = $numbers,
            sizeMode = MorphSizeMode.${sizeMode.name},
        )
        """,
    )
}

private fun easingName(i: Int) = when (i) {
    0 -> "Default"; 1 -> "EaseOutQuint"; 2 -> "EaseInOutCubic"; 3 -> "EaseOutBack"; 4 -> "Material"; else -> "Linear"
}
