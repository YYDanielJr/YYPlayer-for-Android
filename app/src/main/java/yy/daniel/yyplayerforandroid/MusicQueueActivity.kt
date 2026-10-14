package yy.daniel.yyplayerforandroid

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import yy.daniel.yyplayerforandroid.ui.MusicQueueScreen

/** Shares the service-owned player; Activity navigation uses the ROM transition. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class MusicQueueActivity : PlayerHostActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val snapshot by runtime.playback.state.collectAsStateWithLifecycle()
            AppTheme { MusicQueueScreen(snapshot, runtime.playback, ::closePage) }
        }
    }
}
