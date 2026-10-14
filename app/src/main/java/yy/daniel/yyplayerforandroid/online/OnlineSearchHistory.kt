package yy.daniel.yyplayerforandroid.online

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal data class SearchHistoryState(val keep: Boolean = true, val queries: List<String> = emptyList()) {
    fun record(query: String): SearchHistoryState {
        val text = query.trim()
        if (!keep || text.isEmpty() || text.length > 200) return this
        return copy(queries = (listOf(text) + queries.filterNot { it.equals(text, ignoreCase = true) }).take(20))
    }
}

/** Separate local preference: deleting searches never touches provider credentials or library data. */
internal class OnlineSearchHistory(context: Context) {
    private val preferences = context.getSharedPreferences("online_search_history_v1", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state = _state.asStateFlow()

    private fun read(): SearchHistoryState = runCatching {
        val text = preferences.getString("state", null) ?: return@runCatching SearchHistoryState()
        require(text.length <= 16384)
        val json = JSONObject(text)
        require(json.getInt("version") == 1 && json.get("keep") is Boolean)
        val entries = json.getJSONArray("queries")
        var result = SearchHistoryState()
        for (i in (minOf(entries.length(), 20) - 1) downTo 0) {
            (entries.opt(i) as? String)?.let { result = result.record(it) }
        }
        result.copy(keep = json.getBoolean("keep"))
    }.getOrDefault(SearchHistoryState())

    @Synchronized fun record(query: String) = update(_state.value.record(query))
    @Synchronized fun setKeep(keep: Boolean) = update(_state.value.copy(keep = keep))
    @Synchronized fun clear() = update(_state.value.copy(queries = emptyList()))
    @Synchronized fun remove(query: String) = update(_state.value.copy(queries = _state.value.queries.filterNot { it == query }))

    private fun update(value: SearchHistoryState) {
        if (value == _state.value) return
        _state.value = value
        val json = JSONObject().put("version", 1).put("keep", value.keep).put("queries", JSONArray(value.queries))
        preferences.edit { putString("state", json.toString()) }
    }
}
