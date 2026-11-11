package des.c5inco.torph.compose

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import java.text.Bidi

/** Single-line selection bounds from platform bidi levels and native caret coordinates. */
internal class BidiRangeBounds {
    private var layout: TextLayoutResult? = null
    private var eligible = false
    private var levels = IntArray(0)
    private var top = 0f
    private var bottom = 0f
    private var leading = FloatArray(0)
    private var trailing = FloatArray(0)
    private var status = ByteArray(0)
    private var baseLevel = 0

    internal val usesFastGeometry: Boolean get() = eligible

    internal var pathQueries: Int = 0
        private set
    internal var caretQueries: Int = 0
        private set

    fun bounds(full: TextLayoutResult, start: Int, end: Int): Rect {
        if (layout !== full) prepare(full)
        if (eligible && start < end) {
            val a = position(full, start, downstream = true)
            val b = position(full, end, downstream = false)
            var left = minOf(a, b)
            var right = maxOf(a, b)
            // Only visual-run boundaries contribute further selection rectangles. Interior
            // glyph carets are not extrema of the native selection path.
            for (offset in start + 1 until end) if (levels[offset - 1] != levels[offset]) {
                val up = position(full, offset, downstream = false)
                val down = position(full, offset, downstream = true)
                left = minOf(left, up, down)
                right = maxOf(right, up, down)
            }
            return Rect(left, top, right, bottom)
        }
        pathQueries++
        return full.getPathForRange(start, end).getBounds()
    }

    private fun prepare(full: TextLayoutResult) {
        layout = full
        eligible = false
        pathQueries = 0
        caretQueries = 0
        val text = full.layoutInput.text.text
        // Keep native selection for line resets, non-monotonic spacing and replacement-span
        // candidates. These can change the relation between source offsets and visual runs.
        // Skiko's SkiaParagraph.getHorizontalPosition also ignores usePrimaryDirection when the
        // preceding run matches the paragraph direction (such as level-2 LTR digits before level-1
        // RTL spaces), so Desktop uses native getPathForRange directly.
        if (!supportsSplitCaretHorizontalPosition || full.lineCount != 1 || full.layoutInput.style.letterSpacing.value < 0f || text.isEmpty() ||
            text.any { it.isSurrogate() || it == '\u200D' || it == '\uFE0E' || it == '\uFE0F' || it == '\u20E3' }) return
        baseLevel = if (full.getParagraphDirection(0) == ResolvedTextDirection.Rtl) 1 else 0
        val resolved = Bidi(text, if (baseLevel == 1) Bidi.DIRECTION_RIGHT_TO_LEFT else Bidi.DIRECTION_LEFT_TO_RIGHT)
        if (status.size < text.length + 1) {
            status = ByteArray(text.length + 1)
            leading = FloatArray(text.length + 1)
            trailing = FloatArray(text.length + 1)
            levels = IntArray(text.length)
        } else status.fill(0, 0, text.length + 1)
        // Both APIs use the platform bidi engine; retain native geometry if their resolved
        // directions disagree (for example after source-offset transformation).
        for (i in text.indices) {
            val level = resolved.getLevelAt(i)
            levels[i] = level
            if ((level and 1 != 0) != (full.getBidiRunDirection(i) == ResolvedTextDirection.Rtl)) return
        }
        top = full.getLineTop(0)
        bottom = full.getLineBottom(0)
        eligible = true
    }

    private fun position(full: TextLayoutResult, offset: Int, downstream: Boolean): Float {
        val bit = if (downstream) 1 else 2
        if ((status[offset].toInt() and bit) == 0) {
            val before = if (offset == 0) baseLevel else levels[offset - 1]
            val after = if (offset == full.layoutInput.text.length) baseLevel else levels[offset]
            // Android's primary caret belongs to the lower embedding level. At equal levels
            // the two edges coincide; at a split caret select the requested logical side.
            val primary = if (downstream) after <= before else before <= after
            val value = full.getHorizontalPosition(offset, primary)
            caretQueries++
            if (before == after) {
                leading[offset] = value
                trailing[offset] = value
                status[offset] = 3
            } else if (downstream) {
                leading[offset] = value
                status[offset] = (status[offset].toInt() or 1).toByte()
            } else {
                trailing[offset] = value
                status[offset] = (status[offset].toInt() or 2).toByte()
            }
        }
        return if (downstream) leading[offset] else trailing[offset]
    }
}

internal expect val supportsSplitCaretHorizontalPosition: Boolean
