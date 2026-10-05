package yy.daniel.yyplayerforandroid

import android.net.Uri
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import yy.daniel.yyplayerforandroid.playback.*
import yy.daniel.yyplayerforandroid.ui.*
import yy.daniel.yyplayerforandroid.ui.theme.YYPlayerTheme

/** Production library UI, isolated test items/callbacks; no user media modified. */
class LibraryManagementUiTest {
    @get:Rule val compose = createComposeRule()
    private val songs = listOf("管理测试甲", "管理测试乙").map {
        LocalMedia(Uri.parse("content://yyplayer-u07-test/$it"), "$it.wav", MediaKind.AUDIO, "audio/wav", songTitle = it, metadataRead = true)
    }
    private var plays = 0
    private var batch: Pair<List<LocalMedia>, MusicBatchAction>? = null

    /** Mirrors the real page: header actions and tabs live outside the scrolling content. */
    private fun show() {
        compose.setContent { YYPlayerTheme {
            val page = remember { LibraryPageController() }
            HyperPage(title = "音乐", tabs = { LibraryTabs(0, {}) }, actions = {
                LibraryHeaderActions(isMusic = true, managing = page.managing, onSearch = {}, onOnline = null,
                    onSettings = {}, onImport = {}, onExitManagement = page::finish)
            }) { padding ->
                LibraryContent(MediaKind.AUDIO, page, rememberLazyListState(), rememberLazyGridState(), padding,
                    songs, PlaybackSnapshot(), emptySet(), false, false, "", {}, {}, {},
                    { plays++ }, {}, {}, 24.dp, onBatch = { items, action -> batch = items to action })
            }
        } }
    }

    @Test fun longPressSelectsWithoutPlayingAndBatchUsesBothSelections() {
        show()
        compose.onNodeWithText("管理测试甲").performTouchInput { longClick() }
        assertEquals(0, plays)
        compose.onNodeWithText("已选择 1 首").assertIsDisplayed()
        compose.onNodeWithText("管理测试乙").performClick()
        compose.onNodeWithText("已选择 2 首").assertIsDisplayed()
        compose.onNodeWithText("播放所选").performClick()
        assertEquals(songs, batch!!.first)
        assertEquals(MusicBatchAction.PLAY, batch!!.second)
        compose.onNodeWithContentDescription("添加音乐").assertIsDisplayed()
    }
    @Test fun removeNeedsExplicitConfirmationAndCancelRetainsSelection() {
        show()
        compose.onNodeWithText("管理测试甲").performTouchInput { longClick() }
        compose.onNodeWithText("全选当前列表").performClick()
        compose.onNodeWithText("移除").performClick()
        assertNull(batch)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("已选择 2 首").assertIsDisplayed()
        assertNull(batch)
        compose.onNodeWithText("移除").performClick()
        compose.onNodeWithText("移除 2 首歌曲").performClick()
        assertEquals(songs, batch!!.first)
        assertEquals(MusicBatchAction.REMOVE, batch!!.second)
    }
    @Test fun accessibleMenuCanStartManagementAndExitKeepsPlaybackUntouched() {
        show()
        compose.onNodeWithContentDescription("管理测试甲.wav 的选项").performClick()
        compose.onNodeWithText("多选管理").performClick()
        compose.onNodeWithText("已选择 1 首").assertIsDisplayed()
        compose.onNodeWithContentDescription("退出多选").performClick()
        assertNull(batch); assertEquals(0, plays)
        compose.onNodeWithContentDescription("添加音乐").assertIsDisplayed()
    }
}
