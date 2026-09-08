package io.torph.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/** The characters a locale uses to write numbers. */
public data class NumberSymbols(
    val decimalSeparator: Char,
    val groupingSeparator: Char,
    val minusSign: Char,
    val zeroDigit: Char,
) {
    public companion object {
        private val cache = HashMap<Locale, NumberSymbols>()

        public fun forLocale(locale: Locale): NumberSymbols = synchronized(cache) {
            cache.getOrPut(locale) {
                val s = DecimalFormatSymbols.getInstance(locale)
                NumberSymbols(
                    decimalSeparator = s.decimalSeparator,
                    groupingSeparator = s.groupingSeparator,
                    minusSign = s.minusSign,
                    zeroDigit = s.zeroDigit,
                )
            }
        }
    }
}

/**
 * Formats [value] the way `Intl.NumberFormat` would for [locale]: grouping on, exactly [decimals]
 * fraction digits, half-up rounding. The result is what you hand to `TextMorph`.
 */
public fun formatNumber(value: Number, decimals: Int = 0, locale: Locale = Locale.getDefault()): String {
    val format = NumberFormat.getNumberInstance(locale)
    format.isGroupingUsed = true
    format.minimumFractionDigits = decimals
    format.maximumFractionDigits = decimals
    format.roundingMode = RoundingMode.HALF_UP
    val v: Number = when (value) {
        is Float -> BigDecimal(value.toDouble())
        is Double -> BigDecimal(value)
        else -> value
    }
    return format.format(v)
}

/** Parses the digits of [text] (any Unicode digit script) into a double; symbols other than the locale decimal separator and sign are ignored. */
internal fun parseNumericWord(text: String, symbols: NumberSymbols): Double {
    val sb = StringBuilder()
    var negative = false
    var sawDecimal = false
    for (ch in text) {
        when {
            Character.isDigit(ch) -> sb.append(Character.getNumericValue(ch))
            ch == symbols.decimalSeparator && !sawDecimal -> { sb.append('.'); sawDecimal = true }
            ch == symbols.minusSign || ch == '-' || ch == '−' -> negative = sb.isEmpty()
        }
    }
    if (sb.isEmpty() || sb.toString() == ".") return 0.0
    val v = sb.toString().toDoubleOrNull() ?: 0.0
    return if (negative) -v else v
}
