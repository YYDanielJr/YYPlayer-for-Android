package yy.daniel.yyplayerforandroid

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
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
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun onlineMusicEntryTracksItsOwnSwitch() {
        val runtime = compose.activity.runtime
        val original = runtime.preferences.value
        try {
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
            compose.onNode(hasSetTextAction()).performTextInput("原创测试")
            compose.onNode(hasSetTextAction()).performImeAction()
            compose.waitUntil(10000) { compose.onAllNodesWithText("原创测试").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("下载 原创测试").performClick()
            compose.onNodeWithText("下载音质").assertIsDisplayed()
            compose.waitUntil(10000) { compose.onAllNodesWithText("高品质 · 320 kbps").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("高品质 · 320 kbps").assertIsDisplayed()
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithContentDescription("播放 原创测试").performClick()
            compose.onNodeWithText("播放音质").assertIsDisplayed()
            compose.waitUntil(10000) { compose.onAllNodesWithText("标准 · 128 kbps").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("标准 · 128 kbps").performClick()
            compose.waitUntil(15000) { runtime.playback.state.value.isPlaying }
            val main = compose.activity
            compose.waitUntil(5000) {
                var resumed = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it === main }
                }
                resumed
            }
            assertTrue(runtime.playback.state.value.current!!.isOnline)
            compose.runOnIdle { runtime.playback.togglePlayPause() }
        } finally {
            compose.activity.runOnUiThread { runtime.setOnlineMusic(original.onlineMusic); runtime.setOnlineLyrics(original.onlineLyrics) }
            if (saved == null) configFile.delete() else configFile.writeBytes(saved)
            runBlocking { runtime.online.load() }
            import.delete()
        }
    }
}
