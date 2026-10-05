package yy.daniel.yyplayerforandroid

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import yy.daniel.yyplayerforandroid.playback.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real PCM/ExoPlayer playback across actual Activities, without clearing user imports/preferences. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class ActivityPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Test fun realEofAdvancesProjectedSongAndGeneration() {
        val context = instrumentation.targetContext
        val first = File.createTempFile("yyplayer-eof-first-", ".wav", context.cacheDir)
        val second = File.createTempFile("yyplayer-eof-second-", ".wav", context.cacheDir)
        fun write(file: File, seconds: Int) {
            val pcm = ByteArray(44100 * 4 * seconds)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(pcm.size + 36); put("WAVEfmt ".toByteArray()); putInt(16)
                putShort(1); putShort(2); putInt(44100); putInt(176400); putShort(4); putShort(16); put("data".toByteArray()); putInt(pcm.size)
            }.array()
            file.outputStream().use { it.write(header); it.write(pcm) }
        }
        write(first, 1); write(second, 5)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                lateinit var backend: AndroidMedia3PlaybackBackend
                var generation = 0L
                scenario.onActivity {
                    backend = it.runtime.playback
                    backend.play(listOf(LocalMedia(Uri.fromFile(first), first.name, MediaKind.AUDIO, "audio/wav", songTitle = "第一首"),
                        LocalMedia(Uri.fromFile(second), second.name, MediaKind.AUDIO, "audio/wav", songTitle = "第二首")))
                    generation = backend.state.value.mediaGeneration
                    it.showMusic()
                }
                await { backend.state.value.queueIndex == 1 && backend.state.value.isPlaying }
                instrumentation.runOnMainSync {
                    assertEquals(Uri.fromFile(second), backend.state.value.current?.uri)
                    assertEquals("第二首", backend.state.value.current?.musicTitle)
                    assertTrue(backend.state.value.mediaGeneration > generation)
                    backend.togglePlayPause()
                }
            }
        } finally { first.delete(); second.delete() }
    }
    @Test fun secondaryActivitiesShareEngineAndReturnToExistingLibrary() {
        val context = instrumentation.targetContext
        val fixture = File.createTempFile("yyplayer-activity-", ".wav", context.cacheDir)
        val pcm = ByteArray(44100 * 2 * 2 * 30)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(pcm.size + 36); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(2); putInt(44100); putInt(44100 * 4)
            putShort(4); putShort(16); put("data".toByteArray()); putInt(pcm.size)
        }.array()
        fixture.outputStream().use { it.write(header); it.write(pcm) }
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                lateinit var main: MainActivity
                lateinit var backend: AndroidMedia3PlaybackBackend
                scenario.onActivity {
                    main = it; backend = it.runtime.playback
                    backend.play(listOf(LocalMedia(Uri.fromFile(fixture), fixture.name, MediaKind.AUDIO, "audio/wav")), 0)
                }
                await { backend.state.value.isPlaying }
                instrumentation.runOnMainSync { main.openSettings() }
                val settings = resumed<SettingsActivity>()
                SystemClock.sleep(600)
                instrumentation.runOnMainSync {
                    assertSame(backend, settings.runtime.playback)
                    backend.refreshPosition()
                    assertTrue("Activity hand-off must keep the real player running", backend.state.value.isPlaying)
                    assertTrue(backend.state.value.positionMs > 0)
                    settings.openOrientation()
                }
                val selection = resumed<OrientationActivity>()
                instrumentation.runOnMainSync { assertSame(backend, selection.runtime.playback); selection.closePage() }
                resumed<SettingsActivity>()
                instrumentation.runOnMainSync { settings.closePage() }
                assertSame(main, resumed<MainActivity>())
                instrumentation.runOnMainSync { main.openPlayer() }
                SystemClock.sleep(800)
                assertSame("Audio details must remain in MainActivity", main, resumed<MainActivity>())
                instrumentation.runOnMainSync {
                    assertTrue(main.musicRequest > 0)
                    assertEquals(Uri.fromFile(fixture), backend.state.value.current?.uri)
                    assertTrue(backend.state.value.isPlaying)
                }
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                assertSame(main, resumed<MainActivity>())
                instrumentation.runOnMainSync {
                    assertSame(backend, main.runtime.playback)
                    assertTrue("Collapsing music details must not release the shared engine", backend.state.value.isPlaying)
                    backend.togglePlayPause()
                }
            }
        } finally { fixture.delete() }
    }

    private inline fun <reified T> resumed(): T {
        var result: T? = null
        await {
            instrumentation.runOnMainSync { result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<T>().firstOrNull() }
            result != null
        }
        return result!!
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (!condition()) { check(SystemClock.uptimeMillis() < deadline) { "Activity/playback state timed out" }; SystemClock.sleep(50) }
        instrumentation.waitForIdleSync()
    }
}
