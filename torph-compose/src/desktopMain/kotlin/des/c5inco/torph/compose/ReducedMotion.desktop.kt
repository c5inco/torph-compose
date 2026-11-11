package des.c5inco.torph.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

internal fun isDesktopReducedMotion(
    isHeadless: Boolean = GraphicsEnvironment.isHeadless(),
    desktopProperty: (String) -> Any? = { Toolkit.getDefaultToolkit().getDesktopProperty(it) },
): Boolean {
    if (isHeadless) return false
    return try {
        val gtkAnimations = desktopProperty("gnome.Gtk/EnableAnimations")
            ?: desktopProperty("gnome.Net/EnableAnimations")
        gtkAnimations == false || gtkAnimations == 0
    } catch (_: Exception) {
        false
    }
}

/**
 * Reads the desktop reduced-motion setting when exposed by the toolkit (such as
 * `gnome.Gtk/EnableAnimations` or `gnome.Net/EnableAnimations` on Linux), defaulting to `false`
 * elsewhere; call [TextMorph] with `respectReducedMotion = false` or `disabled = true` to override
 * it explicitly.
 */
@Composable
public actual fun rememberReducedMotion(): Boolean = remember { isDesktopReducedMotion() }
