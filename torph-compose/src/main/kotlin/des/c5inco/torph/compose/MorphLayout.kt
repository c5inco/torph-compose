package des.c5inco.torph.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

/**
 * Sizes the node to the morphing text (animated per [MorphOptions.sizeMode]) and draws it.
 * Set [TextMorphState.text], [TextMorphState.style] and [TextMorphState.options] from composition;
 * this modifier performs measurement and diffing in the layout pass and reads animatables only in
 * layout and draw, never in composition.
 */
public fun Modifier.textMorph(state: TextMorphState): Modifier = this
    .layout { measurable, constraints ->
        val size = state.measure(constraints, layoutDirection)
        val placeable = measurable.measure(Constraints.fixed(size.width, size.height))
        layout(size.width, size.height) { placeable.place(0, 0) }
    }
    .drawBehind { state.draw(this) }
