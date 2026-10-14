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
import yy.daniel.yyplayerforandroid.online.OnlineRepository
import yy.daniel.yyplayerforandroid.online.OnlineSong
import yy.daniel.yyplayerforandroid.playback.LocalMedia

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
    val onlineMusic: Boolean = false,
    val onlineLyrics: Boolean = false,
    val lyricStyle: yy.daniel.yyplayerforandroid.ui.LyricStyle = yy.daniel.yyplayerforandroid.ui.LyricStyle(),
    val audioSharing: yy.daniel.yyplayerforandroid.audio.SharingRequest = yy.daniel.yyplayerforandroid.audio.SharingRequest.SHARED,
    val audioTransparency: yy.daniel.yyplayerforandroid.audio.TransparencyRequest =
        yy.daniel.yyplayerforandroid.audio.TransparencyRequest.PREFER_UNPROCESSED,
    val audioOutputPath: yy.daniel.yyplayerforandroid.audio.OutputPath =
        yy.daniel.yyplayerforandroid.audio.OutputPath.SYSTEM,
)

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal class PlayerRuntime(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storage = context.getSharedPreferences("ui_preferences_v1", 0)
    val setup = SetupRepository(context)
    private val _preferences = MutableStateFlow(PlayerPreferences(
        theme = ThemeChoice.entries.firstOrNull { it.name == storage.getString("theme", "SYSTEM") } ?: ThemeChoice.SYSTEM,
        accent = AccentChoice.entries.firstOrNull { it.name == storage.getString("accent", "BLUE") } ?: AccentChoice.BLUE,
        reduceMotion = storage.getBoolean("reduce_motion", false),
        orientation = OrientationChoice.entries.firstOrNull { it.name == storage.getString("orientation", "AUTO") } ?: OrientationChoice.AUTO,
        favorites = storage.getStringSet("favorites", emptySet()).orEmpty().toSet(),
        onlineMusic = storage.getBoolean("online_music", false),
        onlineLyrics = storage.getBoolean("online_lyrics", false),
        lyricStyle = runCatching { yy.daniel.yyplayerforandroid.ui.LyricStyle.decode(storage.getString("lyric_style", null)) }
            .getOrElse {
                storage.edit { putString("lyric_style_bad_backup", storage.all["lyric_style"]?.toString()?.take(4096)); remove("lyric_style") }
                yy.daniel.yyplayerforandroid.ui.LyricStyle()
            },
        audioSharing = yy.daniel.yyplayerforandroid.audio.SharingRequest.entries
            .firstOrNull { it.name == storage.getString("audio_sharing", null) }
            ?: yy.daniel.yyplayerforandroid.audio.SharingRequest.SHARED,
        audioTransparency = yy.daniel.yyplayerforandroid.audio.TransparencyRequest.entries
            .firstOrNull { it.name == storage.getString("audio_transparency", null) }
            ?: yy.daniel.yyplayerforandroid.audio.TransparencyRequest.PREFER_UNPROCESSED,
        audioOutputPath = yy.daniel.yyplayerforandroid.audio.OutputPath.fromWire(
            storage.getString("audio_output_path", null),
        ),
    ))
    val preferences = _preferences.asStateFlow()
    val library = MediaLibraryRepository(context.applicationContext)
    val collections = yy.daniel.yyplayerforandroid.playback.MusicCollectionRepository(context.applicationContext)
    val lyrics = MusicLyricsRepository(context.applicationContext)
    val online = OnlineRepository(appContext, { preferences.value.onlineMusic }, { preferences.value.onlineLyrics })

    /**
     * Owns the audio output policy and the evidence about it. It follows the same owner count as the
     * engine so the device callback is only registered while a screen is alive.
     */
    val audioOutput: yy.daniel.yyplayerforandroid.audio.AudioOutputController by lazy {
        yy.daniel.yyplayerforandroid.audio.AudioOutputController(appContext) { engine?.stopForOutputLoss() }.also {
            val preferences = _preferences.value
            it.setPolicy(preferences.audioSharing, preferences.audioTransparency)
        }
    }
    val libraryRevision = MutableStateFlow(0L)
    init { scope.launch { online.load() }; scope.launch { collections.load() } }
    private var engine: AndroidMedia3PlaybackBackend? = null

    /**
     * The one playback engine of this process. Created here and borrowed by [playback.PlayerSessionService]
     * so the platform MediaSession and the in-app UI drive the same player: two engines would mean two
     * output streams and two clocks.
     */
    fun ensureEngine(): AndroidMedia3PlaybackBackend = engine ?: AndroidMedia3PlaybackBackend(
        appContext,
        { preferences.value.onlineMusic },
        { preferences.value.audioOutputPath },
        { audioOutput.state.value.selectedDeviceId },
        { negotiated ->
            // The native path reports what it really opened; the audio output screen shows it as evidence.
            audioOutput.publishNegotiatedOutput(negotiated)
        },
        { preferences.value.audioSharing },
        { preferences.value.audioTransparency },
    ).also { engine = it }

    val playback: AndroidMedia3PlaybackBackend get() = ensureEngine()

    /** Set while the media session service is alive, so the output path can be applied to it. */
    private var session: yy.daniel.yyplayerforandroid.playback.PlayerSessionService? = null

    internal fun attachSession(session: yy.daniel.yyplayerforandroid.playback.PlayerSessionService) {
        this.session = session
        acquire() // The service keeps the single engine alive after the last Activity is destroyed.
        ensureTicker()
    }

    internal fun detachSession(session: yy.daniel.yyplayerforandroid.playback.PlayerSessionService) {
        if (this.session === session) {
            this.session = null
            release(false)
            if (started == 0) ticker?.cancel()
        }
    }

    /**
     * Starts the media session service, which is what publishes the session to the system. Called when
     * playback is about to begin, because a session that nothing is playing into should not hold a
     * foreground service.
     */
    internal fun ensureSessionService() {
        runCatching {
            appContext.startService(
                android.content.Intent(appContext, yy.daniel.yyplayerforandroid.playback.PlayerSessionService::class.java),
            )
        }
    }
    private var owners = 0
    private var started = 0
    private var cleanup: Job? = null
    private var ticker: Job? = null
    private var pause: Job? = null
    private var onlineLyricJob: Job? = null
    private var onlineArtworkJob: Job? = null
    fun fetchOnlineArtwork(song: OnlineSong, media: LocalMedia) {
        onlineArtworkJob?.cancel()
        val generation = playback.state.value.mediaGeneration
        onlineArtworkJob = scope.launch {
            try {
                if (online.artwork(song) != null && engine?.state?.value?.let {
                        it.mediaGeneration == generation && it.current?.identity == media.identity
                    } == true) invalidateMediaArtwork()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Artwork failure must not delay or stop the music. */ }
        }
    }
    fun fetchOnlineLyrics(song: OnlineSong, media: LocalMedia) {
        onlineLyricJob?.cancel()
        onlineLyricJob = scope.launch { online.automaticLyrics(song, media, lyrics) }
    }

    fun acquire() { cleanup?.cancel(); if (owners == 0) audioOutput.start(); owners++ }
    fun release(retainForConfiguration: Boolean) {
        owners--
        if (owners == 0 && !retainForConfiguration) cleanup = scope.launch {
            delay(700) // Allow a normal Activity hand-off to acquire the same engine.
            if (owners == 0) {
                audioOutput.stop()
                ticker?.cancel(); onlineLyricJob?.cancel(); onlineArtworkJob?.cancel(); engine?.close(); engine = null
            }
        }
    }
    fun start() {
        pause?.cancel()
        ensureSessionService()
        started++
        ensureTicker()
    }
    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) { engine?.refreshPosition(); delay(300) }
        }
    }
    fun stop() {
        if (--started == 0) {
            if (session == null) ticker?.cancel()
            pause = scope.launch {
                delay(500) // Internal Activity hand-off must not pause the music.
                if (started == 0) engine?.let {
                    if (it.state.value.isPlaying && (session == null || it.state.value.isVideo)) it.togglePlayPause()
                }
            }
        }
    }
    fun setTheme(value: ThemeChoice) { _preferences.value = _preferences.value.copy(theme = value); storage.edit { putString("theme", value.name) } }
    fun setAccent(value: AccentChoice) { _preferences.value = _preferences.value.copy(accent = value); storage.edit { putString("accent", value.name) } }
    fun setReduceMotion(value: Boolean) { _preferences.value = _preferences.value.copy(reduceMotion = value); storage.edit { putBoolean("reduce_motion", value) } }
    fun setOrientation(value: OrientationChoice) { _preferences.value = _preferences.value.copy(orientation = value); storage.edit { putString("orientation", value.name) } }
    fun setOnlineMusic(value: Boolean) { _preferences.value = _preferences.value.copy(onlineMusic = value); storage.edit { putBoolean("online_music", value) }; if (!value) { onlineArtworkJob?.cancel(); engine?.stopOnline() } }
    fun setOnlineLyrics(value: Boolean) { _preferences.value = _preferences.value.copy(onlineLyrics = value); storage.edit { putBoolean("online_lyrics", value) }; if (!value) onlineLyricJob?.cancel() }
    fun toggleFavorite(key: String) { setFavorites(_preferences.value.favorites.let { if (key in it) it - key else it + key }) }
    fun setLyricStyle(value: yy.daniel.yyplayerforandroid.ui.LyricStyle) {
        value.validated(); _preferences.value = _preferences.value.copy(lyricStyle = value)
        storage.edit { putString("lyric_style", value.encode()) }
    }
    fun setFavorites(keys: Set<String>, favorite: Boolean) { setFavorites(if (favorite) preferences.value.favorites + keys else preferences.value.favorites - keys) }
    fun removeFavorite(key: String) { setFavorites(_preferences.value.favorites - key) }
    private fun setFavorites(value: Set<String>) { _preferences.value = _preferences.value.copy(favorites = value); storage.edit { putStringSet("favorites", value) } }

    fun setAudioSharing(value: yy.daniel.yyplayerforandroid.audio.SharingRequest) {
        if (audioOutput.state.value.selfTestRunning) return
        _preferences.value = _preferences.value.copy(audioSharing = value)
        storage.edit { putString("audio_sharing", value.name) }
        audioOutput.setPolicy(value, _preferences.value.audioTransparency)
        if (preferences.value.audioOutputPath == yy.daniel.yyplayerforandroid.audio.OutputPath.NATIVE_EXACT) engine?.reconfigureOutput()
    }
    fun setAudioTransparency(value: yy.daniel.yyplayerforandroid.audio.TransparencyRequest) {
        if (audioOutput.state.value.selfTestRunning) return
        _preferences.value = _preferences.value.copy(audioTransparency = value)
        storage.edit { putString("audio_transparency", value.name) }
        audioOutput.setPolicy(_preferences.value.audioSharing, value)
        if (preferences.value.audioOutputPath == yy.daniel.yyplayerforandroid.audio.OutputPath.NATIVE_EXACT) engine?.reconfigureOutput()
    }

    /**
     * Switches the playback output path. The two paths each own the platform stream, so the running
     * player is rebuilt at its current position rather than having a second sink attached. The session
     * service applies it too, so a session started later is built with the current setting.
     */
    fun setAudioOutputPath(value: yy.daniel.yyplayerforandroid.audio.OutputPath) {
        if (audioOutput.state.value.selfTestRunning) return
        audioOutput.clearPlaybackEvidence()
        _preferences.value = _preferences.value.copy(audioOutputPath = value)
        storage.edit { putString("audio_output_path", value.wire) }
        session?.applyOutputPath(value) ?: engine?.setOutputPath(value)
    }
    fun selectAudioOutputDevice(deviceId: Int?) {
        if (audioOutput.state.value.selfTestRunning) return
        audioOutput.selectDevice(deviceId)
        engine?.reconfigureOutput()
    }

    /** Explicit user action: play the reference signal out of the native ring and report the facts. */
    fun runAudioOutputSelfTest() {
        if (audioOutput.state.value.selfTestRunning) return
        val retainedVolume = engine?.exoPlayer()?.volume ?: 1f
        val playback = engine
        if (playback == null) audioOutput.runOutputSelfTest(retainedVolume) { }
        else playback.setOutputTestActive(true) {
            audioOutput.runOutputSelfTest(retainedVolume) { playback.setOutputTestActive(false) }
        }
    }
}
