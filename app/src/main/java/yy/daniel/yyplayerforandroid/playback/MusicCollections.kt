package yy.daniel.yyplayerforandroid.playback

import java.util.Locale
import java.util.UUID

data class MusicPlaylist(val id: String = UUID.randomUUID().toString(), val name: String, val members: List<String> = emptyList())

/** Stable URI membership; editing a saved playlist never edits the active player timeline. */
data class MusicCollections(val lists: List<MusicPlaylist> = emptyList()) {
    fun validated(): MusicCollections {
        require(lists.size <= 128) { "最多创建 128 个歌单" }
        require(lists.map { it.id }.distinct().size == lists.size && lists.all { it.id.isNotBlank() })
        require(lists.map { key(it.name) }.distinct().size == lists.size) { "歌单名称已存在" }
        require(lists.sumOf { it.members.size } <= 20_000) { "歌单合计最多保存 20000 首歌曲" }
        lists.forEach {
            require(it.name.isNotBlank() && it.name.length <= 80 && it.name.none(Char::isISOControl)) { "歌单名称须为 1–80 个字符" }
            require(it.members.all(String::isNotBlank) && it.members.distinct().size == it.members.size)
        }
        return this
    }
    fun create(name: String) = copy(lists = lists + MusicPlaylist(name = name.trim())).validated()
    fun rename(id: String, name: String) = copy(lists = lists.map { if (it.id == id) it.copy(name = name.trim()) else it }).validated()
    fun delete(id: String) = copy(lists = lists.filterNot { it.id == id })
    fun add(id: String, members: List<String>) = edit(id) { (it + members).distinct() }.validated()
    fun remove(id: String, members: Set<String>) = edit(id) { it.filterNot(members::contains) }
    fun move(id: String, member: String, delta: Int) = edit(id) {
        val from = it.indexOf(member)
        if (from < 0) it else it.toMutableList().apply { add((from + delta).coerceIn(indices), removeAt(from)) }
    }
    private fun edit(id: String, transform: (List<String>) -> List<String>) = copy(lists = lists.map {
        if (it.id == id) it.copy(members = transform(it.members)) else it
    })
    companion object { fun key(text: String) = text.trim().lowercase(Locale.ROOT) }
}

data class MusicGroup(val key: Pair<String, String>, val name: String, val subtitle: String, val tracks: List<LocalMedia>)

fun groupMusic(tracks: List<LocalMedia>, albums: Boolean): List<MusicGroup> = tracks.groupBy {
    MusicCollections.key(it.artist.orEmpty()) to if (albums) MusicCollections.key(it.album.orEmpty()) else ""
}.map { (key, members) ->
    val artist = members.first().artist?.trim()?.takeIf(String::isNotBlank) ?: "未知歌手"
    MusicGroup(key, if (albums) members.first().album?.trim()?.takeIf(String::isNotBlank) ?: "未知专辑" else artist,
        if (albums) artist else "${members.map { MusicCollections.key(it.album.orEmpty()) }.distinct().size} 张专辑", members)
}.sortedBy { MusicCollections.key(it.name) }
