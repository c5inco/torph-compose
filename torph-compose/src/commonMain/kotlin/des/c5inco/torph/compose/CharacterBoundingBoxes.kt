package des.c5inco.torph.compose

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange

/**
 * Fills four floats `(left, top, right, bottom)` per character in [range] into [array] starting at
 * [arrayStart], in [full]'s coordinates.
 */
internal expect fun fillCharacterBoundingBoxes(
    full: TextLayoutResult,
    range: TextRange,
    array: FloatArray,
    arrayStart: Int,
)
