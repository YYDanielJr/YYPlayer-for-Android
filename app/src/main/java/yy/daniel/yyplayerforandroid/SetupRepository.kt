package yy.daniel.yyplayerforandroid

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** First-install marker is independent of theme changes made halfway through setup. */
internal class SetupRepository(context: Context) {
    private val storage = context.getSharedPreferences("setup_v1", Context.MODE_PRIVATE)
    init {
        if (!storage.contains("completed")) {
            val existing = context.getSharedPreferences("ui_preferences_v1", 0).all.isNotEmpty() ||
                context.getSharedPreferences("local_media_v1", 0).contains("items") ||
                context.getSharedPreferences("online_api_v1", 0).all.isNotEmpty()
            // An update must not interrupt users who already configured/imported their library.
            storage.edit { putBoolean("completed", existing) }
        }
    }
    private val _completed = MutableStateFlow(storage.getBoolean("completed", false))
    val completed = _completed.asStateFlow()
    fun finish() = setCompleted(true)
    fun restart() = setCompleted(false)
    private fun setCompleted(value: Boolean) {
        storage.edit { putBoolean("completed", value) }
        _completed.value = value
    }
}
