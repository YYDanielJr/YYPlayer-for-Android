package yy.daniel.yyplayerforandroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import yy.daniel.yyplayerforandroid.PlayerHostActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Only scroll-tail padding or fixed actions consume this; never shrink a scrolling viewport. */
internal val LocalPlaybackBottomSpace = compositionLocalOf { 0.dp }

/** A page's content can scroll/transition independently of the service-owned playback controls. */
@Composable
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlaybackPageHost(
    activity: PlayerHostActivity,
    content: @Composable () -> Unit,
) {
    val backend = activity.runtime.playback
    val snapshot by backend.state.collectAsStateWithLifecycle()
    val backdrop = rememberHyperBackdrop()
    val hasMedia = snapshot.current != null
    val density = LocalDensity.current
    var dockHeight by remember { mutableStateOf(96.dp) }
    // Keep the content/capture full size. The dock is a later-drawn sibling, not a bottom row.
    MusicExpansionHost(activity) { music ->
        Box(Modifier.fillMaxSize().then(music.backgroundModifier).imePadding()) {
            Box(Modifier.fillMaxSize().testTag("playback-page-content")
                .hyperCapture(backdrop).background(MiuixTheme.colorScheme.background)) {
                CompositionLocalProvider(LocalPlaybackBottomSpace provides if (hasMedia) dockHeight else 0.dp) {
                    content()
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                if (hasMedia) LibraryMiniPlayer(snapshot, backend, backdrop, activity::openPlayer,
                    Modifier.widthIn(max = 520.dp).fillMaxWidth()
                        .onSizeChanged { dockHeight = with(density) { it.height.toDp() } }
                        .padding(horizontal = 16.dp, vertical = 12.dp), music.reduceMotion, music.artworkModifier)
            }
        }
    }
}
