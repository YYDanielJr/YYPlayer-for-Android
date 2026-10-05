package yy.daniel.yyplayerforandroid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Reopen the real setup, preserving each original user preference and library row. */
class OnboardingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun optionalPermissionsOnlineChoicesAndCompletionStayInExistingActivity() {
        val runtime = compose.activity.runtime
        val original = runtime.preferences.value
        val library = runtime.library.load()
        val storage = compose.activity.getSharedPreferences("ui_preferences_v1", 0)
        val hadThemeKey = storage.contains("theme")
        try {
            compose.runOnIdle { runtime.setup.restart() }
            compose.onNodeWithText("开始设置").performClick()
            compose.onNodeWithText("选择音乐文件夹").assertIsDisplayed()
            compose.onNodeWithText("选择歌曲文件").assertIsDisplayed()
            compose.onNodeWithText("继续").performClick()
            compose.onNodeWithText("联网搜索歌曲").assertIsDisplayed()
            compose.onNodeWithText("联网搜索歌词").assertIsDisplayed()
            compose.onNodeWithText("导入 API 配置").assertIsDisplayed()
            compose.onNodeWithText("继续").performClick()
            compose.onNodeWithText("浅色").performClick()
            compose.runOnIdle { assertEquals(yy.daniel.yyplayerforandroid.ui.ThemeChoice.LIGHT, runtime.preferences.value.theme) }
            compose.onNodeWithText("深色").performClick()
            compose.runOnIdle { assertEquals(yy.daniel.yyplayerforandroid.ui.ThemeChoice.DARK, runtime.preferences.value.theme) }
            compose.runOnIdle { runtime.setTheme(original.theme) }
            compose.onNodeWithText("继续").performClick()
            compose.onNodeWithText("准备好了").assertIsDisplayed()
            compose.onNodeWithText("开始使用").performClick()
            compose.onNodeWithContentDescription("添加音乐").assertIsDisplayed()
            assertTrue(runtime.setup.completed.value)
            assertEquals(original, runtime.preferences.value)
            assertEquals(library, runtime.library.load())
        } finally {
            compose.runOnIdle { runtime.setTheme(original.theme); runtime.setup.finish() }
            if (!hadThemeKey) storage.edit().remove("theme").commit()
        }
    }
}
