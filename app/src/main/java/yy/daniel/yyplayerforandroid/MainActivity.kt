package yy.daniel.yyplayerforandroid

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.compose.setContent
import yy.daniel.yyplayerforandroid.ui.YYPlayerApp

class MainActivity : PlayerHostActivity() {
    internal var musicRequest by mutableIntStateOf(0)
        private set
    internal fun showMusic() { musicRequest++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("open_music", false)) showMusic()
        setContent { AppTheme { YYPlayerApp(this) } }
    }
    // open_music is a transient command. Preserve the launch intent (and debug portrait aid)
    // when the existing library Activity is brought forward from an online search.
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); if (intent.getBooleanExtra("open_music", false)) showMusic() }
}
