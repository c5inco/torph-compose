package des.c5inco.torph.compose

import des.c5inco.torph.core.Segmentation
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Whether the composable's bounds animate to the new text's size or jump straight to it. */
public enum class MorphSizeMode { Animate, Snap }

/** Everything that tunes a morph, held on [TextMorphState.options]. */
public data class MorphOptions(
    val ease: MorphEase = MorphEase.Curve(),
    val duration: Duration = 400.milliseconds,
    /** Scale entering/exiting segments between 0.5 and 1. */
    val scale: Boolean = true,
    /** Place-value matching and vertical rolling for numbers. */
    val numbers: Boolean = true,
    val locale: Locale = Locale.getDefault(),
    /** Caret position for editable text: the number under the caret is matched by position, not place. */
    val cursorIndex: Int? = null,
    val segmentation: Segmentation = Segmentation.AUTO,
    val sizeMode: MorphSizeMode = MorphSizeMode.Animate,
    /** Snap every change; set by `disabled`, reduced motion, or inspection mode. */
    val snap: Boolean = false,
    /** Draw segment rects, ids and enter/exit colouring. */
    val debug: Boolean = false,
    /** Above this many segments, fall back to word segmentation. */
    val maxSegments: Int = 300,
)
