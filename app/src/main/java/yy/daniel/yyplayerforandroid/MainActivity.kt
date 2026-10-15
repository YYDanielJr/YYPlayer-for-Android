package yy.daniel.yyplayerforandroid

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import yy.daniel.yyplayerforandroid.ui.YYPlayerApp

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class MainActivity : PlayerHostActivity() {
    internal val musicOnBackStack get() = intent.getBooleanExtra(EXTRA_MUSIC_ON_BACK_STACK, false)
    internal fun closeMusic(collapse: () -> Unit) {
        if (musicOnBackStack) closePage() else collapse()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A restored activity already owns its saved expanded/collapsed state. Do not replay
        // the original SystemUI launch after the user has collapsed the cover.
        if (savedInstanceState == null) consumeMediaEntry()
        setContent { AppTheme { YYPlayerApp(this) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val previous = getIntent()
        // getReferrer() must read the incoming intent, not the previous launch's extras.
        setIntent(Intent(intent).apply {
            if (!hasExtra("qa_portrait") && previous.hasExtra("qa_portrait"))
                putExtra("qa_portrait", previous.getBooleanExtra("qa_portrait", false))
            if (!hasExtra(EXTRA_MUSIC_ON_BACK_STACK) && previous.getBooleanExtra(EXTRA_MUSIC_ON_BACK_STACK, false))
                putExtra(EXTRA_MUSIC_ON_BACK_STACK, true)
        })
        consumeMediaEntry()
    }

    private fun consumeMediaEntry() {
        val explicit = intent.getBooleanExtra("open_music", false)
        // HyperOS's control-centre card launches MAIN/LAUNCHER instead of sessionActivity.
        // Referrer is only a navigation hint, never an authorization or playback command.
        val origin = referrer
        val systemLauncher = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            origin?.scheme == "android-app" && origin.host == "com.android.systemui"
        val current = runtime.playback.state.value
        if (explicit || (systemLauncher && current.current != null && !current.isVideo)) {
            // System media entry always collapses to this Activity's home, including when
            // reusing a legacy music-on-back-stack destination.
            intent.removeExtra(EXTRA_MUSIC_ON_BACK_STACK)
            showMusic()
        }
        intent.removeExtra("open_music")
    }
    internal companion object {
        const val EXTRA_MUSIC_ON_BACK_STACK = "music_on_back_stack"
    }
}
