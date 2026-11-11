package des.c5inco.torph.compose

import des.c5inco.torph.core.Segmenter
import java.util.Locale

/** [Segmenter] backed by ICU's grapheme and word `BreakIterator`s. One iterator pair is cached per locale. */
public expect class IcuSegmenter() : Segmenter {
    override fun graphemes(text: String, locale: Locale): List<IntRange>

    override fun words(text: String, locale: Locale): List<IntRange>
}
