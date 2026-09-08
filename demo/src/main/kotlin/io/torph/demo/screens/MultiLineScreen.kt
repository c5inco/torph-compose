package io.torph.demo.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.torph.compose.TextMorph
import io.torph.demo.Caption
import io.torph.demo.CodeBlock
import io.torph.demo.Gap
import io.torph.demo.LabeledSlider
import io.torph.demo.Stage
import kotlin.time.Duration.Companion.milliseconds

private const val A = "Torph measures the whole target string once, then reads every segment's position out of the layout. Line breaks and kerning come for free."
private const val B = "Torph measures the target string once and reads segment positions from the layout, so wrapped lines re-flow correctly.\nEven across paragraphs."

@Composable
fun MultiLineScreen() {
    var second by rememberSaveable { mutableStateOf(false) }
    var widthFraction by rememberSaveable { mutableFloatStateOf(1f) }
    Caption("Two versions of a paragraph with different line breaks. Shrink the width to see words move between lines.")
    Gap(12)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(Modifier.fillMaxWidth(widthFraction)) {
            Stage(200) {
                TextMorph(
                    text = if (second) B else A,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    duration = 600.milliseconds,
                )
            }
        }
    }
    Gap(12)
    Button(onClick = { second = !second }) { Text("Swap paragraph") }
    LabeledSlider("width", widthFraction, 0.4f..1f, { "${(it * 100).toInt()}%" }) { widthFraction = it }
    CodeBlock(
        """
        // Wrapping follows the incoming constraints, like Text():
        TextMorph(
            text = paragraph,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        """,
    )
}
