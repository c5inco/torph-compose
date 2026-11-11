package des.c5inco.torph.compose

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange

/**
 * Skiko's `SkiaParagraph.fillBoundingBoxes` is currently unimplemented (`CMP-720` — a no-op that
 * prints to stdout and leaves [array] untouched). Populate character boxes via
 * [TextLayoutResult.getBoundingBox], skipping line breaks and trailing surrogates so Skiko's
 * forward-scanning fallback never borrows a box from the following character or line.
 */
internal actual fun fillCharacterBoundingBoxes(
    full: TextLayoutResult,
    range: TextRange,
    array: FloatArray,
    arrayStart: Int,
) {
    val text = full.layoutInput.text.text
    val start = range.min
    val end = range.max
    var out = arrayStart
    for (offset in start until end) {
        val ch = text[offset]
        if (ch == '\n' || ch == '\r' ||
            (ch.isLowSurrogate() && offset > 0 && text[offset - 1].isHighSurrogate())
        ) {
            array[out] = 0f
            array[out + 1] = 0f
            array[out + 2] = 0f
            array[out + 3] = 0f
        } else {
            val line = full.getLineForOffset(offset)
            val box = full.getBoundingBox(offset)
            val lineTop = full.getLineTop(line)
            val lineBottom = full.getLineBottom(line)
            // Zero-width marks at a line end can scan forward onto the next line in SkiaParagraph;
            // discard a box whose vertical span belongs to another line.
            if (box.bottom <= lineTop || box.top >= lineBottom) {
                array[out] = 0f
                array[out + 1] = 0f
                array[out + 2] = 0f
                array[out + 3] = 0f
            } else {
                array[out] = box.left
                array[out + 1] = box.top
                array[out + 2] = box.right
                array[out + 3] = box.bottom
            }
        }
        out += 4
    }
}

internal actual val supportsSplitCaretHorizontalPosition: Boolean = false
