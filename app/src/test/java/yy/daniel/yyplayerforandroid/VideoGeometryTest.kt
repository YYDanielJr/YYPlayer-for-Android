package yy.daniel.yyplayerforandroid

import org.junit.Assert.*
import org.junit.Test
import yy.daniel.yyplayerforandroid.ui.*

class VideoGeometryTest {
    @Test fun fitPreservesAspectAndFillReachesBothEdges() {
        for ((w, h) in listOf(2400f to 1080f, 1080f to 2400f, 1600f to 1200f)) {
            for (aspect in listOf(16f / 9, 9f / 16, 4f / 3, 2.35f)) {
                val (x, y) = videoFitScale(w, h, aspect, VideoFit.FIT)
                assertEquals(aspect, w * x / (h * y), .0001f)
                assertTrue(x <= 1f && y <= 1f)
                val (fillX, fillY) = videoFitScale(w, h, aspect, VideoFit.FILL)
                assertEquals(aspect, w * fillX / (h * fillY), .0001f)
                assertTrue(fillX >= .9999f && fillY >= .9999f)
            }
        }
    }
    @Test fun zoomCanCoverFormerSideBars() {
        val (x, y) = videoFitScale(2400f, 1080f, 16f / 9, VideoFit.FIT)
        assertEquals(.8f, x, .0001f)
        assertEquals(1f, y, .0001f)
        assertTrue("2x exceeds the full viewport, rather than an aspect-sized clipped child", x * 2 > 1f)
    }
    @Test fun invalidSizeIsSafeAndForcedAspectIsExplicit() {
        assertEquals(1f to 1f, videoFitScale(0f, 100f, 1f, VideoFit.FIT))
        assertEquals(1f to 1f, videoFitScale(100f, 100f, Float.NaN, VideoFit.FILL))
        val (x, y) = videoFitScale(2400f, 1080f, 1f, VideoFit.CLASSIC)
        assertEquals(4f / 3, 2400 * x / (1080 * y), .0001f)
    }
}
