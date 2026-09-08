package io.torph.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.constrain
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.torph.core.DiffResult
import io.torph.core.Segment
import io.torph.core.SegmentKind
import io.torph.core.SegmentOptions
import io.torph.core.Segmentation
import io.torph.core.Segmenter
import io.torph.core.diffSegments
import io.torph.core.segmentText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
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

/** One drawn segment with its animatables. Mutated only from the layout pass and the animation coroutines. */
internal class LiveSegment(
    val id: Long,
    var segment: Segment,
    var layout: TextLayoutResult,
    var target: Offset,
    var cell: Rect,
    initialOffset: Offset,
    initialAlpha: Float,
    initialScale: Float,
) {
    var offset = Animatable(initialOffset, Offset.VectorConverter)
    var alpha = Animatable(initialAlpha)
    var scale = Animatable(initialScale)
    var exiting = false
    var entering = false
    /** Clip rect for rolling digits, null otherwise. */
    var clip: Rect? = null
    /** Odometer strip (old digit ... new digit) while a digit rolls; drawn instead of [layout]. */
    var strip: List<TextLayoutResult>? = null
    /** Index into [strip] (fractional) of the digit currently at the target position. */
    var stripProgress: Animatable<Float, AnimationVector1D>? = null
    var stripRoll: Int = 0
    var job: Job? = null
    /** Owns the strip roll; independent of [job] so a retarget never freezes a roll in progress. */
    var stripJob: Job? = null
    val width: Float get() = layout.size.width.toFloat()
    val height: Float get() = layout.size.height.toFloat()
}

/**
 * Owns the segment list, animatables and layout cache behind [TextMorph]. Create with
 * [rememberTextMorphState] and attach with [Modifier.textMorph] for custom containers.
 */
