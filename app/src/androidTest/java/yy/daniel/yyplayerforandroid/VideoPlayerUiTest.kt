package yy.daniel.yyplayerforandroid

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import yy.daniel.yyplayerforandroid.playback.*
import yy.daniel.yyplayerforandroid.ui.*
import yy.daniel.yyplayerforandroid.ui.theme.YYPlayerTheme
import java.io.File

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class VideoPlayerUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base = instrumentation.targetContext
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("v15_ui_$name", mode)
    }
    private lateinit var backend: AndroidMedia3PlaybackBackend
    private var compactLargeText by mutableStateOf(false)
    private var shortLandscape by mutableStateOf(false)
    private var animatedSettings by mutableStateOf(false)
    @Before fun setup() {
        val file = File(context.cacheDir, "v15-ui.mp4")
        instrumentation.context.assets.open("v15-video.mp4").use { input -> file.outputStream().use(input::copyTo) }
        compose.runOnUiThread {
            backend = AndroidMedia3PlaybackBackend(context)
            backend.setVideoEngine(VideoEngine.MEDIA3)
            backend.play(listOf(LocalMedia(Uri.fromFile(file), "色彩与节奏 · 测试短片", MediaKind.VIDEO, "video/mp4")))
        }
        compose.setContent {
            val snapshot by backend.state.collectAsState()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (compactLargeText || shortLandscape) 2f else density.fontScale)) {
                Box(if (compactLargeText) Modifier.width(360.dp).fillMaxHeight()
                    else if (shortLandscape) Modifier.fillMaxWidth().height(360.dp) else Modifier.fillMaxSize()) {
                    YYPlayerTheme(ColorSchemeMode.Dark) {
                        VideoPlayerPage(backend, snapshot, !animatedSettings, {}, {}, true, null)
                    }
                }
            }
        }
        compose.waitUntil(15000) { backend.state.value.isPlaying }
        compose.runOnIdle { backend.exoPlayer().pause(); backend.seekTo(1000) }
        compose.waitForIdle()
    }
    @After fun teardown() { compose.runOnUiThread { backend.close() } }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(300) // Wait for the hardware layer to submit the matrix to SurfaceFlinger.
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(base.getExternalFilesDir(null), "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun formerBarBrightness(): Int {
        val image = instrumentation.uiAutomation.takeScreenshot()
        val x = if (image.width > image.height) image.width / 12 else image.width / 2
        val y = if (image.width > image.height) image.height / 2 else image.height / 4
        val pixel = image.getPixel(x, y)
        image.recycle()
        return android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)
    }
    @Test fun customSpeedUsesTenthsAndTenTimesWhilePreservingQuickChoices() {
        fun awaitSpeed(expected: Float) {
            compose.waitUntil(15000) {
                compose.runOnUiThread { backend.refreshPosition() }
                kotlin.math.abs(backend.state.value.speed - expected) < .002f
            }
            compose.runOnIdle { assertNull(backend.state.value.error) }
        }
        compose.onNodeWithContentDescription("播放工具").performClick()
        compose.onNodeWithText("播放倍速").performClick()
        val slider = compose.onNodeWithContentDescription("自由调节播放倍速")
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(.1f) }
        awaitSpeed(.1f)
        compose.onNodeWithText("减 0.1×").assertIsNotEnabled()
        compose.onNodeWithText("加 0.1×").performClick()
        awaitSpeed(.2f)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(7.43f) }
        awaitSpeed(7.4f)
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("7.4×").performClick()
        listOf("0.5×", "1×", "1.25×", "1.5×", "2×", "3×").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("1.25×").performClick()
        awaitSpeed(1.25f)
        compose.onNodeWithContentDescription("播放工具").performClick()
        compose.onNodeWithText("播放倍速").performClick()
        compose.onNodeWithText("加 0.1×").performClick()
        awaitSpeed(1.3f)
        // Actual pointer drag and release must apply the maximum, not just update its label.
        slider.performTouchInput { swipe(center, Offset(width - 1f, center.y), durationMillis = 500) }
        awaitSpeed(10f)
        compose.runOnIdle {
            assertEquals(VideoEngine.MPV, backend.state.value.videoEngine)
            assertFalse(backend.state.value.isPlaying)
            assertTrue(kotlin.math.abs(backend.state.value.positionMs - 1000L) < 200L)
        }
        compose.onNodeWithText("加 0.1×").assertIsNotEnabled()
        capture("v19-custom-speed")
        var token: Long? = null
        compose.runOnIdle { token = backend.beginSpeedBoost() }
        awaitSpeed(3f)
        compose.runOnIdle { backend.endSpeedBoost(token!!, 10f) }
        awaitSpeed(10f)
        compose.onNodeWithText("恢复正常速度").performScrollTo().performClick()
        awaitSpeed(1f)
    }
    @Test fun landscapeSettingsStayOnRightAndDismissWithoutChangingPlayback() {
        val player = compose.onNodeWithTag("video-player").fetchSemanticsNode().boundsInRoot
        assertTrue("测试设备应为横屏", player.width > player.height)
        compose.onNodeWithContentDescription("播放工具").performClick()
        val drawer = compose.onNodeWithTag("video-settings-drawer").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(player.right, drawer.right, 1f)
        assertTrue("抽屉只占右侧", drawer.left > player.center.x)
        compose.onNodeWithContentDescription("播放").assertDoesNotExist() // Modal accessibility.
        capture("v17-landscape-tools")
        compose.onNodeWithText("字幕与音轨").performClick()
        compose.onNodeWithTag("video-settings-drawer").assertIsDisplayed()
        compose.onNodeWithText("字幕兼容性").performScrollTo().assertIsDisplayed()
        capture("v17-landscape-tracks")
        compose.onNodeWithText("完成").assertIsDisplayed().performClick()
        compose.onNodeWithTag("video-settings-drawer").assertDoesNotExist()
        compose.onNodeWithText("1×").performClick()
        compose.onNodeWithText("1.5×").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1.5f, backend.state.value.speed, .001f) }
        compose.onNodeWithTag("video-settings-drawer").assertDoesNotExist()
        compose.onNodeWithText("画面").performClick()
        compose.onNodeWithTag("video-settings-drawer").assertIsDisplayed()
        compose.waitForIdle()
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("视频后端").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回上一级").assertDoesNotExist()
        compose.onNodeWithTag("video-settings-scrim").performTouchInput { click(Offset(24f, center.y)) }
        compose.onNodeWithTag("video-settings-drawer").assertDoesNotExist()
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
        compose.runOnIdle { assertFalse(backend.state.value.isPlaying) }
    }
    @Test fun fingerSwipeScrollsSettingsAndHeaderReturnsToParent() {
        compose.runOnIdle { shortLandscape = true }
        compose.onNodeWithContentDescription("播放工具").performClick()
        val content = compose.onNodeWithTag("video-settings-content")
        val initialOffset = content.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        content.performTouchInput { swipeUp(durationMillis = 600) }
        val movedOffset = content.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("真实手指拖动必须滚动列表", movedOffset > initialOffset + 30f)
        repeat(12) {
            if (!compose.onNodeWithText("媒体信息").isDisplayed()) content.performTouchInput { swipeUp(durationMillis = 300) }
        }
        compose.onNodeWithText("媒体信息").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("返回上一级").assertIsDisplayed()
        capture("v18-media-back")
        compose.onNodeWithContentDescription("返回上一级").performClick()
        compose.onNodeWithText("视频后端").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回上一级").assertDoesNotExist()
        content.performTouchInput { swipeUp(durationMillis = 600) }
        val beforeDown = content.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        content.performTouchInput { swipeDown(durationMillis = 600) }
        val afterDown = content.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("反向拖动也必须滚动列表", afterDown < beforeDown)
        compose.onNodeWithText("字幕与音轨").performScrollTo().performClick()
        compose.onNodeWithContentDescription("返回上一级").assertIsDisplayed().performClick()
        compose.onNodeWithText("视频后端").assertIsDisplayed()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("video-settings-drawer").assertDoesNotExist()
    }
    @Test fun drawerSlidesFromRightAndBlocksInputWhileClosing() {
        compose.runOnIdle { animatedSettings = true }
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("播放工具").performClick()
            compose.mainClock.advanceTimeBy(64)
            val entering = compose.onNodeWithTag("video-settings-drawer").fetchSemanticsNode().boundsInRoot
            compose.mainClock.advanceTimeBy(400)
            val opened = compose.onNodeWithTag("video-settings-drawer").fetchSemanticsNode().boundsInRoot
            assertTrue("抽屉应由右边滑入", entering.left > opened.left)
            compose.onNodeWithText("完成").performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithText("视频后端").assertDoesNotExist()
            compose.onNodeWithTag("video-settings-scrim").performTouchInput { click(Offset(24f, center.y)) }
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag("video-settings-drawer").assertDoesNotExist()
            compose.runOnIdle { assertFalse(backend.state.value.isPlaying) }
        } finally { compose.mainClock.autoAdvance = true }
    }
    @Test fun settingsAdaptBetweenPortraitAndShortLandscapeAtLargeText() {
        compose.runOnIdle { compactLargeText = true }
        compose.onNodeWithText("字幕 / 音轨").performClick()
        compose.onNodeWithTag("video-settings-drawer").assertDoesNotExist()
        compose.onNodeWithText("完成").assertIsDisplayed()
        capture("v17-portrait-dialog")
        // Resize the actual content bounds while settings remain open (no global device settings).
        compose.runOnIdle { compactLargeText = false; shortLandscape = true }
        compose.onNodeWithTag("video-settings-drawer").assertIsDisplayed()
        compose.onNodeWithText("字幕兼容性").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("完成").assertIsDisplayed()
        capture("v17-short-large-text")
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("video-settings-drawer").assertDoesNotExist()
    }
    @Test fun viewportIsFullSizeAndResetReturnsAfterPinch() {
        compose.onNodeWithTag("video-reset").assertDoesNotExist()
        onView(isAssignableFrom(TextureView::class.java)).check { view, error ->
            if (error != null) throw error
            assertEquals("texture must span parent width", (view.parent as android.view.View).width, view.width)
            assertEquals("texture must span parent height", (view.parent as android.view.View).height, view.height)
        }
        capture("v15-player")
        assertTrue("原始画面边缘是黑边", formerBarBrightness() < 35)
        compose.onNodeWithTag("video-surface").performTouchInput {
            down(0, center + Offset(-50f, 0f)); down(1, center + Offset(50f, 0f))
            updatePointerTo(0, center + Offset(-110f, -25f)); updatePointerTo(1, center + Offset(110f, 25f)); move()
            up(0); up(1)
        }
        compose.onNodeWithTag("video-reset").assertIsDisplayed()
        onView(isAssignableFrom(TextureView::class.java)).check { view, error ->
            if (error != null) throw error
            val values = FloatArray(9)
            (view as TextureView).getTransform(android.graphics.Matrix()).getValues(values)
            println("V15_TRANSFORM ${values.toList()} viewport=${view.width}x${view.height}")
            assertTrue("真实视频纹理矩阵应放大", values[0] > 1.2f)
        }
        capture("v15-transformed")
        assertTrue("放大后的真实视频像素必须覆盖原黑边", formerBarBrightness() > 70)
        compose.onNodeWithTag("video-reset").performClick()
        compose.onNodeWithTag("video-reset").assertDoesNotExist()
        capture("v15-restored")
        assertTrue("还原后的画面保持原始比例", formerBarBrightness() < 35)
        compose.onNodeWithText("锁定").performClick()
        compose.onNodeWithText("解锁").assertIsDisplayed().performClick()
        compose.onNodeWithText("字幕 / 音轨").assertIsDisplayed()
    }
    @Test fun subtitleAndPicturePanelsHaveRealChoices() {
        compose.onNodeWithText("字幕 / 音轨").performClick()
        compose.onNodeWithText("导入字幕").assertExists()
        capture("v15-tracks")
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("画面", useUnmergedTree = true).performClick()
        compose.onNodeWithText("画面比例").assertExists()
        capture("v15-picture")
    }
    @Test fun compactViewportAtTwoHundredPercentKeepsActionsReachable() {
        compose.runOnIdle { compactLargeText = true }
        compose.onNodeWithText("字幕 / 音轨").assertIsDisplayed()
        compose.onNodeWithText("画面").assertIsDisplayed()
        compose.onNodeWithText("锁定").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
        capture("v15-large-text")
    }
}
