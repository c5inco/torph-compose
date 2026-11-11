package des.c5inco.torph.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

internal fun isDesktopReducedMotion(): Boolean {
    if (GraphicsEnvironment.isHeadless()) return false
    return try {
        val gnomeAnimations = Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Net/EnableAnimations")
        gnomeAnimations == false || gnomeAnimations == 0
    } catch (_: Exception) {
        false
    }
}

/**
 * Reads the desktop reduced-motion setting when exposed by the toolkit (such as
 * `gnome.Net/EnableAnimations` on Linux), defaulting to `false` elsewhere; call [TextMorph] with
 * `respectReducedMotion = false` or `disabled = true` to override it explicitly.
 */
@Composable
public actual fun rememberReducedMotion(): Boolean = remember { isDesktopReducedMotion() }
