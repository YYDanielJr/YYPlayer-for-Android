package yy.daniel.yyplayerforandroid

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import yy.daniel.yyplayerforandroid.ui.*
import yy.daniel.yyplayerforandroid.ui.theme.YYPlayerTheme

class LyricStyleUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun bilingualSettingsSaveIndependentlyAndPreviewTranslation() {
        val style = mutableStateOf(LyricStyle())
        compose.setContent { YYPlayerTheme { LyricStyleScreen(style.value, { style.value = it }, {}) } }
        compose.onNodeWithTag("lyric-style-list").performScrollToNode(hasContentDescription("当前译文字号"))
        compose.onNodeWithContentDescription("当前译文字号").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(24f) }
        compose.onNodeWithTag("lyric-style-list").performScrollToNode(hasContentDescription("原文与译文间距"))
        compose.onNodeWithContentDescription("原文与译文间距").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(20f) }
        assertEquals(24f, style.value.translationActiveSize)
        assertEquals(20f, style.value.bilingualSpacing)
        assertEquals(20f, style.value.translationSize)
        assertEquals(28f, style.value.activeSize)
        assertEquals(14f, style.value.lineSpacing)
    }
    @Test fun accessibilitySizeChangeSavesAndRemainsIndependentOfInactiveLine() {
        val style = mutableStateOf(LyricStyle())
        compose.setContent { YYPlayerTheme { LyricStyleScreen(style.value, { style.value = it }, {}) } }
        compose.onNodeWithContentDescription("选中行字号").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(36f) }
        assertEquals(36f, style.value.activeSize)
        assertEquals(28f, style.value.inactiveSize)
        compose.onNodeWithTag("lyric-style-list").performScrollToNode(hasContentDescription("非选中行字号"))
        compose.onNodeWithContentDescription("非选中行字号")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(22f) }
        assertEquals(36f, style.value.activeSize)
        assertEquals(22f, style.value.inactiveSize)
    }
    @Test fun presetColorOnlyChangesItsTargetAndResetRestoresBoth() {
        val style = mutableStateOf(LyricStyle())
        compose.setContent { YYPlayerTheme { LyricStyleScreen(style.value, { style.value = it }, {}) } }
        compose.onNodeWithText("选中行颜色").performScrollTo().performClick()
        compose.onNodeWithText("青绿").performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.waitUntil(5000) { style.value.activeColor != null || style.value.inactiveColor != null }
        assertEquals(style.value.toString(), 0xff18a27bL, style.value.activeColor)
        assertNull(style.value.inactiveColor)
        compose.onNodeWithTag("lyric-style-list").performScrollToNode(hasText("恢复默认歌词样式"))
        compose.onNodeWithText("恢复默认歌词样式").performClick()
        assertEquals(LyricStyle(), style.value)
    }
}
