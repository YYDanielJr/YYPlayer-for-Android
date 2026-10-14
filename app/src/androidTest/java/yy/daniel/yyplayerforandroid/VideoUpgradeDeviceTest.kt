package yy.daniel.yyplayerforandroid

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import yy.daniel.yyplayerforandroid.playback.*
import java.io.File

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class VideoUpgradeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base = instrumentation.targetContext
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("v15_test_$name", mode)
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(label: String, test: () -> Boolean) {
        val end = System.nanoTime() + 15_000_000_000L
        while (System.nanoTime() < end) {
            var result = false; main { result = test() }; if (result) return; Thread.sleep(60)
        }
        fail("超时：$label")
    }
    private fun fixture(name: String = "v15-video.mp4"): LocalMedia {
        val file = File(context.cacheDir, name)
        instrumentation.context.assets.open("v15-video.mp4").use { input -> file.outputStream().use(input::copyTo) }
        return LocalMedia(Uri.fromFile(file), "自有视频测试", MediaKind.VIDEO, "video/mp4")
    }
    @Test fun malformedSubtitleIsRejectedBeforeReload() {
        assertTrue(runCatching { validateVideoSubtitle("invalid subtitle".toByteArray(), "application/x-subrip") }.isFailure)
        validateVideoSubtitle("1\n00:00:00,000 --> 00:00:02,000\nValid subtitle\n".toByteArray(), "application/x-subrip")
        validateVideoSubtitle("WEBVTT\n\n00:00.000 --> 00:02.000\nValid WebVTT\n".toByteArray(), "text/vtt")
    }
    @Test fun realAudioAndTextSelectionSurviveDecoderRebuild() {
        val media = fixture()
        lateinit var backend: AndroidMedia3PlaybackBackend
        main { backend = AndroidMedia3PlaybackBackend(context); backend.play(listOf(media)) }
        try {
            await("双音轨和字幕") { backend.state.value.tracks.size >= 3 && backend.state.value.isPlaying }
            main {
                backend.exoPlayer().pause(); backend.seekTo(1800); backend.setSpeed(1.5f)
                val audio = backend.state.value.tracks.filter { it.type == C.TRACK_TYPE_AUDIO }.last()
                val text = backend.state.value.tracks.first { it.type == C.TRACK_TYPE_TEXT }
                backend.selectTrack(audio.type, audio.group, audio.track, backend.state.value.mediaGeneration)
                backend.selectTrack(text.type, text.group, text.track, backend.state.value.mediaGeneration)
            }
            await("第二音轨已选择") { backend.state.value.tracks.filter { it.type == C.TRACK_TYPE_AUDIO }.last().selected }
            await("内嵌字幕解码") { backend.exoPlayer().currentCues.cues.isNotEmpty() }
            main { backend.setDecoderMode(DecoderMode.SOFTWARE) }
            await("解码重建保留选择") {
                backend.state.value.phase == PlaybackPhase.READY &&
                    backend.state.value.tracks.filter { it.type == C.TRACK_TYPE_AUDIO }.lastOrNull()?.selected == true
            }
            main {
                assertFalse(backend.exoPlayer().playWhenReady)
                assertEquals(1800, backend.state.value.positionMs.toInt())
                assertEquals(1.5f, backend.state.value.speed, .001f)
                backend.selectTrack(C.TRACK_TYPE_TEXT, null, generation = backend.state.value.mediaGeneration)
            }
            await("字幕关闭") { backend.state.value.tracks.none { it.type == C.TRACK_TYPE_TEXT && it.selected } }
        } finally { main { backend.close() } }
    }

    @Test fun externalSubtitleRetainsPositionAndRejectsStaleImport() {
        val media = fixture()
        val file = File(context.cacheDir, "v15-sidecar.srt").apply {
            writeText("1\n00:00:00,000 --> 00:00:10,000\nExternal subtitle\n\n")
        }
        lateinit var backend: AndroidMedia3PlaybackBackend
        main { backend = AndroidMedia3PlaybackBackend(context); backend.play(listOf(media)) }
        try {
            await("播放就绪") { backend.state.value.isPlaying }
            main {
                backend.exoPlayer().pause(); backend.seekTo(2200)
                assertFalse(backend.attachSubtitle(VideoSubtitle(Uri.fromFile(file), file.name, "application/x-subrip"), media.uri, -1))
                assertTrue(backend.attachSubtitle(VideoSubtitle(Uri.fromFile(file), file.name, "application/x-subrip"), media.uri, backend.state.value.mediaGeneration))
            }
            await("外置字幕实际呈现") { backend.exoPlayer().currentCues.cues.any { it.text.toString().contains("External subtitle") } }
            main {
                assertFalse(backend.exoPlayer().playWhenReady)
                assertEquals(2200, backend.state.value.positionMs.toInt())
                backend.removeExternalSubtitle()
            }
            await("移除后恢复视频") { backend.state.value.subtitleName == null && backend.state.value.phase == PlaybackPhase.READY }
        } finally { main { backend.close() } }
    }

    @Test fun clockDrivenAbLoopAndMediaChangeCleanup() {
        val media = listOf(fixture(), fixture("v15-other.mp4"))
        lateinit var backend: AndroidMedia3PlaybackBackend
        main { backend = AndroidMedia3PlaybackBackend(context); backend.play(media) }
        try {
            await("就绪") { backend.state.value.isPlaying }
            main {
                backend.exoPlayer().pause(); backend.seekTo(1000); backend.markLoop()
                backend.seekTo(1700); backend.markLoop()
                assertEquals(1000L, backend.state.value.loopStartMs)
                assertEquals(1700L, backend.state.value.loopEndMs)
                backend.exoPlayer().play()
            }
            var wraps = 0; var last = 0L
            val end = System.nanoTime() + 5_000_000_000L
            while (System.nanoTime() < end && wraps < 3) {
                main { val now = backend.exoPlayer().currentPosition; if (now < last - 250) wraps++; last = now }
                Thread.sleep(60)
            }
            assertTrue("真实时钟至少循环三次", wraps >= 3)
            main {
                backend.next()
                assertNull(backend.state.value.loopStartMs); assertNull(backend.state.value.loopEndMs)
                backend.setPlayOrder(PlayOrder.SINGLE)
                assertEquals(Player.REPEAT_MODE_ONE, backend.exoPlayer().repeatMode)
                backend.setPlayOrder(PlayOrder.RANDOM)
                assertTrue(backend.exoPlayer().shuffleModeEnabled)
            }
        } finally { main { backend.close() } }
    }

    @Test fun boostTokenCannotChangeNewMediaAndRebuildRestoresBaseSpeed() {
        val media = listOf(fixture(), fixture("v15-next.mp4"))
        lateinit var backend: AndroidMedia3PlaybackBackend
        main { backend = AndroidMedia3PlaybackBackend(context); backend.play(media) }
        try {
            await("就绪") { backend.state.value.isPlaying }
            main {
                backend.setSpeed(2f)
                val old = backend.beginSpeedBoost()!!
                assertEquals(3f, backend.state.value.speed, 0f)
                backend.next()
                assertEquals(2f, backend.state.value.speed, 0f)
                backend.setSpeed(1.5f)
                backend.endSpeedBoost(old, 2f)
                assertEquals(1.5f, backend.state.value.speed, 0f)
                backend.beginSpeedBoost()
                backend.setDecoderMode(DecoderMode.SOFTWARE)
                assertEquals(1.5f, backend.state.value.speed, 0f)
            }
        } finally { main { backend.close() } }
    }
}
