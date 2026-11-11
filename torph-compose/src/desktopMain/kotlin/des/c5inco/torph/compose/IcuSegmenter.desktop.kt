package des.c5inco.torph.compose

import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale
import des.c5inco.torph.core.Segmenter
import java.util.Locale

/** [Segmenter] backed by ICU4J's grapheme and word [BreakIterator]s. One iterator pair is cached per locale. */
public actual class IcuSegmenter actual constructor() : Segmenter {
    private class Iterators(val grapheme: BreakIterator, val word: BreakIterator)

    private val cache = HashMap<Locale, Iterators>()

    private fun iterators(locale: Locale): Iterators = cache.getOrPut(locale) {
        val ul = ULocale.forLocale(locale)
        Iterators(BreakIterator.getCharacterInstance(ul), BreakIterator.getWordInstance(ul))
    }

    public actual override fun graphemes(text: String, locale: Locale): List<IntRange> = ranges(iterators(locale).grapheme, text)

    public actual override fun words(text: String, locale: Locale): List<IntRange> = ranges(iterators(locale).word, text)

    private fun ranges(it: BreakIterator, text: String): List<IntRange> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>(text.length)
        it.setText(text)
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            out.add(start until end)
            start = end
            end = it.next()
        }
        return out
    }
}
