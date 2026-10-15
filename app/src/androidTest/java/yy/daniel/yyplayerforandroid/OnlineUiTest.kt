package yy.daniel.yyplayerforandroid

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

/** Exercise real production navigation/gates without deleting imports or leaving preference changes. */
class OnlineUiTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(QaForegroundRule()).around(compose)

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    @Test fun streamingCoverPopsToRetainedSearchAndHomeStillCollapses() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("onlineQa") == "true")
        val home = compose.activity
        check(home.packageName.endsWith(".qa"))
        val runtime = home.runtime
        val original = runtime.preferences.value
        val context = home.applicationContext
        val configFile = File(context.noBackupFilesDir, "online-api/providers.json")
        val saved = configFile.takeIf { it.exists() }?.readBytes()
        val imported = File.createTempFile("u23-online-", ".json", context.cacheDir)
        val root = JSONObject(context.assets.open("online-providers.json").bufferedReader().use { it.readText() })
        root.getJSONArray("providers").getJSONObject(0)
            .put("id", "yyplayer-qa").put("name", "YYPlayer 自有 HTTPS 验收").put("baseUrl", "https://localhost:18443/api")
            .put("requestsPerWindow", 1000)
            .put("headers", JSONObject().put("X-QA-Api", "fixture-only"))
            .put("mediaHeaders", JSONObject().put("localhost", JSONObject().put("X-QA-Media", "fixture-media")))
        imported.writeText(root.toString())
        val i = InstrumentationRegistry.getInstrumentation()
        fun resumed(): android.app.Activity? {
            var activity: android.app.Activity? = null
            i.runOnMainSync { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).singleOrNull() }
            return activity
        }
        fun back() = i.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        try {
            runBlocking { runtime.online.importConfig(Uri.fromFile(imported)) }
            compose.runOnIdle { runtime.setup.finish(); runtime.setOnlineMusic(true); runtime.setOnlineLyrics(false); home.openOnlineSearch() }
            compose.waitUntil(5000) { resumed() is OnlineSearchActivity }
            var search = resumed() as OnlineSearchActivity
            compose.onNode(hasSetTextAction()).performTextInput("栈返回")
            compose.onNode(hasSetTextAction()).performImeAction()
            compose.waitUntil(10000) { compose.onAllNodesWithText("栈返回 01").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("online-search-results").performScrollToIndex(20)
            val title = "栈返回 24"
            fun result() = compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag("online-search-results")))
            result().assertIsDisplayed()
            val resultBounds = result().fetchSemanticsNode().boundsInRoot
            repeat(3) { pass ->
                result().performClick()
                compose.waitUntil(15000) { runtime.playback.state.value.isPlaying }
                assertSame("stream cover expands inside search", search, resumed())
                assertSame(runtime.playback, search.runtime.playback)
                assertFalse(search.isFinishing)
                assertTrue(runtime.playback.state.value.current!!.isOnline)
                val generation = runtime.playback.state.value.mediaGeneration
                compose.onNodeWithContentDescription("专辑封面，切换到歌词").assertIsDisplayed()
                if (pass == 2) {
                    // Expanded state and the underlying results survive configuration recreation.
                    val previous = search
                    i.runOnMainSync { search.recreate() }
                    compose.waitUntil(5000) { resumed() is OnlineSearchActivity && resumed() !== previous }
                    search = resumed() as OnlineSearchActivity
                    compose.onNodeWithContentDescription("收起音乐详情").assertIsDisplayed()
                    compose.onNodeWithContentDescription("播放队列").performClick()
                    compose.waitUntil(5000) { resumed() is MusicQueueActivity }
                    back()
                    compose.waitUntil(5000) { resumed() === search }
                    compose.onNodeWithContentDescription("收起音乐详情").assertIsDisplayed()
                }
                if (pass == 1) back() else compose.onNodeWithContentDescription("收起音乐详情").performClick()
                compose.waitUntil(5000) { resumed() === search }
                assertFalse(search.isFinishing)
                compose.onNode(hasSetTextAction()).assertTextEquals("栈返回")
                result().assertIsDisplayed()
                assertEquals("same result and scroll offset", resultBounds, result().fetchSemanticsNode().boundsInRoot)
                assertEquals("return must not reload or stop the stream", generation, runtime.playback.state.value.mediaGeneration)
                assertTrue(runtime.playback.state.value.isPlaying)
                compose.onAllNodesWithTag("library-mini-player").assertCountEquals(1)
                val dock = compose.onNodeWithTag("library-mini-player")
                dock.assertIsDisplayed()
                val restingBounds = dock.fetchSemanticsNode().boundsInRoot
                assertTrue("search viewport continues under the floating dock",
                    compose.onNodeWithTag("online-search-results").fetchSemanticsNode().boundsInRoot.bottom > restingBounds.center.y)
                assertTrue("real result rows are drawn beneath the dock",
                    compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("online-search-results")))
                        .fetchSemanticsNodes().any { it.boundsInRoot.overlaps(restingBounds) })
                compose.onNodeWithContentDescription("暂停").performClick()
                compose.waitUntil(5000) { !runtime.playback.state.value.isPlaying }
                compose.onNodeWithContentDescription("播放").performClick()
                compose.waitUntil(5000) { runtime.playback.state.value.isPlaying }
                assertEquals("dock taps must not click through to an underlying result", generation, runtime.playback.state.value.mediaGeneration)
                if (pass == 0) {
                    if (InstrumentationRegistry.getArguments().getString("u07Evidence") == "true") {
                        val folder = File(home.cacheDir, "u07-evidence").apply { mkdirs() }
                        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                        File(folder, "u24-online-search.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    }
                    // Reopen from the persistent dock itself, retaining the same search instance.
                    dock.performClick()
                    compose.onNodeWithContentDescription("收起音乐详情").assertIsDisplayed()
                    assertSame(search, resumed())
                    compose.onAllNodesWithTag("library-mini-player").assertCountEquals(0)
                    compose.onNodeWithContentDescription("收起音乐详情").performClick()
                    compose.waitUntil(5000) { resumed() === search }
                    assertEquals(generation, runtime.playback.state.value.mediaGeneration)
                    compose.onNode(hasSetTextAction()).performClick()
                    compose.waitUntil(5000) {
                        var visible = false
                        i.runOnMainSync { visible = androidx.core.view.ViewCompat.getRootWindowInsets(search.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
                        visible
                    }
                    compose.waitForIdle()
                    val raised = dock.fetchSemanticsNode().boundsInRoot
                    assertTrue("dock rises above the real keyboard", raised.bottom < restingBounds.bottom)
                    assertTrue(compose.onNodeWithTag("online-search-results").fetchSemanticsNode().boundsInRoot.bottom > raised.center.y)
                    dock.performClick() // opening from a raised dock dismisses IME and retains the page
                    compose.onNodeWithContentDescription("收起音乐详情").assertIsDisplayed()
                    assertSame(search, resumed())
                    back() // collapse inline cover, not the search page
                    compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
                    compose.waitUntil(5000) { dock.fetchSemanticsNode().boundsInRoot == restingBounds }
                    assertSame(search, resumed())
                }
            }
            val dockBounds = compose.onNodeWithTag("library-mini-player").fetchSemanticsNode().boundsInRoot
            // Real finger scrolling travels behind a stationary dock. Tail padding still lets
            // the last result escape from underneath it, rather than clipping the viewport.
            compose.onNodeWithTag("online-search-results").performTouchInput { swipeUp() }
            assertEquals(dockBounds, compose.onNodeWithTag("library-mini-player").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithTag("online-search-results").performScrollToIndex(31)
            val lastResult = compose.onNode(hasText("栈返回 32") and hasAnyAncestor(hasTestTag("online-search-results")))
            lastResult.assertIsDisplayed()
            assertTrue("last result can scroll clear of the overlay", lastResult.fetchSemanticsNode().boundsInRoot.bottom < dockBounds.top)
            back()
            compose.waitUntil(5000) { resumed() === home }
            compose.onNodeWithTag("library-mini-player").performClick()
            compose.onNodeWithContentDescription("收起音乐详情").performClick()
            compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
            assertSame(home, resumed())
            assertFalse(home.isFinishing)
        } finally {
            i.runOnMainSync {
                runtime.playback.exoPlayer().pause()
                runtime.setOnlineMusic(original.onlineMusic); runtime.setOnlineLyrics(original.onlineLyrics)
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filter { it !== home }.forEach { it.finish() }
            }
            if (saved == null) configFile.delete() else configFile.writeBytes(saved)
            runBlocking { runtime.online.load() }
            imported.delete()
        }
    }
    @Test fun onlineMusicEntryTracksItsOwnSwitch() {
        val runtime = compose.activity.runtime
        val original = runtime.preferences.value
        try {
            if (compose.activity.packageName.endsWith(".qa")) compose.runOnIdle { runtime.setup.finish() }
            compose.runOnIdle { runtime.setOnlineMusic(false); runtime.setOnlineLyrics(true) }
            compose.onAllNodesWithContentDescription("在线搜索").assertCountEquals(0)
            compose.runOnIdle { runtime.setOnlineMusic(true) }
            compose.onNodeWithContentDescription("在线搜索").assertIsDisplayed()
            compose.onNodeWithContentDescription("在线搜索").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("搜索在线歌曲").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("搜索在线歌曲").assertIsDisplayed()
            compose.runOnIdle { runtime.setOnlineMusic(false) }
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("添加音乐").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithContentDescription("在线搜索").assertCountEquals(0)
        } finally { compose.runOnIdle { runtime.setOnlineMusic(original.onlineMusic); runtime.setOnlineLyrics(original.onlineLyrics) } }
    }
    @Test fun realHttpsResultsOfferPlaybackAndDownloadQuality() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("onlineQa") == "true")
        val runtime = compose.activity.runtime
        val original = runtime.preferences.value
        val context = compose.activity.applicationContext
        val configFile = File(context.noBackupFilesDir, "online-api/providers.json")
        val saved = configFile.takeIf { it.exists() }?.readBytes()
        val import = File(context.cacheDir, "online-ui-fixture.json")
        val root = JSONObject(context.assets.open("online-providers.json").bufferedReader().use { it.readText() })
        val p = root.getJSONArray("providers").getJSONObject(0)
        p.put("id", "yyplayer-qa").put("name", "YYPlayer 自有 HTTPS 验收").put("baseUrl", "https://localhost:18443/api")
            .put("headers", JSONObject().put("X-QA-Api", "fixture-only"))
            .put("mediaHeaders", JSONObject().put("localhost", JSONObject().put("X-QA-Media", "fixture-media")))
        import.writeText(root.toString())
        try {
            runBlocking { runtime.online.importConfig(Uri.fromFile(import)) }
            compose.runOnIdle { runtime.setOnlineMusic(true); runtime.setOnlineLyrics(true); compose.activity.openOnlineSearch() }
            compose.waitUntil(5000) { compose.onAllNodesWithText("搜索在线歌曲").fetchSemanticsNodes().isNotEmpty() }
            var search: android.app.Activity? = null
            compose.runOnIdle { search = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single() }
            compose.onNode(hasSetTextAction()).performTextInput("原创测试")
            compose.onNode(hasSetTextAction()).performImeAction()
            compose.waitUntil(10000) { compose.onAllNodesWithText("原创测试").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("原创测试 的选项").performClick()
            compose.onNodeWithText("下载歌曲").performClick()
            compose.onNodeWithText("下载音质").assertIsDisplayed()
            compose.waitUntil(10000) { compose.onAllNodesWithText("高品质 · 320 kbps").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("高品质 · 320 kbps").assertIsDisplayed()
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithContentDescription("原创测试 的选项").performClick()
            compose.onNodeWithText("选择播放音质").performClick()
            compose.onNodeWithText("播放音质").assertIsDisplayed()
            compose.waitUntil(10000) { compose.onAllNodesWithText("标准 · 128 kbps").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("标准 · 128 kbps").performClick()
            compose.waitUntil(15000) { runtime.playback.state.value.isPlaying }
            compose.onNodeWithContentDescription("收起音乐详情").assertIsDisplayed()
            compose.runOnIdle { assertSame(search, ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()) }
            assertTrue(runtime.playback.state.value.current!!.isOnline)
            compose.runOnIdle { runtime.playback.togglePlayPause() }
            compose.onNodeWithContentDescription("收起音乐详情").performClick()
            compose.runOnIdle { assertSame(search, ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()) }
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        } finally {
            compose.activity.runOnUiThread { runtime.setOnlineMusic(original.onlineMusic); runtime.setOnlineLyrics(original.onlineLyrics) }
            if (saved == null) configFile.delete() else configFile.writeBytes(saved)
            runBlocking { runtime.online.load() }
            import.delete()
        }
    }
}
