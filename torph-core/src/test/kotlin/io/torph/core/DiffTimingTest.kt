package io.torph.core

import java.util.Locale
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

/** Coarse budget for the per-change work: segment + diff of a 1000-char string in well under a frame. */
class DiffTimingTest {
    private val words = "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor 42 incididunt 1,234 labore".split(" ")

    private fun text(chars: Int, seed: Int): String {
        val r = Random(seed)
        val sb = StringBuilder()
        while (sb.length < chars) sb.append(words[r.nextInt(words.size)]).append(' ')
        return sb.substring(0, chars)
    }

    @Test
    fun `1000 chars segments and diffs within budget`() {
        val a = text(1000, 1)
        val b = text(1000, 2)
        // Word mode is what the >300-segment guard produces for this size; also time forced graphemes.
        for (mode in listOf(Segmentation.AUTO, Segmentation.GRAPHEME)) {
            val opts = SegmentOptions(segmentation = mode, maxSegments = if (mode == Segmentation.GRAPHEME) Int.MAX_VALUE else 300)
            val old = segmentText(a, Locale.US, SimpleSegmenter, opts)
            repeat(20) { diffSegments(old, b, Locale.US, SimpleSegmenter, opts) } // warm-up
            val best = (1..10).minOf { measureNanoTime { diffSegments(old, b, Locale.US, SimpleSegmenter, opts) } }
            println("diff 1000 chars $mode (${old.size} segments): ${best / 1000} µs")
            assertTrue(best < 16_000_000L, "$mode diff took ${best / 1_000_000} ms")
        }
    }
}
