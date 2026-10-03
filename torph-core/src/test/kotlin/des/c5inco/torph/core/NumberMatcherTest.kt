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
        assertEquals(1234.5, parseNumericWord(words[0].text, en))
        assertEquals(-12.0, parseNumericWord(words[1].text, en))
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
        // No decimals given: up to 3 fraction digits, half-up, like Intl.NumberFormat and torph.
        assertEquals("1,234.5", formatNumber(1234.5, locale = Locale.US))
        assertEquals("2", formatNumber(2.0, locale = Locale.US))
        assertEquals("1.235", formatNumber(1.2345, locale = Locale.US))
        assertEquals("1.01", formatNumber(1.005, 2, Locale.US))
    }
}