public class TextMorphState internal constructor(
    /** The measurer used for every layout; exposed for debug overlays and tests. */
    public val textMeasurer: TextMeasurer,
    private val scope: CoroutineScope,
    private val segmenter: Segmenter,
) {
    /** Target text. Setting it (from composition) schedules a morph in the next layout pass. */
    public var text: String by mutableStateOf("")

    /** Resolved style, including colour. Changing it re-measures without morphing. */
    public var style: TextStyle by mutableStateOf(TextStyle.Default)

    public var options: MorphOptions by mutableStateOf(MorphOptions())

    public var onAnimationStart: (() -> Unit)? = null
    public var onAnimationComplete: (() -> Unit)? = null
    public var onAnimationCancel: (() -> Unit)? = null

    /** True between `onAnimationStart` and the matching complete/cancel. */
    public var isAnimating: Boolean by mutableStateOf(false)
        private set

    /** Current segments of [text] with resolved ids; the `old` side of the next diff. */
    public var segments: List<Segment> = emptyList()
        private set

    /** Most recent full-string layout, or null before the first measure. */
    public var layoutResult: TextLayoutResult? = null
        private set

    /** Number of live (drawn) segments, including ones still exiting. */
    public val liveCount: Int get() = live.size

    internal val live = mutableStateListOf<LiveSegment>()
    internal val sizeAnim = Animatable(Size.Zero, Size.VectorConverter)
    private var sizeTarget = Size.Zero
    private var snapSizeUntilAnimated = true
    private var nextId = 0L
    private var generation = 0
    private var lastKey: LayoutKey? = null
    private var layoutCache = HashMap<String, TextLayoutResult>()
    private var cacheStyle: TextStyle? = null

    private data class LayoutKey(
        val text: String,
        val style: TextStyle,
        val maxWidth: Int,
        val direction: LayoutDirection,
        val segmentation: Segmentation,
        val numbers: Boolean,
        val locale: Locale,
        val maxSegments: Int,
    )

    // region layout pass

    /** Called from the layout modifier. Applies pending text changes and returns the size to occupy. */
    internal fun measure(constraints: Constraints, direction: LayoutDirection): IntSize {
        val opts = options
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val key = LayoutKey(text, style, maxWidth, direction, opts.segmentation, opts.numbers, opts.locale, opts.maxSegments)
        val prev = lastKey
        if (key != prev) {
            lastKey = key
            val textChanged = prev != null && prev.text != key.text
            apply(key, opts, morph = textChanged && !opts.snap, snapEverything = prev == null || !textChanged || opts.snap)
        }
        val s = if (snapSizeUntilAnimated || opts.sizeMode == MorphSizeMode.Snap || opts.snap) sizeTarget else sizeAnim.value
        return constraints.constrain(IntSize(ceil(s.width).toInt(), ceil(s.height).toInt()))
    }

    private fun apply(key: LayoutKey, opts: MorphOptions, morph: Boolean, snapEverything: Boolean) {
        val full = textMeasurer.measure(
            text = AnnotatedString(key.text),
            style = key.style,
            softWrap = true,
            constraints = if (key.maxWidth == Constraints.Infinity) Constraints() else Constraints(maxWidth = key.maxWidth),
        )
        layoutResult = full
        if (cacheStyle != key.style) { layoutCache.clear(); cacheStyle = key.style }
        val newSize = Size(full.size.width.toFloat(), full.size.height.toFloat())
        val segOptions = SegmentOptions(opts.segmentation, opts.numbers, opts.cursorIndex, opts.maxSegments)

        if (!morph) {
            // Initial layout, re-wrap without a text change, or snapping: rebuild everything in place.
            if (lastTextForSegments != key.text || segments.isEmpty() && key.text.isNotEmpty()) {
                val diff = if (snapEverything && segments.isNotEmpty() && opts.snap) {
                    diffSegments(segments, segmentText(key.text, opts.locale, segmenter, segOptions, nextId), opts.locale)
                } else null
                segments = diff?.segments ?: segmentText(key.text, opts.locale, segmenter, segOptions, nextId)
                nextId = diff?.nextId ?: ((segments.maxOfOrNull { it.id } ?: (nextId - 1)) + 1)
                lastTextForSegments = key.text
                if (diff != null) fireSnapCallbacks(diff)
            }
            cancelAll()
            live.clear()
            for (s in segments) {
                val (target, cell) = place(s, full)
                live.add(LiveSegment(s.id, s, segmentLayout(s), target, cell, target, 1f, 1f))
            }
            sizeTarget = newSize
            snapSizeUntilAnimated = true
            scope.launch { sizeAnim.snapTo(newSize) }
            return
        }

        val diff = diffSegments(
            segments,
            segmentText(key.text, opts.locale, segmenter, segOptions, nextId),
            opts.locale,
        )
        segments = diff.segments
        nextId = diff.nextId
        lastTextForSegments = key.text
        if (diff.isEmpty && diff.persist.all { it.first.index == it.second.index }) {
            // Same segments, same positions (e.g. only trailing whitespace measured differently): retarget silently.
        }
        startMorph(diff, full, newSize, opts)
    }

    private var lastTextForSegments: String? = null

    private fun fireSnapCallbacks(diff: DiffResult) {
        if (diff.isEmpty) return
        scope.launch {
            onAnimationStart?.invoke()
            onAnimationComplete?.invoke()
        }
    }

    private fun startMorph(diff: DiffResult, full: TextLayoutResult, newSize: Size, opts: MorphOptions) {
        val gen = ++generation
        val jobs = ArrayList<Job>(diff.persist.size + diff.enter.size + diff.exit.size + 1)
        val byId = HashMap<Long, LiveSegment>(live.size)
        for (ls in live) if (!ls.exiting) byId[ls.id] = ls

        val offsetSpec = opts.ease.toSpec<Offset>(opts.duration, Offset.VisibilityThreshold)
        val floatSpec = opts.ease.toSpec<Float>(opts.duration, 0.001f)
        val sizeSpec = opts.ease.toSpec<Size>(opts.duration, Size.VisibilityThreshold)
        val minScale = if (opts.scale) 0.5f else 1f

        // Persisting segments glide to their new rect.
        for ((old, new) in diff.persist) {
            val ls = byId[old.id]
            val (target, cell) = place(new, full)
            if (ls == null) {
                live.add(LiveSegment(new.id, new, segmentLayout(new), target, cell, target, 1f, 1f))
                continue
            }
            ls.segment = new
            ls.target = target
            ls.cell = cell
            ls.clip = null
            ls.job?.cancel()
            ls.stripJob?.takeIf { it.isActive }?.let { jobs.add(it) }
            val wasEntering = ls.entering && ls.strip == null
            if (ls.strip == null) ls.entering = false
            if (ls.offset.value != target || ls.alpha.value != 1f || ls.scale.value != 1f) {
                ls.job = scope.launch {
                    val a = launch { ls.offset.animateTo(target, offsetSpec) }
                    val b = if (wasEntering || ls.alpha.value != 1f) launch { ls.alpha.animateTo(1f, floatSpec) } else null
                    val c = if (wasEntering || ls.scale.value != 1f) launch { ls.scale.animateTo(1f, floatSpec) } else null
                    a.join(); b?.join(); c?.join()
                }.also { jobs.add(it) }
            }
        }

        // Rolling digits: an exit and an enter at the same place value become one odometer strip
        // (old digit, every intermediate digit, new digit) that scrolls inside the digit's cell.
        val rollExits = HashMap<Long, Segment>()
        if (opts.numbers) {
            for (old in diff.exit) if (old.kind == SegmentKind.DIGIT && old.roll != 0 && old.group != null && old.place != null) {
                rollExits[rollKey(old.group!!, old.place!!)] = old
            }
        }
        val claimed = HashSet<Long>()

        // Entering segments appear at their target, faded and (optionally) shrunk; digits roll in.
        for (new in diff.enter) {
            val (target, cell) = place(new, full)
            val layout = segmentLayout(new)
            val roll = if (opts.numbers && new.kind == SegmentKind.DIGIT) new.roll else 0
            val from = if (roll != 0 && new.group != null && new.place != null) rollExits[rollKey(new.group!!, new.place!!)] else null
            if (from != null && roll != 0) {
                claimed.add(from.id)
                val previous = byId[from.id]
                val strip = digitStrip(from.text, new.text, roll)
                val ls = LiveSegment(new.id, new, layout, target, cell, target, 1f, 1f)
                ls.entering = true
                ls.clip = cell
                ls.strip = strip
                ls.stripRoll = roll
                // If the old digit was itself mid-roll, continue from where its strip is drawn.
                val prevProgress = previous?.stripProgress
                val prevStrip = previous?.strip
                val initial = if (prevProgress != null && prevStrip != null && previous.stripRoll == roll) {
                    prevProgress.value - (prevStrip.size - 1)
                } else 0f
                val velocity = prevProgress?.velocity ?: 0f
                val progress = Animatable(initial)
                ls.stripProgress = progress
                live.add(ls)
                ls.stripJob = scope.launch {
                    progress.animateTo((strip.size - 1).toFloat(), floatSpec, initialVelocity = velocity)
                    ls.entering = false
                    ls.clip = null
                    ls.strip = null
                    ls.stripProgress = null
                }.also { jobs.add(it) }
                continue
            }
            val start = if (roll != 0) Offset(target.x, target.y + roll * cell.height) else target
            val ls = LiveSegment(new.id, new, layout, target, cell, start, if (roll != 0) 1f else 0f, if (roll != 0) 1f else minScale)
            ls.entering = true
            if (roll != 0) ls.clip = cell
            live.add(ls)
            ls.job = scope.launch {
                val a = if (ls.alpha.value != 1f) launch { ls.alpha.animateTo(1f, floatSpec) } else null
                val b = if (start != target) launch { ls.offset.animateTo(target, offsetSpec) } else null
                val c = if (ls.scale.value != 1f) launch { ls.scale.animateTo(1f, floatSpec) } else null
                a?.join(); b?.join(); c?.join()
                ls.entering = false
                ls.clip = null
            }.also { jobs.add(it) }
        }

        // Exiting segments fade (digits roll) out, then leave the list.
        for (old in diff.exit) {
            val ls = byId[old.id] ?: continue
            ls.job?.cancel()
            ls.stripJob?.cancel()
            if (old.id in claimed) {
                // Its strip successor draws the old digit from here on.
                live.remove(ls)
                continue
            }
            ls.exiting = true
            ls.entering = false
            ls.strip = null
            ls.stripProgress = null
            val roll = if (opts.numbers && old.kind == SegmentKind.DIGIT) old.roll else 0
            val rollTarget = if (roll != 0) {
                ls.clip = ls.cell
                Offset(ls.offset.value.x, ls.offset.value.y - roll * ls.cell.height)
            } else null
            ls.job = scope.launch {
                val a = if (rollTarget == null) launch { ls.alpha.animateTo(0f, floatSpec) } else null
                val b = if (rollTarget != null) launch { ls.offset.animateTo(rollTarget, offsetSpec) } else null
                val c = if (minScale != 1f && rollTarget == null) launch { ls.scale.animateTo(minScale, floatSpec) } else null
                a?.join(); b?.join(); c?.join()
                live.remove(ls)
            }.also { jobs.add(it) }
        }

        // Container size.
        sizeTarget = newSize
        if (opts.sizeMode == MorphSizeMode.Snap) {
            snapSizeUntilAnimated = true
            jobs.add(scope.launch { sizeAnim.snapTo(newSize) })
        } else {
            if (snapSizeUntilAnimated) {
                // First animated change: start from the size we've been reporting.
                val from = sizeAnim.value
                snapSizeUntilAnimated = false
                jobs.add(scope.launch {
                    if (from != sizeTarget) sizeAnim.animateTo(newSize, sizeSpec) else sizeAnim.snapTo(newSize)
                })
            } else {
                jobs.add(scope.launch { sizeAnim.animateTo(newSize, sizeSpec) })
            }
        }

        // Exactly one of complete/cancel per morph.
        scope.launch {
            isAnimating = true
            onAnimationStart?.invoke()
            jobs.joinAll()
            if (generation == gen) {
                isAnimating = false
                onAnimationComplete?.invoke()
            } else {
                onAnimationCancel?.invoke()
            }
        }
    }

    private fun cancelAll() {
        for (ls in live) { ls.job?.cancel(); ls.stripJob?.cancel() }
        if (isAnimating) {
            generation++
            isAnimating = false
        }
    }

    // endregion

    // region geometry

    /** Top-left draw offset and the line cell (for clipping rolls) of [s] inside [full]. */
    private fun place(s: Segment, full: TextLayoutResult): Pair<Offset, Rect> {
        val textLen = full.layoutInput.text.length
        if (textLen == 0) return Offset.Zero to Rect.Zero
        val start = s.index.coerceIn(0, textLen - 1)
        val line = full.getLineForOffset(start)
        val lineTop = full.getLineTop(line)
        val lineBottom = full.getLineBottom(line)
        val baseline = full.getLineBaseline(line)
        if (s.kind == SegmentKind.NEWLINE) {
            val r = full.getCursorRect(start)
            return Offset(r.left, lineTop) to Rect(r.left, lineTop, r.left, lineBottom)
        }
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        val end = s.end.coerceAtMost(textLen)
        var i = start
        while (i < end) {
            val box = full.getBoundingBox(i)
            if (box.width > 0f || box.left != 0f) {
                if (box.left < left) left = box.left
                if (box.right > right) right = box.right
            }
            i++
        }
        if (left == Float.POSITIVE_INFINITY) {
            val r = full.getCursorRect(start)
            left = r.left; right = r.left
        }
        val seg = segmentLayout(s)
        val y = baseline - seg.firstBaseline
        // Centre the segment's own layout inside the measured box so kerning differences split evenly.
        val x = left + ((right - left) - seg.size.width) / 2f
        return Offset(x, y) to Rect(left, lineTop, right, lineBottom)
    }

    private fun segmentLayout(s: Segment): TextLayoutResult = layoutCache.getOrPut(s.text) {
        textMeasurer.measure(
            text = AnnotatedString(if (s.kind == SegmentKind.NEWLINE) "" else s.text),
            style = style,
            softWrap = false,
            maxLines = 1,
        )
    }

    // endregion

    /** Layouts for every digit from [from] to [to] moving in [roll] direction (wrapping 9 -> 0), same script as [to]. */
    private fun digitStrip(from: String, to: String, roll: Int): List<TextLayoutResult> {
        val a = Character.getNumericValue(from[0]).coerceIn(0, 9)
        val b = Character.getNumericValue(to[0]).coerceIn(0, 9)
        val zero = to[0] - b
        val steps = if (roll > 0) (b - a + 10) % 10 else (a - b + 10) % 10
        val out = ArrayList<TextLayoutResult>(steps + 1)
        for (k in 0..steps) {
            val d = if (roll > 0) (a + k) % 10 else (a - k + 10) % 10
            val ch = (zero + d).toString()
            out.add(layoutCache.getOrPut(ch) {
                textMeasurer.measure(AnnotatedString(ch), style, softWrap = false, maxLines = 1)
            })
        }
        return out
    }

    private fun rollKey(group: Int, place: Int): Long = (group.toLong() shl 32) or (place.toLong() and 0xFFFFFFFFL)
}

/**
 * Creates and remembers a [TextMorphState]. A new state is created when the text measurer's
 * environment (density, font resolver, layout direction) changes.
 */
@Composable
public fun rememberTextMorphState(): TextMorphState {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val scope = rememberCoroutineScope()
    val segmenter = remember { IcuSegmenter() }
    return remember(measurer) { TextMorphState(measurer, scope, segmenter) }
}

internal fun Float.roundPx(): Int = roundToInt()
