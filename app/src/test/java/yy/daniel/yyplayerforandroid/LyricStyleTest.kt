package yy.daniel.yyplayerforandroid

import org.junit.Assert.*
import org.junit.Test
import yy.daniel.yyplayerforandroid.ui.LyricStyle

class LyricStyleTest {
    @Test fun colorsPreserveAlphaAndRejectMalformedInput() {
        assertEquals(0xff3482ffL, LyricStyle.parseColor("#3482FF"))
        assertEquals(0x803482ffL, LyricStyle.parseColor("803482ff"))
        listOf("#12", "#GG3482FF", "#FFFFFFFFF", "-123456").forEach { assertNull(LyricStyle.parseColor(it)) }
    }
    @Test fun unsafeStoredDimensionsWeightsAndColorsAreRejected() {
        listOf(LyricStyle(activeSize = Float.NaN), LyricStyle(inactiveSize = 49f),
            LyricStyle(activeWeight = 350), LyricStyle(lineSpacing = -1f), LyricStyle(inactiveColor = 0x1ffffffffL)).forEach {
            assertTrue(runCatching { it.validated() }.isFailure)
        }
        assertEquals(48f, LyricStyle(activeSize = 48f, inactiveWeight = 900, lineSpacing = 40f).validated().activeSize)
    }
}
