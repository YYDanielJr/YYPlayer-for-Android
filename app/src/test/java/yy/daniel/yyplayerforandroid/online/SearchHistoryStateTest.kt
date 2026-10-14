package yy.daniel.yyplayerforandroid.online

import org.junit.Assert.*
import org.junit.Test

class SearchHistoryStateTest {
    @Test fun recentQueriesAreTrimmedDeduplicatedAndBounded() {
        var history = SearchHistoryState().record("  夜曲  ").record("Hello").record("hello")
        assertEquals(listOf("hello", "夜曲"), history.queries)
        repeat(30) { history = history.record("歌曲 $it") }
        assertEquals(20, history.queries.size)
        assertEquals("歌曲 29", history.queries.first())
        assertEquals("歌曲 10", history.queries.last())
    }
    @Test fun disablingHistoryDoesNotRecordOrDeleteExistingQueries() {
        val history = SearchHistoryState().record("夜曲").copy(keep = false)
        assertSame(history, history.record("不保留"))
        assertEquals(listOf("夜曲"), history.queries)
        assertSame(history, history.record(" "))
        assertSame(history, history.record("歌".repeat(201)))
    }
}
