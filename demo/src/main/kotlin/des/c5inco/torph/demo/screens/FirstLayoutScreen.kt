package des.c5inco.torph.demo.screens

import android.os.Trace
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import des.c5inco.torph.compose.TextMorph
import des.c5inco.torph.compose.TextMorphDiagnostics
import des.c5inco.torph.core.Segmentation

/** Fresh states on every launch, optionally followed by one immediate update; no warm text-change loop. */
@Composable
fun FirstLayoutScreen(mode: String, count: Int, repeated: Boolean, log: Boolean) {
    TextMorphDiagnostics.traceSections = true
    TextMorphDiagnostics.forceEagerPreparation = !mode.contains("Lazy")
    TextMorphDiagnostics.logTimings = log
    val markers = remember { FirstLayoutMarkers() }
    var changed by remember { mutableStateOf(false) }
    if (mode.contains("Change")) LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        changed = true
    }
    val labels = if (mode.endsWith("Bidi")) listOf(
        "שלום 123 (abc)", "שלום (456) abc", "abc \u2067שלום ١٢٣\u2069 xyz", "שלום (789) abc (xyz)",
        "abc (שלום) xyz", "שלום 234 (def)", "שלום (567) ghi", "jkl \u2067שלום ٤٥٦\u2069 mno",
    ) else listOf("12,345", "67.89%", "1,234 steps", "Active minutes", "Distance today", "Calories burned", "Weekly average", "Personal best") +
        if (mode.contains("Unique")) listOf("Goal progress", "Elevation gained", "Resting pulse", "Workout time", "Lap count", "5,678", "90.12%", "3,456 floors") else emptyList()
    val updated = if (mode.endsWith("Bidi")) listOf(
        "שלום 234 (abc)", "שלום (567) abc", "abc \u2067שלום ٢٣٤\u2069 xyz", "שלום (890) abc (xyz)",
        "abc (שלום) uvw", "שלום 345 (def)", "שלום (678) ghi", "jkl \u2067שלום ٥٦٧\u2069 mno",
    ) else listOf("23,456", "78.90%", "2,345 steps", "Active hours", "Distance yesterday", "Calories consumed", "Monthly average", "Season best")
    Column(Modifier.semantics { contentDescription = if (changed) "first-update-complete:$mode:$count" else "first-layout-ready:$mode:$count" }.layout { measurable, constraints ->
        val section = when {
            changed && !markers.updated -> "Labels:firstUpdate"
            !changed && !markers.initial -> "Labels:firstMeasure"
            else -> null
        }
        if (section != null) Trace.beginSection(section)
        val placeable = try { measurable.measure(constraints) } finally {
            if (section != null) Trace.endSection()
        }
        if (changed) markers.updated = true else markers.initial = true
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }) {
        repeat(count) { i ->
            val text = if (changed) updated[i % updated.size] else if (repeated) "12,345" else labels[i % labels.size]
            val style = TextStyle(fontSize = 18.sp)
            if (mode.startsWith("morph")) TextMorph(text, style = style, segmentation = if (mode.startsWith("morphAuto")) Segmentation.AUTO else Segmentation.GRAPHEME)
            else Text(text, style = style)
        }
    }
}

private class FirstLayoutMarkers(var initial: Boolean = false, var updated: Boolean = false)
