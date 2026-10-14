package yy.daniel.yyplayerforandroid

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import yy.daniel.yyplayerforandroid.audio.OutputPath
import yy.daniel.yyplayerforandroid.playback.MediaKind
import yy.daniel.yyplayerforandroid.playback.PlayerSessionService
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerSessionServiceDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun awaitState(label: String, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            var ready = false
            main { ready = predicate() }
            if (ready) return
            Thread.sleep(100)
        }
        fail("超时：$label")
    }

    @Test
    fun sessionFollowsOutputSwitchAndKeepsMusicAliveWithoutAnActivity() {
        android.util.Log.i("YYPlayerAudio", "YYTEST_SESSION_STAGE start")
        assertTrue(yy.daniel.yyplayerforandroid.audio.NativeAudioCore.available)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = (context.applicationContext as PlayerApplication).runtime
        val tracks = runtime.library.load().filter { it.kind == MediaKind.AUDIO }
        assertTrue("需要已导入的真实本地音频", tracks.isNotEmpty())
        android.util.Log.i("YYPlayerAudio", "YYTEST_SESSION_STAGE library")
        val originalPath = runtime.preferences.value.audioOutputPath
        lateinit var original: yy.daniel.yyplayerforandroid.playback.PlaybackSnapshot
        main { original = runtime.playback.state.value }
        android.util.Log.i("YYPlayerAudio", "YYTEST_SESSION_STAGE engine")
        var controller: MediaController? = null
        var ownerHeld = false
        try {
            main {
                // Exercise the production ownership hooks called by PlayerHostActivity, without
                // waiting for Compose to become globally idle on this ROM.
                runtime.acquire()
                ownerHeld = true
                runtime.start()
                runtime.setAudioOutputPath(OutputPath.NATIVE_EXACT)
                runtime.playback.play(listOf(tracks.first(), tracks.first()))
            }
            lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
            main {
                future = MediaController.Builder(context,
                    SessionToken(context, ComponentName(context, PlayerSessionService::class.java))).buildAsync()
            }
            controller = future.get(15, TimeUnit.SECONDS)
            val activeController = controller!!
            awaitState("会话开始真实播放") { activeController.isPlaying && activeController.currentPosition > 1_000L }
            main { activeController.pause() }
            awaitState("控制器暂停真实引擎") { !runtime.playback.exoPlayer().playWhenReady }
            var pausedPosition = 0L
            main { pausedPosition = activeController.currentPosition }
            Thread.sleep(1_000)
            main { assertTrue(kotlin.math.abs(activeController.currentPosition - pausedPosition) < 100) }
            main { activeController.seekTo(60_000); activeController.play() }
            awaitState("拖动后从新位置续播") { activeController.isPlaying && activeController.currentPosition > 61_000L }
            main { runtime.setAudioOutputPath(OutputPath.SYSTEM) }
            awaitState("切系统输出后会话改绑") {
                activeController.isPlaying && activeController.currentPosition > 61_000L &&
                    runtime.playback.state.value.outputPath == OutputPath.SYSTEM
            }
            main { activeController.pause() }
            awaitState("新播放器仍受媒体控制器控制") { !runtime.playback.exoPlayer().playWhenReady }
            main { runtime.setAudioOutputPath(OutputPath.NATIVE_EXACT); activeController.play() }
            awaitState("切回整数输出后续播") { activeController.isPlaying && activeController.currentPosition > 62_000L }
            main { activeController.seekToNextMediaItem() }
            awaitState("队列切换时间基准归零") {
                activeController.currentMediaItemIndex == 1 && activeController.isPlaying &&
                    activeController.currentPosition in 1_000L..10_000L
            }
            main { runtime.stop() }
            var beforeBackground = 0L
            main { beforeBackground = activeController.currentPosition }
            Thread.sleep(2_000)
            main {
                assertTrue("后台必须继续播放", activeController.isPlaying)
                assertTrue(activeController.currentPosition > beforeBackground + 1_000L)
            }
            main { runtime.release(false); ownerHeld = false }
            Thread.sleep(1_500)
            main {
                assertTrue("最后 Activity 销毁后 service 必须保留同一引擎", runtime.playback.exoPlayer().isPlaying)
                android.util.Log.i("YYPlayerAudio", "YYTEST_SESSION background=true uiOwnersReleased=true " +
                    "position=${activeController.currentPosition} state=${activeController.playbackState}")
                activeController.pause()
            }
        } finally {
            main {
                controller?.release()
                if (ownerHeld) { runtime.stop(); runtime.release(false); ownerHeld = false }
                runtime.setAudioOutputPath(originalPath)
                if (original.queue.isNotEmpty() && original.queueIndex in original.queue.indices) {
                    runtime.playback.play(original.queue, original.queueIndex)
                    runtime.playback.seekTo(original.positionMs)
                    if (!original.isPlaying) runtime.playback.exoPlayer().pause()
                } else runtime.playback.exoPlayer().stop()
            }
            if (!original.isPlaying) context.stopService(android.content.Intent(context, PlayerSessionService::class.java))
        }
    }
}
