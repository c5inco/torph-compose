package des.c5inco.torph.compose

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class MorphClipTest {
    @Test
    fun defaultIsTorphsQuarterLineMarginAndTenthLineFade() {
        // MorphDraw used these two constants before the clip was configurable.
        assertEquals(MorphClip(overflow = 0.25f, inset = 0.1f), MorphClip.Default)
        assertEquals(MorphClip.Default, MorphClip())
    }

    @Test
    fun optionsDefaultToTheDefaultClip() {
        assertSame(MorphClip.Default, MorphOptions().clip)
    }

    @Test
    fun lineBoxHasNoOverflow() {
        assertEquals(MorphClip(overflow = 0f, inset = 0.12f), MorphClip.lineBox())
        assertEquals(MorphClip(overflow = 0f, inset = 0.2f), MorphClip.lineBox(inset = 0.2f))
    }

    @Test
    fun windowIsANegativeOverflow() {
        assertEquals(MorphClip(overflow = -0.06f, inset = 0.13f), MorphClip.window())
        assertEquals(MorphClip(overflow = -0.1f, inset = 0.25f), MorphClip.window(margin = 0.1f, inset = 0.25f))
    }

    @Test
    fun clipTakesPartInOptionsEquality() {
        // TextMorph only writes new options to its state when they differ, so a clip change must.
        assertEquals(MorphOptions(clip = MorphClip.lineBox()), MorphOptions(clip = MorphClip(0f, 0.12f)))
        assertNotEquals(MorphOptions(), MorphOptions(clip = MorphClip.lineBox()))
    }
}
