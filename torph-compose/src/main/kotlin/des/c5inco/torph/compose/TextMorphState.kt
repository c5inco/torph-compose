package des.c5inco.torph.compose

import androidx.compose.animation.core.FloatAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.unit.constrain
import des.c5inco.torph.core.DiffResult
import des.c5inco.torph.core.Segment
import des.c5inco.torph.core.SegmentKind
import des.c5inco.torph.core.SegmentOptions
import des.c5inco.torph.core.Segmentation
import des.c5inco.torph.core.Segmenter
import des.c5inco.torph.core.diffSegments
import des.c5inco.torph.core.segmentText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil
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

/**
 * One drawn segment. All animation state is plain fields ([Channel]s) advanced by the state's frame
 * loop; nothing here is snapshot state, so per-frame reads and writes allocate nothing.
 */
internal class LiveSegment(
    val id: Long,
    var segment: Segment,
    var layout: TextLayoutResult,
    var target: Offset,
    var cell: Rect,
    initial: Offset,
    initialAlpha: Float,
    initialScale: Float,
) {
    val x = Channel(initial.x)
    val y = Channel(initial.y)
    val alpha = Channel(initialAlpha)
    val scale = Channel(initialScale)
    var exiting = false
    var entering = false
    /** Clip rect for a digit sliding in or out of its cell, null otherwise. */
    var clip: Rect? = null
    /** Odometer strip (old digit ... new digit) while a digit rolls; drawn instead of [layout]. */
    var strip: List<TextLayoutResult>? = null
    /** Index into [strip] (fractional) of the digit currently at the target position. */
    val stripProgress = Channel(0f)
    var stripRoll: Int = 0
    val width: Float get() = layout.size.width.toFloat()
    val height: Float get() = layout.size.height.toFloat()

    fun tick(now: Long): Boolean {
        var active = x.tick(now)
        active = y.tick(now) || active
        active = alpha.tick(now) || active
        active = scale.tick(now) || active
        if (stripProgress.tick(now)) {
            active = true
        } else if (strip != null) {
            strip = null
            entering = false
            clip = null
        }
        return active
    }

    val isActive: Boolean get() = x.active || y.active || alpha.active || scale.active || stripProgress.active
}

