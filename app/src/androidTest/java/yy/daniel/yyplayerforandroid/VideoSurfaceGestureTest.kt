package yy.daniel.yyplayerforandroid

import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import yy.daniel.yyplayerforandroid.playback.*
import yy.daniel.yyplayerforandroid.ui.VideoSurface
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** Injected MotionEvents exercise the real Compose/TextureView path. No production fake media. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class VideoSurfaceGestureTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var player: ExoPlayer
    private val backend = GestureBackend()
    private val visible = mutableStateOf(true)
    private val surfaceActive = mutableStateOf(true)
    private val snapshot = PlaybackSnapshot(queue = listOf(LocalMedia(Uri.parse("file:///gesture-test.mp4"), "gesture-test.mp4", MediaKind.VIDEO, "video/mp4")), queueIndex = 0, speed = 1.5f)

    @Before fun setup() {
        compose.runOnUiThread { player = ExoPlayer.Builder(InstrumentationRegistry.getInstrumentation().targetContext).build() }
        compose.setContent { if (visible.value) VideoSurface(Modifier.fillMaxSize().testTag("surface"), player, backend, snapshot, 0, surfaceActive = surfaceActive.value) }
    }
    @After fun teardown() { compose.runOnUiThread { player.release() } }

    @Test fun pinchAndRotationReachTextureMatrix() {
        compose.onNodeWithTag("surface").performTouchInput {
            val origin = center
            down(0, origin + Offset(-50f, 0f)); down(1, origin + Offset(50f, 0f))
            updatePointerTo(0, origin + Offset(-90f, -60f)); updatePointerTo(1, origin + Offset(130f, 60f)); move()
            up(0); up(1)
        }
        compose.waitForIdle()
        onView(isAssignableFrom(TextureView::class.java)).check { view, error ->
            if (error != null) throw error
            val values = FloatArray(9)
            (view as TextureView).getTransform(Matrix()).getValues(values)
            val scale = sqrt(values[Matrix.MSCALE_X] * values[Matrix.MSCALE_X] + values[Matrix.MSKEW_Y] * values[Matrix.MSKEW_Y])
            val degrees = Math.toDegrees(atan2(values[Matrix.MSKEW_Y], values[Matrix.MSCALE_X]).toDouble())
            assertTrue("pinch should scale the real texture (actual=$scale)", scale > 1.5f)
            assertTrue("rotation should reach the real texture", abs(degrees) > 15)
        }
        assertFalse("two fingers must not leave a speed override", backend.speeds.contains(3f))
    }

    @Test fun longPressRestoresExistingBaseSpeed() {
        compose.onNodeWithTag("surface").performTouchInput { longClick(durationMillis = 900) }
        compose.waitForIdle()
        assertTrue(backend.speeds.contains(3f))
        assertEquals(1.5f, backend.speeds.last(), 0f)
    }

    @Test fun removingSurfaceCancelsTemporarySpeed() {
        compose.onNodeWithTag("surface").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(900)
        compose.waitUntil(3000) { backend.speeds.contains(3f) }
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        assertEquals(1.5f, backend.speeds.last(), 0f)
    }

    @Test fun activityStopAndResumeRetainsTextureTransform() {
        compose.onNodeWithTag("surface").performTouchInput {
            down(0, center + Offset(-50f, 0f)); down(1, center + Offset(50f, 0f))
            updatePointerTo(0, center + Offset(-90f, -60f)); updatePointerTo(1, center + Offset(130f, 60f)); move()
            up(0); up(1)
        }
        compose.waitForIdle()
        fun matrix(): FloatArray {
            val values = FloatArray(9)
            onView(isAssignableFrom(TextureView::class.java)).check { view, error ->
                if (error != null) throw error
                (view as TextureView).getTransform(Matrix()).getValues(values)
            }
            return values
        }
        val before = matrix()
        compose.runOnIdle { surfaceActive.value = false }; compose.waitForIdle()
        compose.runOnIdle { surfaceActive.value = true }; compose.waitForIdle()
        val after = matrix()
        before.indices.forEach { assertEquals("matrix component $it must survive detach/attach", before[it], after[it], .1f) }
    }

    private class GestureBackend : PlaybackBackend {
        override val state = MutableStateFlow(PlaybackSnapshot())
        val speeds = mutableListOf<Float>()
        override fun setSpeed(speed: Float) { speeds.add(speed) }
        override fun play(queue: List<LocalMedia>, startIndex: Int) = Unit
        override fun togglePlayPause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun seekBy(deltaMs: Long) = Unit
        override fun setDecoderMode(mode: DecoderMode) = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun close() = Unit
    }
}
