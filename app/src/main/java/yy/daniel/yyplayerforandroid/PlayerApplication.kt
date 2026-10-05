package yy.daniel.yyplayerforandroid

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import yy.daniel.yyplayerforandroid.playback.AndroidMedia3PlaybackBackend
import yy.daniel.yyplayerforandroid.playback.MediaLibraryRepository
import yy.daniel.yyplayerforandroid.playback.MusicLyricsRepository
import yy.daniel.yyplayerforandroid.ui.*

/** One engine for the whole Activity stack. No Activity may release another Activity's player. */
class PlayerApplication : Application() {
    internal val runtime by lazy { PlayerRuntime(this) }
}

internal data class PlayerPreferences(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val accent: AccentChoice = AccentChoice.BLUE,
    val reduceMotion: Boolean = false,
    val orientation: OrientationChoice = OrientationChoice.AUTO,
    val favorites: Set<String> = emptySet(),
)

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal class PlayerRuntime(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storage = context.getSharedPreferences("ui_preferences_v1", 0)
    private val _preferences = MutableStateFlow(PlayerPreferences(
        theme = ThemeChoice.entries.firstOrNull { it.name == storage.getString("theme", "SYSTEM") } ?: ThemeChoice.SYSTEM,
        accent = AccentChoice.entries.firstOrNull { it.name == storage.getString("accent", "BLUE") } ?: AccentChoice.BLUE,
        reduceMotion = storage.getBoolean("reduce_motion", false),
        orientation = OrientationChoice.entries.firstOrNull { it.name == storage.getString("orientation", "AUTO") } ?: OrientationChoice.AUTO,
        favorites = storage.getStringSet("favorites", emptySet()).orEmpty().toSet(),
    ))
    val preferences = _preferences.asStateFlow()
    val library = MediaLibraryRepository(context.applicationContext)
    val lyrics = MusicLyricsRepository(context.applicationContext)
    private var engine: AndroidMedia3PlaybackBackend? = null
    val playback: AndroidMedia3PlaybackBackend get() = engine ?: AndroidMedia3PlaybackBackend(appContext).also { engine = it }
    private var owners = 0
    private var started = 0
    private var cleanup: Job? = null
    private var ticker: Job? = null
    private var pause: Job? = null

    fun acquire() { cleanup?.cancel(); owners++ }
    fun release(retainForConfiguration: Boolean) {
        owners--
        if (owners == 0 && !retainForConfiguration) cleanup = scope.launch {
            delay(700) // Allow a normal Activity hand-off to acquire the same engine.
            if (owners == 0) { ticker?.cancel(); engine?.close(); engine = null }
        }
    }
    fun start() {
        pause?.cancel()
        if (started++ == 0) ticker = scope.launch {
            while (isActive) { engine?.refreshPosition(); delay(300) }
        }
    }
    fun stop() {
        if (--started == 0) {
            ticker?.cancel()
            pause = scope.launch {
                delay(500) // Internal Activity hand-off must not pause the music.
                if (started == 0) engine?.let { if (it.state.value.isPlaying) it.togglePlayPause() }
            }
        }
    }
    fun setTheme(value: ThemeChoice) { _preferences.value = _preferences.value.copy(theme = value); storage.edit { putString("theme", value.name) } }
    fun setAccent(value: AccentChoice) { _preferences.value = _preferences.value.copy(accent = value); storage.edit { putString("accent", value.name) } }
    fun setReduceMotion(value: Boolean) { _preferences.value = _preferences.value.copy(reduceMotion = value); storage.edit { putBoolean("reduce_motion", value) } }
    fun setOrientation(value: OrientationChoice) { _preferences.value = _preferences.value.copy(orientation = value); storage.edit { putString("orientation", value.name) } }
    fun toggleFavorite(key: String) { setFavorites(_preferences.value.favorites.let { if (key in it) it - key else it + key }) }
    fun removeFavorite(key: String) { setFavorites(_preferences.value.favorites - key) }
    private fun setFavorites(value: Set<String>) { _preferences.value = _preferences.value.copy(favorites = value); storage.edit { putStringSet("favorites", value) } }
}
