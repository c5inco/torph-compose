package io.torph.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import io.torph.core.SegmentKind

/** Per-frame draw: N cached `drawText` calls, no measurement, no string allocation. */
internal fun TextMorphState.draw(scope: DrawScope) = with(scope) {
    val list = live
    val debug = options.debug
    var i = 0
    val n = list.size
    while (i < n) {
        val ls = list[i]
        i++
        if (ls.segment.kind == SegmentKind.NEWLINE) continue
        val alpha = ls.alpha.value
        if (alpha <= 0.001f && !debug) continue
        val offset = ls.offset.value
        val sc = ls.scale.value
        val clip = ls.clip
        val strip = ls.strip
        val progress = ls.stripProgress
        if (strip != null && progress != null) {
            // The strip follows the segment's animated offset, so its clip is derived from it too.
            val h = ls.cell.height
            clipRect(offset.x - ls.width * 0.5f, offset.y - 1f, offset.x + ls.width * 1.5f, offset.y + h + 1f) {
                drawStrip(ls, strip, progress.value, offset, alpha)
            }
        } else if (clip != null) {
            clipRect(clip.left, clip.top - 1f, clip.right, clip.bottom + 1f) { drawSegment(ls, offset, sc, alpha) }
        } else {
            drawSegment(ls, offset, sc, alpha)
        }
        if (debug) drawDebug(textMeasurer, ls, offset)
    }
}

private fun DrawScope.drawSegment(ls: LiveSegment, offset: Offset, sc: Float, alpha: Float) {
    if (sc == 1f) {
        drawText(ls.layout, topLeft = offset, alpha = alpha)
    } else {
        scale(sc, sc, pivot = Offset(offset.x + ls.width / 2f, offset.y + ls.height / 2f)) {
            drawText(ls.layout, topLeft = offset, alpha = alpha)
        }
    }
}

/** Odometer: digit k sits k - progress cells away from the target, in the roll direction. */
private fun DrawScope.drawStrip(ls: LiveSegment, strip: List<TextLayoutResult>, progress: Float, offset: Offset, alpha: Float) {
    val h = ls.cell.height
    val baseY = offset.y
    val centerX = offset.x + ls.width / 2f
    val first = (progress - 1f).toInt().coerceAtLeast(0)
    val last = (progress + 2f).toInt().coerceAtMost(strip.size - 1)
    var k = first
    while (k <= last) {
        val layout = strip[k]
        val y = baseY + ls.stripRoll * (k - progress) * h
        drawText(layout, topLeft = Offset(centerX - layout.size.width / 2f, y), alpha = alpha)
        k++
    }
}

private val debugStyle = TextStyle(fontSize = 8.sp)
private val enterColor = Color(0xFF2E7D32)
private val exitColor = Color(0xFFC62828)
private val persistColor = Color(0xFF1565C0)
private val rollColor = Color(0xFFEF6C00)

private fun DrawScope.drawDebug(textMeasurer: TextMeasurer, ls: LiveSegment, offset: Offset) {
    val color = when {
        ls.exiting -> exitColor
        ls.entering -> enterColor
        ls.segment.kind == SegmentKind.DIGIT -> rollColor
        else -> persistColor
    }
    drawRect(color, topLeft = offset, size = androidx.compose.ui.geometry.Size(ls.width, ls.height), style = Stroke(1f), alpha = 0.9f)
    val label = textMeasurer.measure(AnnotatedString(debugLabel(ls)), debugStyle, maxLines = 1)
    drawRect(color, topLeft = Offset(offset.x, offset.y - label.size.height), size = androidx.compose.ui.geometry.Size(label.size.width.toFloat(), label.size.height.toFloat()), alpha = 0.85f)
    drawText(label, color = Color.White, topLeft = Offset(offset.x, offset.y - label.size.height))
}

private fun debugLabel(ls: LiveSegment): String {
    val s = ls.segment
    return buildString {
        append(s.id)
        if (s.place != null) append(" p").append(s.place)
    }
}
