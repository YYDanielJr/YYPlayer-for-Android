package yy.daniel.yyplayerforandroid.playback

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// edit(commit=true) returns Unit; these writes must check the actual commit result before publishing.
@android.annotation.SuppressLint("UseKtx")
class MusicCollectionRepository(context: Context) {
    private val preferences = context.getSharedPreferences("music_collections_v1", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(MusicCollections())
    val state = mutable.asStateFlow()
    private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { read() } }
    suspend fun update(change: (MusicCollections) -> MusicCollections) = withContext(Dispatchers.IO) {
        mutex.withLock {
            read()
            val next = change(mutable.value).validated()
            val encoded = JSONObject().put("version", 1).put("lists", JSONArray().apply {
                next.lists.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("members", JSONArray(it.members))) }
            }).toString()
            require(encoded.toByteArray().size <= 2_000_000) { "歌单数据超过 2MB" }
            check(preferences.edit().putString("data", encoded).commit()) { "歌单保存失败，请重试" }
            mutable.value = next
        }
    }
    private fun read() {
        if (loaded) return
        val raw = preferences.getString("data", null)
        mutable.value = if (raw == null) MusicCollections() else try {
            require(raw.toByteArray().size <= 2_000_000)
            val root = JSONObject(raw)
            require(root.getInt("version") == 1)
            val lists = root.getJSONArray("lists")
            MusicCollections(List(lists.length()) { index ->
                val item = lists.getJSONObject(index)
                val members = item.getJSONArray("members")
                MusicPlaylist(item.getString("id"), item.getString("name"), List(members.length()) { members.getString(it) })
            }).validated()
        } catch (_: Exception) {
            check(preferences.edit().putString("bad_backup", raw).remove("data").commit())
            MusicCollections()
        }
        loaded = true
    }
}
