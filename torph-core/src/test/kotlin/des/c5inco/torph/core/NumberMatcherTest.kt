package des.c5inco.torph.core

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class NumberMatcherTest {
    private val en = NumberSymbols.forLocale(Locale.US)

    @Test
    fun `finds numbers with attached symbols`() {
        val words = findNumericWords("Total $1,234.50 and -12% at 3pm.", en)
        assertEquals(listOf("$1,234.50", "-12%", "3"), words.map { it.text })
        assertEquals(1234.5, words[0].value)
        assertEquals(-12.0, words[1].value)
    }

    @Test
    fun `places for currency`() {
        val w = findNumericWords("$1,234.50", en).single()
        assertEquals(listOf(3, 3, 3, 2, 1, 0, 0, -1, -2), w.places.toList())
    }

    @Test
    fun `sentence period is not swallowed`() {
        val w = findNumericWords("I have 5.", en).single()
        assertEquals("5", w.text)
    }

    @Test
    fun `format number`() {
        assertEquals("1,234.50", formatNumber(1234.5, 2, Locale.US))
        assertEquals("1.234,50", formatNumber(1234.5, 2, Locale.GERMANY))
    }
}
