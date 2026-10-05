package yy.daniel.yyplayerforandroid

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import yy.daniel.yyplayerforandroid.ui.*
import yy.daniel.yyplayerforandroid.ui.theme.YYPlayerTheme

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
            AppTheme {
                SettingsScreen(preferences.theme, runtime::setTheme, preferences.accent, runtime::setAccent,
                    preferences.reduceMotion, runtime::setReduceMotion, snapshot, runtime.playback::setDecoderMode,
                    preferences.orientation, ::openOrientation, ::closePage)
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlayerActivity : PlayerHostActivity() {
    @android.annotation.SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val snapshot by runtime.playback.state.collectAsStateWithLifecycle()
            val preferences by runtime.preferences.collectAsStateWithLifecycle()
            val lifecycleState by lifecycle.currentStateFlow.collectAsState()
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
                PlayerScreen(runtime.playback, snapshot, preferences.reduceMotion,
                    onOpenOrientation = ::openOrientation, favorite = snapshot.current?.uri.toString() in preferences.favorites,
                    onFavorite = { snapshot.current?.let { runtime.toggleFavorite(it.uri.toString()) } },
                    onBack = ::closePage, surfaceActive = lifecycleState.isAtLeast(Lifecycle.State.STARTED))
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
