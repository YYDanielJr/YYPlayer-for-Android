package yy.daniel.yyplayerforandroid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import yy.daniel.yyplayerforandroid.playback.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Actual Media3 WAV playback and two original covers, never personal music or a synthetic clock. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class MusicExperienceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun fixture(test: (List<LocalMedia>) -> Unit) {
        val context = compose.activity
        val files = mutableListOf<File>()
        val songs = listOf(0xff289cb8.toInt(), 0xffc8903c.toInt()).mapIndexed { index, color ->
            val audio = File.createTempFile("u07-own-$index-", ".wav", context.cacheDir).also { files += it }
            val pcm = ByteArray(44100 * 4 * 30)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(pcm.size + 36); put("WAVEfmt ".toByteArray()); putInt(16)
                putShort(1); putShort(2); putInt(44100); putInt(176400); putShort(4); putShort(16); put("data".toByteArray()); putInt(pcm.size)
            }.array()
            audio.outputStream().use { it.write(header); it.write(pcm) }
            val coverDirectory = File(context.filesDir, "online-artwork").apply { mkdirs() }
            val cover = File.createTempFile("u07-own-$index-", ".png", coverDirectory).also { files += it }
            val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); canvas.drawColor(color)
            canvas.drawCircle(250f, 250f, 140f, Paint().apply { this.color = 0xffeaf4f8.toInt() })
            cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            LocalMedia(Uri.fromFile(audio), audio.name, MediaKind.AUDIO, "audio/wav", songTitle = "原创封面验收 ${index + 1}",
                artist = "YYPlayer QA", album = "自有几何封面", artworkUri = Uri.fromFile(cover), metadataRead = true)
        }
        try {
            compose.runOnIdle { compose.activity.runtime.playback.play(songs) }
            compose.waitUntil(10000) { compose.activity.runtime.playback.state.value.isPlaying }
            compose.runOnIdle { compose.activity.runtime.playback.togglePlayPause() }
            test(songs)
        } finally { files.forEach { it.delete() } }
    }
    private fun evidence(name: String, node: SemanticsNodeInteraction? = null) {
        if (InstrumentationRegistry.getArguments().getString("u07Evidence") != "true") return
        compose.waitForIdle()
        val directory = File(compose.activity.cacheDir, "u07-evidence").apply { mkdirs() }
        val bitmap = (node ?: compose.onRoot(useUnmergedTree = true)).captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun ringSeekPauseAndNextControlTheRealSharedPlayer() = fixture { songs ->
        val backend = compose.activity.runtime.playback
        compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.5f) }
        compose.waitUntil(5000) { backend.state.value.positionMs in 14000..16000 }
        assertFalse(backend.state.value.isPlaying)
        compose.onNodeWithContentDescription("播放").performClick()
        compose.waitUntil(5000) { backend.state.value.isPlaying }
        compose.onNodeWithContentDescription("暂停").performClick()
        compose.waitUntil(5000) { !backend.state.value.isPlaying }
        compose.onNodeWithContentDescription("专辑封面，切换到歌词").assertDoesNotExist()
        val mini = compose.onNodeWithTag("library-mini-player")
        evidence("mini-player-ring", mini)
        val resting = mini.captureToImage().asAndroidBitmap()
        compose.mainClock.autoAdvance = false
        mini.performTouchInput { down(androidx.compose.ui.geometry.Offset(80f, center.y)) }
        compose.mainClock.advanceTimeBy(250)
        val pressed = mini.captureToImage().asAndroidBitmap()
        var changed = 0
        for (y in 0 until minOf(resting.height, pressed.height) step 4) for (x in 0 until minOf(resting.width, pressed.width) step 4) {
            if (resting.getPixel(x, y) != pressed.getPixel(x, y)) changed++
        }
        evidence("mini-player-sink-pressed", mini)
        assertTrue("Whole playback capsule must sink", changed > 100)
        mini.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("下一首").performClick()
        compose.waitUntil(5000) { backend.state.value.current?.uri == songs[1].uri }
    }
    @Test fun coverKeepsItsShapeDuringNextAndClickOpensLyrics() = fixture { songs ->
        val backend = compose.activity.runtime.playback
        compose.runOnIdle { compose.activity.showMusic() }
        val cover = compose.onNodeWithContentDescription("专辑封面，切换到歌词")
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("专辑封面，切换到歌词").fetchSemanticsNodes().isNotEmpty() }
        cover.assertIsDisplayed()
        val bounds = cover.fetchSemanticsNode().boundsInRoot
        evidence("cover-before")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("下一首").performClick()
        compose.waitUntil(5000) { backend.state.value.current?.uri == songs[1].uri }
        compose.mainClock.advanceTimeBy(150)
        assertEquals(bounds, cover.fetchSemanticsNode().boundsInRoot)
        evidence("cover-next-150ms")
        for (elapsed in listOf(300, 450)) {
            compose.mainClock.advanceTimeBy(150)
            assertEquals(bounds, cover.fetchSemanticsNode().boundsInRoot)
            evidence("cover-next-${elapsed}ms")
            val pixels = cover.captureToImage().asAndroidBitmap()
            val centerColor = pixels.getPixel(pixels.width / 2, pixels.height / 2)
            assertTrue("Artwork must not collapse to the grey placeholder mid-transition",
                android.graphics.Color.red(centerColor) > 150 && android.graphics.Color.green(centerColor) > 150 && android.graphics.Color.blue(centerColor) > 150)
        }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(bounds, cover.fetchSemanticsNode().boundsInRoot)
        evidence("cover-next-complete")
        val restingPixels = cover.captureToImage().asAndroidBitmap()
        cover.performTouchInput { down(center + androidx.compose.ui.geometry.Offset(30f, -30f)) }
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        evidence("cover-tilt-pressed")
        val heldPixels = cover.captureToImage().asAndroidBitmap()
        var changedPixels = 0
        for (y in 0 until minOf(restingPixels.height, heldPixels.height) step 4) {
            for (x in 0 until minOf(restingPixels.width, heldPixels.width) step 4) {
                if (restingPixels.getPixel(x, y) != heldPixels.getPixel(x, y)) changedPixels++
            }
        }
        assertTrue("Held cover must visibly tilt (changed samples=$changedPixels)", changedPixels > 100)
        cover.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // Portrait moves to page 1; wide view expands the existing real lyrics pane.
        compose.onNodeWithText("无歌词").assertIsDisplayed()
        evidence("cover-click-lyrics")
    }
}
