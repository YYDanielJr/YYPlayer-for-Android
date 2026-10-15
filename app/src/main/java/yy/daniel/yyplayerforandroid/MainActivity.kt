package yy.daniel.yyplayerforandroid

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import yy.daniel.yyplayerforandroid.ui.YYPlayerApp

class MainActivity : PlayerHostActivity() {
    internal val musicOnBackStack get() = intent.getBooleanExtra(EXTRA_MUSIC_ON_BACK_STACK, false)
    internal fun closeMusic(collapse: () -> Unit) {
        if (musicOnBackStack) closePage() else collapse()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("open_music", false)) showMusic()
        setContent { AppTheme { YYPlayerApp(this) } }
    }
    // open_music is a transient command. Preserve the launch intent (and debug portrait aid)
    // when legacy player/notification navigation brings the existing library forward.
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); if (intent.getBooleanExtra("open_music", false)) showMusic() }
    internal companion object {
        const val EXTRA_MUSIC_ON_BACK_STACK = "music_on_back_stack"
    }
}
