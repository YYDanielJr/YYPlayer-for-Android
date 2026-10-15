package yy.daniel.yyplayerforandroid

import org.junit.Assert.assertEquals
import org.junit.Test
import yy.daniel.yyplayerforandroid.playback.mpvDisplaySize

class MpvVideoGeometryTest {
    @Test fun screenRecordingRotationUsesLandscapeDisplayBounds() {
        for (rotation in listOf(90, 270, -90, 450))
            assertEquals(2038 to 1440, mpvDisplaySize(1440, 2038, rotation))
        for (rotation in listOf(0, 180, 360, -180))
            assertEquals(1440 to 2038, mpvDisplaySize(1440, 2038, rotation))
    }
    @Test fun correctedPixelAspectIsNotAppliedTwice() {
        // A 720x576 PAL source can already have dw/dh=1024x576 after mpv's SAR correction.
        assertEquals(1024 to 576, mpvDisplaySize(1024, 576, 0))
        assertEquals(576 to 1024, mpvDisplaySize(1024, 576, 90))
        assertEquals(0 to 0, mpvDisplaySize(0, 576, 90))
        assertEquals(0 to 0, mpvDisplaySize(1024, -1, 0))
    }
}
