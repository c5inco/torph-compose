package io.torph.demo.screens

import android.view.Choreographer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import io.torph.compose.TextMorph
import io.torph.compose.rememberTextMorphState
import io.torph.core.Segmentation
import io.torph.demo.Caption
import io.torph.demo.Choice
import io.torph.demo.CodeBlock
import io.torph.demo.Gap
import io.torph.demo.SectionTitle
import io.torph.demo.Stage
import io.torph.demo.ToggleRow
import io.torph.demo.cycling
import kotlin.random.Random
import io.torph.compose.TextMorphDiagnostics

private val words = ("lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua " +
    "ut enim ad minim veniam quis nostrud exercitation ullamco laboris nisi aliquip ex ea commodo consequat").split(" ")

private fun lorem(chars: Int, seed: Int): String {
    val r = Random(seed)
    val sb = StringBuilder()
    while (sb.length < chars) {
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(words[r.nextInt(words.size)])
        if (r.nextInt(9) == 0) sb.append(' ').append(r.nextInt(1000))
    }
    return sb.substring(0, chars)
}

@Composable
fun PerfScreen() {
    // Log a per-change timing breakdown to logcat (adb logcat -s TextMorphPerf) while this
    // screen is open. Off elsewhere so it never distorts the other screens.
    DisposableEffect(Unit) {
        TextMorphDiagnostics.logTimings = true
        onDispose { TextMorphDiagnostics.logTimings = false }
    }
    var size by rememberSaveable { mutableIntStateOf(200) }
    var running by rememberSaveable { mutableStateOf(true) }
    var mode by rememberSaveable { mutableStateOf(Segmentation.AUTO) }
    val texts = remember(size) { listOf(lorem(size, 1), lorem(size, 2), lorem(size, 3)) }
    val text by cycling(texts, 1500L, running)
    val state = rememberTextMorphState()

    // Frame-time readout from Choreographer: average and worst over a rolling window.
    var avgMs by remember { mutableFloatStateOf(0f) }
    var maxMs by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val choreographer = Choreographer.getInstance()
        val window = FloatArray(60)
        var idx = 0
        var filled = 0
        var last = 0L
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (last != 0L) {
                    val ms = (frameTimeNanos - last) / 1_000_000f
                    window[idx] = ms
                    idx = (idx + 1) % window.size
                    if (filled < window.size) filled++
                    if (idx == 0) {
                        var sum = 0f; var mx = 0f
                        for (k in 0 until filled) { sum += window[k]; if (window[k] > mx) mx = window[k] }
                        avgMs = sum / filled
                        maxMs = mx
                    }
                }
                last = frameTimeNanos
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(cb)
        onDispose { choreographer.removeFrameCallback(cb) }
    }

    Text(
        "frame avg %.1f ms · max %.1f ms · live segments %d".format(avgMs, maxMs, state.liveCount),
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodyMedium,
    )
    Gap(8)
    Choice(listOf(50, 200, 1000), size, { "$it chars" }) { size = it }
    Gap(4)
    Choice(Segmentation.entries, mode, { it.name }) { mode = it }
    ToggleRow("cycle every 1.5 s", running) { running = it }
    Caption("Above 300 segments the library falls back to word segmentation automatically (maxSegments); force WORD to compare.")
    SectionTitle("$size characters")
    Stage(if (size > 500) 360 else if (size > 100) 160 else 80) {
        TextMorph(
            text = text,
            state = state,
            segmentation = mode,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    CodeBlock(
        """
        // Per frame: one drawText per live segment, no measurement, no composition.
        TextMorph(text = longText, segmentation = Segmentation.${mode.name})
        """,
    )
}
