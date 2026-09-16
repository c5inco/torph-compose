package des.c5inco.torph.core

import java.util.Locale
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiffTest {
    private val en = Locale.US

    private fun seg(text: String, options: SegmentOptions = SegmentOptions(), firstId: Long = 0) =
        segmentText(text, en, SimpleSegmenter, options, firstId)

    private fun diff(old: String, new: String, options: SegmentOptions = SegmentOptions()): Pair<List<Segment>, DiffResult> {
        val o = seg(old, options)
        val r = diffSegments(o, new, en, SimpleSegmenter, options)
        return o to r
    }

    @Test
    fun `persist plus enter reconstructs new, persist plus exit reconstructs old`() {
        val (o, r) = diff("Hello world", "Hello there")
        assertEquals("Hello there", r.segments.sortedBy { it.index }.joinToString("") { it.text })
        val newFromParts = (r.persist.map { it.second } + r.enter).sortedBy { it.index }.joinToString("") { it.text }
        assertEquals("Hello there", newFromParts)
        val oldFromParts = (r.persist.map { it.first } + r.exit).sortedBy { it.index }.joinToString("") { it.text }
        assertEquals("Hello world", oldFromParts)
        assertEquals(o.size, r.persist.size + r.exit.size)
    }

    @Test
    fun `shared prefix keeps ids`() {
        val (o, r) = diff("Hello world", "Hello there")
        val oldIds = o.take(6).map { it.id }
        val newIds = r.segments.take(6).map { it.id }
        assertEquals(oldIds, newIds)
    }

    @Test
    fun `identical text is a no-op diff`() {
        val (_, r) = diff("Same text 123", "Same text 123")
        assertTrue(r.isEmpty)
    }

    @Test
    fun `digits match by place value`() {
        val (o, r) = diff("$1,234", "$1,274")
        // Only the tens digit changed.
        assertEquals(listOf("3"), r.exit.map { it.text })
        assertEquals(listOf("7"), r.enter.map { it.text })
        assertEquals(1, r.enter.single().place)
        assertEquals(1, r.enter.single().roll)
        // Everything else persists with the same id.
        assertEquals(o.size - 1, r.persist.size)
    }

    @Test
    fun `growing a number keeps low places and enters high place`() {
        val (_, r) = diff("999", "1,000")
        assertEquals(setOf("9"), r.exit.map { it.text }.toSet())
        assertEquals(3, r.exit.size)
        assertTrue(r.enter.any { it.text == "1" && it.place == 3 })
        assertTrue(r.enter.any { it.text == "," && it.kind == SegmentKind.NUMBER_SYMBOL })
        assertTrue(r.enter.filter { it.kind == SegmentKind.DIGIT }.all { it.roll == 1 })
    }

    @Test
    fun `decreasing rolls down`() {
        val (_, r) = diff("50", "49")
        assertTrue(r.enter.all { it.roll == -1 })
    }

    @Test
    fun `currency prefix persists when digit count changes`() {
        val (o, r) = diff("$9.99", "$10.00")
        val dollar = o.first { it.text == "$" }
        assertTrue(r.persist.any { it.first.id == dollar.id && it.second.text == "$" })
        val dot = o.first { it.text == "." }
        assertTrue(r.persist.any { it.first.id == dot.id })
    }

    @Test
    fun `cursor index disables place matching for that number`() {
        // Typing "1" after "12" with the caret at the end: positional matching keeps 1 and 2.
        val opts = SegmentOptions(cursorIndex = 3)
        val o = seg("12", SegmentOptions(cursorIndex = 2))
        val r = diffSegments(o, "123", en, SimpleSegmenter, opts)
        assertEquals(listOf("3"), r.enter.map { it.text })
        assertTrue(r.exit.isEmpty())
        // Without the cursor, place matching shifts: "12" -> "123" moves 1 and 2 to new places.
        val (_, r2) = diff("12", "123")
        assertEquals(2, r2.exit.size)
    }

    @Test
    fun `newlines are their own segments`() {
        val s = seg("a\nb")
        assertEquals(listOf(SegmentKind.TEXT, SegmentKind.NEWLINE, SegmentKind.TEXT), s.map { it.kind })
    }

    @Test
    fun `word mode segments arabic as whole words in auto`() {
        val s = seg("hi مرحبا", SegmentOptions(segmentation = Segmentation.AUTO))
        assertEquals(listOf("h", "i", " ", "مرحبا"), s.map { it.text })
    }

    @Test
    fun `long text falls back to word segmentation`() {
        val text = (1..80).joinToString(" ") { "word" }
        val s = seg(text, SegmentOptions(maxSegments = 100))
        assertTrue(s.size < 200)
        assertTrue(s.any { it.text == "word" })
    }

    @Test
    fun `locale symbols are respected`() {
        val de = Locale.GERMANY
        val s = segmentText("1.234,50 €", de, SimpleSegmenter)
        val digits = s.filter { it.kind == SegmentKind.DIGIT }
        assertEquals(listOf(3, 2, 1, 0, -1, -2), digits.map { it.place })
        assertTrue(s.any { it.text == "€" && it.kind == SegmentKind.NUMBER_SYMBOL })
    }

    @Test
    fun `random strings preserve the invariants`() {
        val rnd = Random(42)
        val alphabet = "ab 1290,.$\n"
        repeat(500) {
            val a = (0 until rnd.nextInt(0, 14)).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
            val b = (0 until rnd.nextInt(0, 14)).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
            val o = seg(a)
            val r = diffSegments(o, b, en, SimpleSegmenter)
            assertEquals(b, r.segments.joinToString("") { it.text }, "segments for '$a' -> '$b'")
            assertEquals(b, (r.persist.map { it.second } + r.enter).sortedBy { it.index }.joinToString("") { it.text })
            assertEquals(a, (r.persist.map { it.first } + r.exit).sortedBy { it.index }.joinToString("") { it.text })
            assertEquals(r.segments.size, r.segments.map { it.id }.toSet().size, "ids unique")
            assertTrue(r.persist.all { it.first.id == it.second.id })
            assertTrue(r.enter.all { e -> o.none { it.id == e.id } }, "enter ids fresh")
            // A second diff with the result as old must be consistent too.
            val r2 = diffSegments(r.segments, a, en, SimpleSegmenter, nextId = r.nextId)
            assertEquals(a, r2.segments.joinToString("") { it.text })
        }
    }
}
