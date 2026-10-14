package yy.daniel.yyplayerforandroid

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import yy.daniel.yyplayerforandroid.playback.*
import yy.daniel.yyplayerforandroid.ui.*
import yy.daniel.yyplayerforandroid.ui.theme.YYPlayerTheme
import java.io.File

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class MpvVideoDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base = instrumentation.targetContext
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("v16_test_$name", mode)
    }
    private lateinit var backend: AndroidMedia3PlaybackBackend
    private var active by mutableStateOf(true)
    private var page by mutableStateOf(false)
    private var view: VideoViewport? = null
    private lateinit var media: LocalMedia
    @Before fun setup() {
        val file = File(context.cacheDir, "v16-chapters.mkv")
        instrumentation.context.assets.open("v16-chapters.mkv").use { input -> file.outputStream().use(input::copyTo) }
        media = LocalMedia(Uri.fromFile(file), "libmpv 自有测试", MediaKind.VIDEO, "video/x-matroska")
        compose.runOnUiThread {
            backend = AndroidMedia3PlaybackBackend(context); backend.setVideoEngine(VideoEngine.MPV); backend.play(listOf(media))
        }
        compose.setContent {
            val s by backend.state.collectAsState()
            YYPlayerTheme(ColorSchemeMode.Dark) {
                if (page) VideoPlayerPage(backend, s, true, {}, {}, active, null)
                else VideoSurface(player = backend.exoPlayer(), backend = backend, snapshot = s, resetTransformKey = 0,
                    surfaceActive = active, onViewReady = { view = it })
            }
        }
        await("libmpv 真正装载视频") { it.phase == PlaybackPhase.READY && it.videoWidth > 0 }
        compose.runOnIdle {
            backend.exoPlayer().pause(); backend.seekTo(2000)
            val sub = backend.state.value.tracks.first { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
            backend.selectTrack(sub.type, sub.group, generation = backend.state.value.mediaGeneration)
        }
        await("暂停后精确定位") { !it.isPlaying && kotlin.math.abs(it.positionMs - 2000) < 90 }
    }
    private fun await(label: String, predicate: (PlaybackSnapshot) -> Boolean) {
        try { compose.waitUntil(20000) {
            compose.runOnUiThread { backend.refreshPosition() }
            val s = backend.state.value
            assertNull("$label: ${s.error}", s.error)
            predicate(s)
        } } catch (e: androidx.compose.ui.test.ComposeTimeoutException) { fail("$label 超时：${backend.state.value}") }
    }
    @After fun finish() { if (::backend.isInitialized) compose.runOnUiThread { backend.close() } }
    @Test fun speedRangeSurvivesRebuildAndBoost() {
        for (value in listOf(.1f, .2f, 1.25f, 8.1f, 10f)) {
            compose.runOnIdle { backend.setVideoSpeed(value) }
            await("实际 mpv 倍速 $value") { kotlin.math.abs(it.speed - value) < .002f }
        }
        compose.runOnIdle { backend.setDecoderMode(DecoderMode.SOFTWARE) }
        await("重建保留 10x") { it.phase == PlaybackPhase.READY && it.speed == 10f && !it.isPlaying }
        compose.runOnIdle { backend.setVideoSpeed(.1f) }
        await("返回最低速度") { it.speed == .1f }
        var token: Long? = null
        compose.runOnIdle { token = backend.beginSpeedBoost() }
        await("临时绝对 3x") { it.speed == 3f }
        compose.runOnIdle { backend.endSpeedBoost(token!!, .1f) }
        await("松手恢复 0.1x") { it.speed == .1f }
    }
    @Test fun chaptersFrameStepOffsetsAndTrackRebuild() {
        assertEquals(3, backend.state.value.chapters.size)
        assertEquals(4000L, backend.state.value.chapters[1].positionMs)
        val before = backend.state.value.positionMs
        compose.runOnIdle { backend.stepFrame(true) }
        await("真实前进一步") { it.positionMs > before + 20 && !it.isPlaying }
        val forward = backend.state.value.positionMs
        assertTrue("24 fps 单帧不能是普通大幅 seek", forward - before < 80)
        compose.runOnIdle { backend.stepFrame(false) }
        await("真实回退一帧") { it.positionMs < forward - 20 && !it.isPlaying }
        compose.runOnIdle {
            backend.setVideoDelay(true, 600); backend.setVideoDelay(false, -300)
            backend.setVideoSubtitleFont("monospace", true); backend.setVideoDeband(true)
            val track = backend.state.value.tracks.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }.last()
            backend.selectTrack(track.type, track.group, generation = backend.state.value.mediaGeneration)
        }
        await("真实 mpv 时间偏移、字体、色阶和音轨") { it.subtitleDelayMs == 600L && it.audioDelayMs == -300L &&
            it.subtitleFont == "monospace" && it.overrideAssStyle && it.deband && it.tracks.last { t -> t.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }.selected }
        compose.runOnIdle { backend.setDecoderMode(DecoderMode.SOFTWARE) }
        await("重建保留偏移和暂停") { it.phase == PlaybackPhase.READY && it.subtitleDelayMs == 600L && !it.isPlaying }
        compose.runOnIdle { backend.seekTo(backend.state.value.chapters[2].positionMs) }
        await("章节跳转") { it.chapterIndex == 2 && it.positionMs >= 7900 }
    }
    @Test fun libassPixelsSurfaceRecreationAndBackendSwitch() {
        Thread.sleep(300)
        fun pixels(name: String): Int {
          var magenta = 0
          compose.runOnIdle {
            val bitmap = view!!.texture.bitmap!!
            File(context.cacheDir, "v16-ass-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            for (y in bitmap.height / 4 until bitmap.height * 3 / 4 step 2) for (x in bitmap.width / 4 until bitmap.width * 3 / 4 step 2) {
                val c = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(c) > 180 && android.graphics.Color.blue(c) > 180 && android.graphics.Color.green(c) < 80) magenta++
            }
            bitmap.recycle()
          }
          return magenta
        }
        val withSubtitle = pixels("on")
        compose.runOnIdle { backend.selectTrack(androidx.media3.common.C.TRACK_TYPE_TEXT, null, generation = backend.state.value.mediaGeneration) }
        await("关闭 ASS") { it.tracks.none { t -> t.type == androidx.media3.common.C.TRACK_TYPE_TEXT && t.selected } }
        Thread.sleep(300)
        val withoutSubtitle = pixels("off")
        assertTrue("ASS 开关必须改变实际字幕像素，with=$withSubtitle without=$withoutSubtitle", withSubtitle > withoutSubtitle + 50)
        val position = backend.state.value.positionMs
        compose.runOnIdle { active = false }
        await("Surface 分离后播放状态仍真实") { it.phase == PlaybackPhase.READY && !it.isPlaying }
        compose.runOnIdle { active = true }
        await("Surface 重建保留位置") { it.phase == PlaybackPhase.READY && !it.isPlaying && kotlin.math.abs(it.positionMs - position) < 100 }
        compose.runOnIdle { backend.setVideoEngine(VideoEngine.MEDIA3) }
        await("回退 Media3") { it.videoEngine == VideoEngine.MEDIA3 && it.phase == PlaybackPhase.READY && !it.isPlaying && kotlin.math.abs(it.positionMs - position) < 120 }
        compose.runOnIdle { backend.setVideoEngine(VideoEngine.MPV) }
        await("重新启用 libmpv") { it.videoEngine == VideoEngine.MPV && it.phase == PlaybackPhase.READY && !it.isPlaying }
    }
    @Test fun actualSessionControlsAndNativeMusicOutputAfterVideo() {
        lateinit var session: androidx.media3.session.MediaSession
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>
        compose.runOnIdle {
            session = androidx.media3.session.MediaSession.Builder(context, backend.exoPlayer()).setId("v16-test-session").build()
            backend.onPlayerReplaced = { session.setPlayer(it) }
            future = androidx.media3.session.MediaController.Builder(context, session.token).buildAsync()
        }
        val controller = future.get(10, java.util.concurrent.TimeUnit.SECONDS)
        try {
            compose.runOnIdle { controller.seekTo(6000); controller.play() }
            await("系统会话控制 mpv 定位和续播") { it.isPlaying && it.positionMs > 6200 }
            compose.runOnIdle { controller.pause() }
            await("系统会话暂停 mpv") { !it.isPlaying }
            compose.runOnIdle { controller.stop() }
            await("系统会话停止 mpv") { it.phase == PlaybackPhase.IDLE }
            compose.runOnIdle { controller.prepare(); controller.play() }
            await("停止后重新准备和续播") { it.isPlaying && it.positionMs >= 6000 }
            compose.runOnIdle { controller.pause() }
            val file = File(context.cacheDir, "v16-tone.wav")
            instrumentation.context.assets.open("v16-tone.wav").use { input -> file.outputStream().use(input::copyTo) }
            compose.runOnIdle {
                backend.setOutputPath(yy.daniel.yyplayerforandroid.audio.OutputPath.NATIVE_EXACT)
                backend.play(listOf(LocalMedia(Uri.fromFile(file), "Native music fixture", MediaKind.AUDIO, "audio/wav")))
            }
            await("mpv 释放后 native PCM 音乐正常播放") { !it.isVideo && it.isPlaying && it.positionMs > 300 && it.negotiatedOutput != null }
            compose.runOnIdle { controller.pause() }
            await("同一系统会话已切换到音乐") { !it.isPlaying }
            compose.runOnIdle { assertEquals("Native music fixture", controller.mediaMetadata.title.toString()) }
        } finally { compose.runOnIdle { backend.onPlayerReplaced = null; controller.release(); session.release() } }
    }
    @Test fun nativeAbLoopAndAbsoluteBoostRestore() {
        compose.runOnIdle { backend.seekTo(1000) }
        await("A 起点") { kotlin.math.abs(it.positionMs - 1000) < 70 }
        compose.runOnIdle { backend.markLoop(); backend.seekTo(1700) }
        await("B 终点") { kotlin.math.abs(it.positionMs - 1700) < 70 }
        compose.runOnIdle { backend.markLoop(); backend.exoPlayer().play() }
        var wraps = 0; var previous = 0L
        await("libmpv A–B 至少回绕两次") {
            if (it.positionMs < previous - 250) wraps++
            previous = it.positionMs
            wraps >= 2
        }
        var token = 0L
        compose.runOnIdle { backend.clearLoop(); backend.setSpeed(2f); token = backend.beginSpeedBoost()!! }
        await("绝对 3 倍") { it.speed == 3f }
        compose.runOnIdle { backend.endSpeedBoost(token, 2f) }
        await("恢复基础 2 倍") { it.speed == 2f }
        val generation = backend.state.value.mediaGeneration
        compose.runOnIdle { backend.play(listOf(media)); backend.endSpeedBoost(token, 2f) }
        await("旧 boost 不能改新媒体") { it.mediaGeneration > generation && it.phase == PlaybackPhase.READY && it.speed == 1f }
        compose.runOnIdle { backend.clearQueue(); active = false }
        await("清空队列时安全分离画面") { it.queue.isEmpty() && it.phase == PlaybackPhase.IDLE }
        compose.runOnIdle { backend.play(listOf(media)); active = true }
        await("清空后重新打开原生视频") { it.phase == PlaybackPhase.READY && it.videoWidth > 0 }
    }
    @Test fun expandedToolsAndSyncControlsDriveRealEngine() {
        compose.runOnIdle { page = true }
        await("完整视频页") { it.phase == PlaybackPhase.READY }
        compose.onNodeWithContentDescription("播放工具").performClick()
        compose.onNodeWithText("章节").performClick()
        compose.onNodeWithText("Middle").performClick()
        await("章节菜单控制真实定位") { it.chapterIndex == 1 }
        compose.onNodeWithContentDescription("播放工具").performClick()
        compose.onNodeWithText("音画与字幕同步").performScrollTo().performClick()
        compose.onNodeWithText("字幕 延后 100 毫秒").performScrollTo().performClick()
        await("同步菜单控制真实偏移") { it.subtitleDelayMs == 100L }
        compose.onNodeWithContentDescription("返回上一级").performClick()
        compose.onNodeWithText("字幕与音轨").performClick()
        repeat(8) {
            if (!compose.onNodeWithText("字幕同步").isDisplayed()) {
                compose.onNodeWithTag("video-settings-content").performTouchInput { swipeUp(durationMillis = 400) }
            }
        }
        compose.onNodeWithText("字幕同步").assertIsDisplayed().performClick()
        compose.onNodeWithText("字幕 偏移").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回上一级").performClick()
        compose.onNodeWithText("字幕与音轨").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回上一级").performClick()
        compose.onNodeWithText("视频后端").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回上一级").assertDoesNotExist()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithContentDescription("播放工具").performClick()
        compose.waitForIdle(); Thread.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.cacheDir, "v16-tools.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
