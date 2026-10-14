package yy.daniel.yyplayerforandroid

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import yy.daniel.yyplayerforandroid.ui.*
import yy.daniel.yyplayerforandroid.ui.theme.YYPlayerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Leave normal Activity transitions to the ROM. No custom windowAnimationStyle or slide override. */
abstract class PlayerHostActivity : ComponentActivity() {
    internal val runtime get() = (application as PlayerApplication).runtime
    internal val debugPortrait get() = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.getBooleanExtra("qa_portrait", false)
    @android.annotation.SuppressLint("SourceLockedOrientationActivity") // Guarded debug-only MuMu screenshot aid.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        runtime.acquire()
        if (debugPortrait) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    override fun onStart() { super.onStart(); runtime.start() }
    override fun onStop() { runtime.stop(); super.onStop() }
    override fun onDestroy() { runtime.release(isChangingConfigurations); super.onDestroy() }

    internal fun openSettings() = open(SettingsActivity::class.java)
    internal fun openPlayer() {
        if (runtime.playback.state.value.current?.kind == yy.daniel.yyplayerforandroid.playback.MediaKind.AUDIO) {
            if (this is MainActivity) showMusic()
            else startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_music", true))
        } else open(PlayerActivity::class.java)
    }
    internal fun openOrientation() = open(OrientationActivity::class.java)
    internal fun openLyricStyle() = open(LyricStyleActivity::class.java)
    internal fun openMusicQueue() = open(MusicQueueActivity::class.java)
    internal fun openAudioOutput() = open(AudioOutputActivity::class.java)
    internal fun openSetup() {
        runtime.setup.restart()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        if (this !is MainActivity) closePage()
    }
    internal fun openOnlineSearch() { if (runtime.preferences.value.onlineMusic) open(OnlineSearchActivity::class.java) }
    internal fun openOnlineLyrics() { if (runtime.preferences.value.onlineLyrics) open(OnlineLyricsActivity::class.java) }
    private fun open(type: Class<out PlayerHostActivity>) {
        val target = Intent(this, type)
        if (debugPortrait) target.putExtra("qa_portrait", true)
        startActivity(target) // The platform/HyperOS chooses the transition.
        if (runtime.preferences.value.reduceMotion) suppressTransition()
    }
    internal fun closePage() = finish()
    override fun finish() { super.finish(); if (runtime.preferences.value.reduceMotion) suppressTransition() }
    @Suppress("DEPRECATION")
    private fun suppressTransition() = overridePendingTransition(0, 0)

    @Composable
    internal fun AppTheme(immersive: Boolean = false, content: @Composable () -> Unit) {
        val preferences by runtime.preferences.collectAsStateWithLifecycle()
        val configuration = LocalConfiguration.current
        val view = LocalView.current
        val dark = preferences.theme == ThemeChoice.DARK || (preferences.theme == ThemeChoice.SYSTEM && isSystemInDarkTheme())
        LaunchedEffect(configuration, dark, immersive) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark && !immersive
                isAppearanceLightNavigationBars = !dark && !immersive
            }
        }
        YYPlayerTheme(preferences.theme.mode, preferences.accent.color, content)
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class SettingsActivity : PlayerHostActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences by runtime.preferences.collectAsStateWithLifecycle()
            val snapshot by runtime.playback.state.collectAsStateWithLifecycle()
            val config by runtime.online.config.collectAsStateWithLifecycle()
            val apiMessage by runtime.online.message.collectAsStateWithLifecycle()
            val downloadDirectory by runtime.online.downloadDirectory.collectAsStateWithLifecycle()
            val audioOutput by runtime.audioOutput.state.collectAsStateWithLifecycle()
            var localMessage by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            val configPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) scope.launch {
                    try { runtime.online.importConfig(uri); localMessage = null }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { localMessage = "导入失败：${e.message ?: "配置不合法"}；保留原配置" }
                }
            }
            val directoryPicker = rememberLauncherForActivityResult(DownloadTreePicker()) { result ->
                if (result != null) try { result.persist(this); runtime.online.rememberDownloadTree(result.uri); localMessage = "下载目录已设置" }
                catch (e: Exception) { localMessage = e.message ?: "目录授权失败" }
            }
            AppTheme {
                SettingsScreen(preferences.theme, runtime::setTheme, preferences.accent, runtime::setAccent,
                    preferences.reduceMotion, runtime::setReduceMotion, snapshot, runtime.playback::setDecoderMode,
                    preferences.orientation, ::openOrientation, ::closePage,
                    preferences.onlineMusic, runtime::setOnlineMusic, preferences.onlineLyrics, runtime::setOnlineLyrics,
                    config?.providers?.joinToString("、") { it.name }.orEmpty(), localMessage ?: apiMessage,
                    { configPicker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, { directoryPicker.launch(Unit) },
                    downloadDirectory?.let { runCatching { android.provider.DocumentsContract.getTreeDocumentId(it.toUri()).replace(':', '/') }.getOrDefault("已选择文件夹") } ?: "系统 Music（默认）",
                    { runtime.online.rememberDownloadTree(null); localMessage = "已恢复默认 Music 下载目录" }, ::openLyricStyle, ::openSetup,
                    audioOutput, ::openAudioOutput)
            }
        }
    }
}

