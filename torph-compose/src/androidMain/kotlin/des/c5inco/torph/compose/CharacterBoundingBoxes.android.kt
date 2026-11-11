package des.c5inco.torph.compose

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange

internal actual fun fillCharacterBoundingBoxes(
    full: TextLayoutResult,
    range: TextRange,
    array: FloatArray,
    arrayStart: Int,
) {
    full.multiParagraph.fillBoundingBoxes(range, array, arrayStart)
}

internal actual val supportsSplitCaretHorizontalPosition: Boolean = true
