package des.c5inco.torph.demo.screens

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
import des.c5inco.torph.compose.TextMorph
import des.c5inco.torph.compose.TextMorphDiagnostics
import des.c5inco.torph.compose.rememberTextMorphState
import des.c5inco.torph.core.Segmentation
import des.c5inco.torph.demo.Caption
import des.c5inco.torph.demo.Choice
import des.c5inco.torph.demo.CodeBlock
import des.c5inco.torph.demo.Gap
import des.c5inco.torph.demo.DemoType
import des.c5inco.torph.demo.SectionTitle
import des.c5inco.torph.demo.Stage
import des.c5inco.torph.demo.ToggleRow
import des.c5inco.torph.demo.cycling
import kotlin.random.Random

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

/** [text] with the word at roughly [fraction] of the way through swapped for a different one. */
private fun withEditedWord(text: String, fraction: Float, seed: Int): String {
    var start = (text.length * fraction).toInt()
    while (start > 0 && text[start - 1] != ' ') start--
    var end = start
    while (end < text.length && text[end] != ' ') end++
    val current = text.substring(start, end)
    val r = Random(seed)
    var replacement: String
    do replacement = words[r.nextInt(words.size)] while (replacement == current)
    return text.substring(0, start) + replacement + text.substring(end)
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
    // Replace swaps in unrelated text; edit changes one word of the same paragraph, 40-80% of the
    // way through, so the lines before it are unchanged.
    var edit by rememberSaveable { mutableStateOf(false) }
    val texts = remember(size, edit) {
        if (edit) {
            val base = lorem(size, 1)
            listOf(0.4f, 0.6f, 0.8f).mapIndexed { i, f -> withEditedWord(base, f, i) }
        } else {
            listOf(lorem(size, 1), lorem(size, 2), lorem(size, 3))
        }
    }
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
        fontFamily = DemoType.geistMono,
        style = MaterialTheme.typography.bodyMedium,
    )
    Gap(8)
    Choice(listOf(50, 200, 1000), size, { "$it chars" }) { size = it }
    Gap(4)
    Choice(Segmentation.entries, mode, { it.name }) { mode = it }
    Gap(4)
    Choice(listOf(false, true), edit, { if (it) "Edit one word" else "Replace text" }) { edit = it }
    ToggleRow("cycle every 1.5 s", running) { running = it }
    Caption("AUTO morphs text with spaces word by word, as torph does; GRAPHEME morphs every character. Above 300 segments any mode falls back to words (maxSegments).")
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