/**
 * Owns the segment list, animation channels and layout cache behind [TextMorph]. Create with
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

    internal val live = ArrayList<LiveSegment>()

    /** Bumped once per animation frame (and per structural change); the draw lambda reads it. */
    internal val frameTick = mutableIntStateOf(0)

    /** Bumped only while the container size animates; the layout modifier reads it. */
    private val sizeTick = mutableIntStateOf(0)
    private val width = Channel(0f)
    private val height = Channel(0f)

    private var nextId = 0L
    private var lastKey: LayoutKey? = null
    private var lastTextForSegments: String? = null
    private val layoutCache = HashMap<String, TextLayoutResult>()
    private var cacheStyle: TextStyle? = null

    private val timings = MorphTimings()
    private var loop: Job? = null
    private var lastFrameNanos = Channel.UNSET
    private var generationAnimating = false

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
            apply(key, opts, morph = textChanged && !opts.snap)
        }
        sizeTick.intValue // subscribe: re-measure while the size animates
        return constraints.constrain(IntSize(ceil(width.value).toInt(), ceil(height.value).toInt()))
    }

    private fun apply(key: LayoutKey, opts: MorphOptions, morph: Boolean) {
        val diag = TextMorphDiagnostics.logTimings
        if (diag) timings.reset()
        val applyStart = if (diag) System.nanoTime() else 0L
        var mark = applyStart
        val full = textMeasurer.measure(
            text = AnnotatedString(key.text),
            style = key.style,
            softWrap = true,
            constraints = if (key.maxWidth == Constraints.Infinity) Constraints() else Constraints(maxWidth = key.maxWidth),
        )
        if (diag) { val n = System.nanoTime(); timings.measureText = n - mark; mark = n }
        layoutResult = full
        if (cacheStyle != key.style) { layoutCache.clear(); cacheStyle = key.style }
        val newSize = Size(full.size.width.toFloat(), full.size.height.toFloat())
        val segOptions = SegmentOptions(opts.segmentation, opts.numbers, opts.cursorIndex, opts.maxSegments)

        if (!morph) {
            // Initial layout, re-wrap without a text change, or snapping: rebuild everything in place.
            var changed = false
            if (lastTextForSegments != key.text) {
                val diff = if (segments.isNotEmpty() || lastTextForSegments != null) {
                    diffSegments(segments, segmentText(key.text, opts.locale, segmenter, segOptions, nextId), opts.locale)
                } else null
                segments = diff?.segments ?: segmentText(key.text, opts.locale, segmenter, segOptions, nextId)
                nextId = diff?.nextId ?: ((segments.maxOfOrNull { it.id } ?: (nextId - 1)) + 1)
                lastTextForSegments = key.text
                changed = diff != null && !diff.isEmpty
            }
            finishGeneration(cancelled = true)
            live.clear()
            for (s in segments) {
                val (target, cell) = place(s, full)
                live.add(LiveSegment(s.id, s, segmentLayout(s), target, cell, target, 1f, 1f))
            }
            width.snapTo(newSize.width)
            height.snapTo(newSize.height)
            frameTick.intValue++
            if (changed) {
                // Snapped morph: callbacks still fire, in order.
                scope.launch { onAnimationStart?.invoke(); onAnimationComplete?.invoke() }
            }
            return
        }

        val incoming = segmentText(key.text, opts.locale, segmenter, segOptions, nextId)
        if (diag) { val n = System.nanoTime(); timings.segment = n - mark; mark = n }
        val diff = diffSegments(segments, incoming, opts.locale)
        if (diag) { val n = System.nanoTime(); timings.diff = n - mark; mark = n }
        segments = diff.segments
        nextId = diff.nextId
        lastTextForSegments = key.text
        startMorph(diff, full, newSize, opts)
        if (diag) {
            val n = System.nanoTime()
            // Placement and segment measuring happen inside startMorph; the rest is animation setup.
            timings.animation = (n - mark) - timings.place - timings.segmentLayout
            timings.log(
                totalNs = n - applyStart,
                chars = key.text.length,
                segments = diff.segments.size,
                persist = diff.persist.size,
                enter = diff.enter.size,
                exit = diff.exit.size,
            )
        }
    }

    private fun startMorph(diff: DiffResult, full: TextLayoutResult, newSize: Size, opts: MorphOptions) {
        val now = if (loop?.isActive == true) lastFrameNanos else Channel.UNSET
        val moveSpec = opts.ease.toFloatSpec(opts.duration, 0.5f)
        val fadeSpec = opts.ease.toFloatSpec(opts.duration, 0.001f)
        val minScale = if (opts.scale) 0.5f else 1f
        val byId = HashMap<Long, LiveSegment>(live.size)
        for (ls in live) if (!ls.exiting) byId[ls.id] = ls

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
            if (ls.strip == null) { ls.entering = false; ls.clip = null }
            ls.x.animateTo(target.x, moveSpec, now)
            ls.y.animateTo(target.y, moveSpec, now)
            if (ls.alpha.value != 1f || ls.alpha.active) ls.alpha.animateTo(1f, fadeSpec, now)
            if (ls.scale.value != 1f || ls.scale.active) ls.scale.animateTo(1f, fadeSpec, now)
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
                val previousStrip = previous?.strip
                val ls = LiveSegment(new.id, new, layout, target, cell, target, 1f, 1f)
                ls.entering = true
                ls.stripRoll = roll
                val strip: List<TextLayoutResult>
                if (previous != null && previousStrip != null && previous.stripRoll == roll) {
                    // The old digit is itself mid-roll: start the new strip at the digit currently on
                    // screen, not the one it was heading to, so nothing blanks out while it catches up.
                    val p = previous.stripProgress.value.coerceIn(0f, (previousStrip.size - 1).toFloat())
                    val k = p.toInt()
                    strip = digitStrip(previousStrip[k].layoutInput.text.text, new.text, roll)
                    ls.stripProgress.snapTo(p - k, previous.stripProgress.velocityAt(now))
                    ls.x.snapTo(previous.x.value); ls.y.snapTo(previous.y.value)
                    ls.x.animateTo(target.x, moveSpec, now); ls.y.animateTo(target.y, moveSpec, now)
                } else {
                    strip = digitStrip(from.text, new.text, roll)
                }
                ls.strip = strip
                ls.stripProgress.animateTo((strip.size - 1).toFloat(), fadeSpec, now)
                live.add(ls)
                continue
            }
            val startY = if (roll != 0) target.y + roll * cell.height else target.y
            val ls = LiveSegment(new.id, new, layout, target, cell, Offset(target.x, startY), if (roll != 0) 1f else 0f, if (roll != 0) 1f else minScale)
            ls.entering = true
            if (roll != 0) ls.clip = cell
            live.add(ls)
            if (roll != 0) ls.y.animateTo(target.y, moveSpec, now)
            ls.alpha.animateTo(1f, fadeSpec, now)
            ls.scale.animateTo(1f, fadeSpec, now)
        }

        // Exiting segments fade (digits roll) out, then leave the list when settled.
        for (old in diff.exit) {
            val ls = byId[old.id] ?: continue
            if (old.id in claimed) {
                live.remove(ls) // its strip successor draws the old digit from here on
                continue
            }
            ls.exiting = true
            ls.entering = false
            ls.strip = null
            ls.stripProgress.snapTo(0f)
            val roll = if (opts.numbers && old.kind == SegmentKind.DIGIT) old.roll else 0
            if (roll != 0) {
                ls.clip = ls.cell
                ls.y.animateTo(ls.y.value - roll * ls.cell.height, moveSpec, now)
            } else {
                ls.alpha.animateTo(0f, fadeSpec, now)
                if (minScale != 1f) ls.scale.animateTo(minScale, fadeSpec, now)
            }
        }

        // Container size.
        if (opts.sizeMode == MorphSizeMode.Snap) {
            width.snapTo(newSize.width); height.snapTo(newSize.height)
        } else {
            width.animateTo(newSize.width, moveSpec, now)
            height.animateTo(newSize.height, moveSpec, now)
        }

        // Exactly one of complete/cancel per morph: a new morph cancels the one in flight.
        finishGeneration(cancelled = true)
        generationAnimating = true
        isAnimating = true
        frameTick.intValue++
        val start = onAnimationStart
        if (start != null) scope.launch { start() }
        ensureLoop()
    }

    private fun finishGeneration(cancelled: Boolean) {
        if (!generationAnimating) return
        generationAnimating = false
        isAnimating = false
        val cb = if (cancelled) onAnimationCancel else onAnimationComplete
        if (cb != null) scope.launch { cb() }
    }

    // endregion

    // region frame loop

    private fun ensureLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (true) {
                val active = withFrameNanos { now -> tick(now) }
                if (!active) break
            }
            lastFrameNanos = Channel.UNSET
            finishGeneration(cancelled = false)
        }
    }

    /** Advance every channel to [now]; drop settled exits. Returns true while anything still moves. */
    private fun tick(now: Long): Boolean {
        lastFrameNanos = now
        var active = false
        var i = 0
        while (i < live.size) {
            val ls = live[i]
            val moving = ls.tick(now)
            if (!moving && ls.exiting) {
                live.removeAt(i)
                continue
            }
            active = active || moving
            i++
        }
        val wMoving = width.tick(now)
        val hMoving = height.tick(now)
        val sizeMoving = wMoving || hMoving
        // One extra re-measure after settling so layout picks up the final value.
        if (sizeMoving || lastSizeMoving) sizeTick.intValue++
        lastSizeMoving = sizeMoving
        frameTick.intValue++
        return active || sizeMoving
    }

    private var lastSizeMoving = false

    // endregion

    // region geometry

    /** Top-left draw offset and the line cell (for clipping rolls) of [s] inside [full]. */
    private fun place(s: Segment, full: TextLayoutResult): Pair<Offset, Rect> {
        if (!TextMorphDiagnostics.logTimings) return placeImpl(s, full)
        val layoutBefore = timings.segmentLayout
        val t0 = System.nanoTime()
        val result = placeImpl(s, full)
        // Exclude nested segment measuring so the two numbers don't double-count.
        timings.place += (System.nanoTime() - t0) - (timings.segmentLayout - layoutBefore)
        timings.placeCalls++
        return result
    }

    private fun placeImpl(s: Segment, full: TextLayoutResult): Pair<Offset, Rect> {
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
        val diag = TextMorphDiagnostics.logTimings
        val boxStart = if (diag) System.nanoTime() else 0L
        var i = start
        while (i < end) {
            val box = full.getBoundingBox(i)
            if (box.width > 0f || box.left != 0f) {
                if (box.left < left) left = box.left
                if (box.right > right) right = box.right
            }
            i++
        }
        if (diag) { timings.box += System.nanoTime() - boxStart; timings.boxCalls += end - start }
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

    private fun segmentLayout(s: Segment): TextLayoutResult {
        layoutCache[s.text]?.let { return it }
        val diag = TextMorphDiagnostics.logTimings
        val t0 = if (diag) System.nanoTime() else 0L
        val measured = textMeasurer.measure(
            text = AnnotatedString(if (s.kind == SegmentKind.NEWLINE) "" else s.text),
            style = style,
            softWrap = false,
            maxLines = 1,
        )
        if (diag) { timings.segmentLayout += System.nanoTime() - t0; timings.layoutMisses++ }
        layoutCache[s.text] = measured
        return measured
    }

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

    // endregion
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
