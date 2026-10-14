package yy.daniel.yyplayerforandroid.playback

import org.junit.Assert.*
import org.junit.Test

class MusicCollectionsTest {
    @Test fun identityOrderDuplicatesAndRename() {
        val first = MusicCollections().create("  晚风  ")
        val id = first.lists.single().id
        val list = first.add(id, listOf("content://a", "content://b", "content://a"))
        assertEquals(listOf("content://a", "content://b"), list.lists.single().members)
        assertEquals(listOf("content://b", "content://a"), list.move(id, "content://b", -1).lists.single().members)
        assertEquals(id, list.rename(id, "通勤").lists.single().id)
        assertEquals(listOf("content://b"), list.remove(id, setOf("content://a")).lists.single().members)
        assertTrue(list.delete(id).lists.isEmpty())
        assertTrue(first.lists.single().members.isEmpty())
    }
    @Test fun validationAndLimits() {
        val lists = MusicCollections().create("Mix")
        assertTrue(runCatching { lists.create(" mix ") }.isFailure)
        listOf("", " ", "a\nb", "x".repeat(81)).forEach { assertTrue(runCatching { lists.create(it) }.isFailure) }
        assertTrue(runCatching { lists.add(lists.lists.single().id, List(20_001) { "content://$it" }) }.isFailure)
        assertTrue(runCatching { MusicCollections(List(129) { MusicPlaylist(name = "歌单$it") }).validated() }.isFailure)
    }
    @Test fun bilingualSeparatorMatchesDesktop() {
        assertEquals("Stay with me" to "请留在我身边", bilingualParts("Stay with me / 请留在我身边"))
        assertEquals("夜の風" to "晚风", bilingualParts("  夜の風 / 晚风  "))
        listOf("https://music.example/a/b", "AC/DC", "2026/10/13", " / ", " / tail", "head / ").forEach {
            assertEquals(it to "", bilingualParts(it))
        }
    }
}
