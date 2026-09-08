package io.torph.compose

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import io.torph.core.SpringParams
import kotlin.time.Duration

/** How segments travel. Mirrors torph's `ease` option: a timing curve or a physical spring. */
public sealed interface MorphEase {
    /** Duration-based easing; the duration comes from `TextMorph(duration = ...)`. */
    public data class Curve(val easing: Easing = MorphEasings.Default) : MorphEase

    /**
     * Physical spring in torph's units (stiffness, damping coefficient, mass). Duration is ignored.
     * `precision` is the settle threshold in pixels.
     */
    public data class Spring(
        val stiffness: Float = 100f,
        val damping: Float = 10f,
        val mass: Float = 1f,
        val precision: Float = 0.001f,
    ) : MorphEase {
        /** Same parameters as a core [SpringParams], for settle-time estimates. */
        public val params: SpringParams get() = SpringParams(stiffness, damping, mass, precision)
    }
}

/** Curves that ship with torph's site and easing.dev, expressed as Compose [Easing]s. */
public object MorphEasings {
    /** torph's default: `cubic-bezier(0.19, 1, 0.22, 1)` (ease-out-expo). */
    public val Default: Easing = CubicBezierEasing(0.19f, 1f, 0.22f, 1f)
    public val EaseOutQuint: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    public val EaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    public val EaseOutBack: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    public val Material: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    public val Linear: Easing = LinearEasing

    /** Named presets for pickers. */
    public val presets: List<Pair<String, Easing>> = listOf(
        "ease-out-expo (default)" to Default,
        "ease-out-quint" to EaseOutQuint,
        "ease-in-out-cubic" to EaseInOutCubic,
        "ease-out-back" to EaseOutBack,
        "material emphasized" to Material,
        "linear" to Linear,
    )
}

private val bezierRegex = Regex("""cubic-bezier\(\s*([-\d.eE]+)\s*,\s*([-\d.eE]+)\s*,\s*([-\d.eE]+)\s*,\s*([-\d.eE]+)\s*\)""")

/**
 * Parses a CSS `cubic-bezier(x1, y1, x2, y2)` string (or the keywords `linear`, `ease`, `ease-in`,
 * `ease-out`, `ease-in-out`) into an [Easing], so torph configs port verbatim.
 */
public fun cssCubicBezier(css: String): Easing {
    val s = css.trim()
    when (s.lowercase()) {
        "linear" -> return LinearEasing
        "ease" -> return CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
        "ease-in" -> return CubicBezierEasing(0.42f, 0f, 1f, 1f)
        "ease-out" -> return CubicBezierEasing(0f, 0f, 0.58f, 1f)
        "ease-in-out" -> return CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    }
    val m = bezierRegex.matchEntire(s) ?: throw IllegalArgumentException("Not a cubic-bezier: $css")
    val (a, b, c, d) = m.destructured
    return CubicBezierEasing(a.toFloat(), b.toFloat(), c.toFloat(), d.toFloat())
}

/** Builds the Compose spec for one property. Spring visibility thresholds follow [precision]. */
internal fun <T> MorphEase.toSpec(duration: Duration, threshold: T?): AnimationSpec<T> = when (this) {
    is MorphEase.Curve -> tween(duration.inWholeMilliseconds.toInt().coerceAtLeast(0), easing = easing)
    is MorphEase.Spring -> spring(
        dampingRatio = params.dampingRatio.coerceAtLeast(0.01f),
        stiffness = (stiffness / mass).coerceAtLeast(1f),
        visibilityThreshold = threshold,
    )
}