class LyricStyleActivity : PlayerHostActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences by runtime.preferences.collectAsStateWithLifecycle()
            AppTheme { LyricStyleScreen(preferences.lyricStyle, runtime::setLyricStyle, ::closePage) }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlayerActivity : PlayerHostActivity() {
    private var windowFocused by mutableStateOf(true)
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); windowFocused = hasFocus }
    @android.annotation.SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val snapshot by runtime.playback.state.collectAsStateWithLifecycle()
            val preferences by runtime.preferences.collectAsStateWithLifecycle()
            val lifecycleState by lifecycle.currentStateFlow.collectAsState()
            val audioOutput by runtime.audioOutput.state.collectAsStateWithLifecycle()
            LaunchedEffect(snapshot.current?.uri, snapshot.isVideo) {
                if (snapshot.current != null && !snapshot.isVideo) openPlayer()
            }
            val ratio = if (snapshot.videoHeight > 0) snapshot.videoWidth * snapshot.videoPixelRatio / snapshot.videoHeight else 0f
            val displayRatio = if (snapshot.videoRotationDegrees % 180 != 0 && ratio > 0) 1f / ratio else ratio
            LaunchedEffect(snapshot.isVideo, displayRatio, preferences.orientation) {
                val request = if (snapshot.isVideo) {
                    if (preferences.orientation != OrientationChoice.AUTO) preferences.orientation.request
                    else when { displayRatio > 1.12f -> 2; displayRatio in 0.01f..0.88f -> 1; else -> 0 }
                } else 0
                requestedOrientation = when (request) {
                    1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else -> if (debugPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            AppTheme(snapshot.isVideo) {
                DisposableEffect(snapshot.isVideo) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    if (snapshot.isVideo) {
                        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    }
                    onDispose { controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
                }
                DisposableEffect(snapshot.isVideo, snapshot.isPlaying) {
                    if (snapshot.isVideo && snapshot.isPlaying) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }
                PlayerScreen(runtime.playback, snapshot, preferences.reduceMotion,
                    onOpenOrientation = ::openOrientation, favorite = snapshot.current?.uri.toString() in preferences.favorites,
                    onFavorite = { snapshot.current?.let { runtime.toggleFavorite(it.uri.toString()) } },
                    onBack = ::closePage, surfaceActive = lifecycleState.isAtLeast(Lifecycle.State.STARTED),
                    inputActive = windowFocused && lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
                    audioOutput = audioOutput)
            }
        }
    }
}

class OrientationActivity : PlayerHostActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences by runtime.preferences.collectAsStateWithLifecycle()
            AppTheme { OrientationPage(preferences.orientation, runtime::setOrientation, ::closePage) }
        }
    }
}

/**
 * Output strategy and device selection. The screen only shows state that the controller published
 * from platform evidence, so an unavailable bit-perfect contract stays visible as unavailable.
 */
class AudioOutputActivity : PlayerHostActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences by runtime.preferences.collectAsStateWithLifecycle()
            val output by runtime.audioOutput.state.collectAsStateWithLifecycle()
            AppTheme {
                AudioOutputScreen(
                    state = output,
                    sharing = preferences.audioSharing,
                    transparency = preferences.audioTransparency,
                    outputPath = preferences.audioOutputPath,
                    onSharing = runtime::setAudioSharing,
                    onTransparency = runtime::setAudioTransparency,
                    onOutputPath = runtime::setAudioOutputPath,
                    onSelectDevice = runtime::selectAudioOutputDevice,
                    onRunSelfTest = { runtime.runAudioOutputSelfTest() },
                    onBack = ::closePage,
                )
            }
        }
    }
}
