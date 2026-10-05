package yy.daniel.yyplayerforandroid

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import yy.daniel.yyplayerforandroid.playback.*
import yy.daniel.yyplayerforandroid.ui.*
import yy.daniel.yyplayerforandroid.ui.theme.YYPlayerTheme
import kotlin.math.abs

/**
 * A tab change may only move the content below the tab row: the title swaps its text with a fade and
 * the header keeps its exact bounds, so the page never slides sideways as one block.
 */
class LibraryTabsUiTest {
    @get:Rule val compose = createComposeRule()
    private val songs = listOf("分页测试甲").map {
        LocalMedia(Uri.parse("content://yyplayer-u08-test/$it"), "$it.wav", MediaKind.AUDIO, "audio/wav", songTitle = it, metadataRead = true)
    }
    private lateinit var pager: PagerState

    /** Same wiring as the production page: shared header plus a pager that only holds content. */
    private fun show() {
        compose.setContent { YYPlayerTheme {
            val pagerState = rememberPagerState(pageCount = { 2 })
            pager = pagerState
            val audio = remember { LibraryPageController() }
            val video = remember { LibraryPageController() }
            val scope = rememberCoroutineScope()
            HyperPage(title = if (pagerState.currentPage == 0) "音乐" else "视频",
                headerAlpha = { 1f - (abs(pagerState.currentPageOffsetFraction) * 2f).coerceIn(0f, 1f) },
                tabs = { LibraryTabs(pagerState.currentPage, onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } }) },
                actions = {
                    LibraryHeaderActions(isMusic = pagerState.currentPage == 0, managing = false, onSearch = {},
                        onOnline = {}, onSettings = {}, onImport = {}, onExitManagement = {})
                }) { padding ->
                HorizontalPager(pagerState, Modifier.fillMaxSize()) { index ->
                    LibraryContent(if (index == 0) MediaKind.AUDIO else MediaKind.VIDEO,
                        if (index == 0) audio else video, rememberLazyListState(), rememberLazyGridState(), padding,
                        if (index == 0) songs else emptyList(), PlaybackSnapshot(), emptySet(), false, false, "", {}, {}, {},
                        {}, {}, {}, 24.dp, isActive = index == pagerState.currentPage)
                }
            }
        } }
    }

    private fun headerBounds(): List<Rect> = compose.onAllNodes(hasText("音乐") or hasText("视频"))
        .fetchSemanticsNodes().map { it.boundsInRoot }.sortedWith(compareBy({ it.top }, { it.left }))

    private fun boundsOf(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test fun switchingTabsKeepsHeaderStillAndSlidesOnlyContent() {
        show()
        val barBefore = boundsOf("hyper-top-bar")
        val tabsBefore = boundsOf("library-tabs")
        val headerBefore = headerBounds()
        assertEquals(1, compose.onAllNodesWithText("全部音乐").fetchSemanticsNodes().size)

        compose.onNode(hasText("视频") and hasAnyAncestor(hasTestTag("library-tabs"))).performClick()
        compose.waitUntil(5_000) { pager.currentPage == 1 && !pager.isScrollInProgress }
        compose.waitForIdle()

        assertEquals(barBefore, boundsOf("hyper-top-bar"))
        assertEquals(tabsBefore, boundsOf("library-tabs"))
        assertEquals(headerBefore, headerBounds())
        assertEquals(0, compose.onAllNodesWithText("全部音乐").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("本地视频").fetchSemanticsNodes().size)
        compose.onNode(hasText("视频") and hasAnyAncestor(hasTestTag("library-tabs"))).assertIsSelected()
    }
}
