package yy.daniel.yyplayerforandroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import yy.daniel.yyplayerforandroid.MainActivity
import yy.daniel.yyplayerforandroid.PlayerHostActivity

internal class MusicExpansionScope(
    val expanded: MutableState<Boolean>,
    val reduceMotion: Boolean,
    val backgroundModifier: Modifier,
    val artworkModifier: Modifier,
)

/** The same shared artwork and spring for home, search, settings, queue and setup. */
@Composable
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun MusicExpansionHost(activity: PlayerHostActivity, content: @Composable (MusicExpansionScope) -> Unit) {
    val runtime = activity.runtime
    val playback = runtime.playback
    val snapshot by playback.state.collectAsStateWithLifecycle()
    val preferences by runtime.preferences.collectAsStateWithLifecycle()
    val onlineConfig by runtime.online.config.collectAsStateWithLifecycle()
    val audioOutput by runtime.audioOutput.state.collectAsStateWithLifecycle()
    val expandedState = rememberSaveable { mutableStateOf(activity is MainActivity && activity.musicOnBackStack) }
    var musicExpanded by expandedState
    val closeMusic = {
        if (activity is MainActivity) activity.closeMusic { musicExpanded = false }
        else musicExpanded = false
    }
    val reduceMotion = rememberMusicReduceMotion(preferences.reduceMotion)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var miniBounds by remember { mutableStateOf<Rect?>(null) }
    var measuredMiniBounds by remember { mutableStateOf<Rect?>(null) }
    var coverAnchor by remember { mutableStateOf<MusicCoverAnchor?>(null) }
    // Restored expanded pages start fully open; a fresh tap always starts at the local dock.
    val journey = remember { Animatable(if (musicExpanded) 1f else 0f, visibilityThreshold = .0001f) }
    val target = if (musicExpanded && !snapshot.isVideo && snapshot.current != null) 1f else 0f
    val expansion = journey.value // Signed displacement preserves the original spring rebound.
    val travelling = !reduceMotion && (journey.isRunning || journey.value != target)
    val showingMusic = musicExpanded || travelling
    val sharedMini = !reduceMotion && !snapshot.isVideo && snapshot.current != null && miniBounds != null
    val detailVisible = musicExpanded || expansion > .02f
    val detailInteractive = musicExpanded && expansion >= .96f
    LaunchedEffect(target, reduceMotion) {
        if (target == 1f && expansion <= .02f) {
            val from = miniBounds
            val to = coverAnchor?.bounds
            if (journey.value != 0f && from != null && to != null) {
                miniBounds = androidx.compose.ui.geometry.lerp(from, to, journey.value)
                journey.snapTo(0f)
            } else miniBounds = measuredMiniBounds ?: miniBounds
            // Do not clear the destination here: the new cover can already have reported
            // its bounds in this frame, before this effect starts. Clearing them loses the
            // only layout callback and leaves the shared artwork at the dock until settling.
        } else if (!journey.isRunning) miniBounds = measuredMiniBounds ?: miniBounds
        if (reduceMotion) journey.snapTo(target)
        else if (target == 0f) journey.animateTo(0f, spring(stiffness = 230f, dampingRatio = .8f, visibilityThreshold = .0001f))
        else journey.animateTo(1f, spring(stiffness = 180f, dampingRatio = .86f, visibilityThreshold = .0001f))
    }
    LaunchedEffect(activity.musicRequest, snapshot.current?.uri, snapshot.isVideo) {
        if (!snapshot.isVideo && snapshot.current != null && activity.consumeMusicRequest()) musicExpanded = true
    }
    LaunchedEffect(musicExpanded) { if (musicExpanded) { keyboard?.hide(); focus.clearFocus() } }
    LaunchedEffect(snapshot.isVideo, snapshot.current == null) {
        if (snapshot.isVideo || snapshot.current == null) closeMusic()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fallbackOrigin = Offset(48f, constraints.maxHeight - 72f)
        content(MusicExpansionScope(expandedState, reduceMotion,
            Modifier.graphicsLayer { alpha = 1f - expansion.coerceIn(0f, 1f) * .2f }
                .musicInputGate(detailVisible)
                .then(if (detailVisible) Modifier.clearAndSetSemantics {} else Modifier),
            Modifier.onGloballyPositioned {
                measuredMiniBounds = it.boundsInRoot()
                if (miniBounds == null || !showingMusic) miniBounds = measuredMiniBounds
            }.graphicsLayer { alpha = if (sharedMini || showingMusic) 0f else 1f }))
        // Register after the underlying page's handlers: Back closes this layer first.
        BackHandler(detailVisible, onBack = closeMusic)
        if (detailVisible) {
            Box(Modifier.fillMaxSize().musicInputGate(!detailInteractive)
                .then(if (!detailInteractive) Modifier.clearAndSetSemantics {} else Modifier)
                .graphicsLayer { alpha = expansion.coerceIn(0f, 1f) }) {
                MusicPlayerScreen(snapshot, playback, runtime.lyrics, reduceMotion,
                    snapshot.current?.identity in preferences.favorites,
                    { snapshot.current?.let { runtime.toggleFavorite(it.identity) } }, closeMusic,
                    lyricStyle = preferences.lyricStyle, audioOutput = audioOutput,
                    outputPath = preferences.audioOutputPath, onOutputPath = runtime::setAudioOutputPath,
                    onSelectOutputDevice = runtime::selectAudioOutputDevice,
                    onLyricStyle = activity::openLyricStyle, onQueue = activity::openMusicQueue,
                    journeyProgress = expansion, journeyOrigin = miniBounds?.center ?: fallbackOrigin,
                    hideCover = travelling, onCoverBounds = {
                        // A fresh detail starts on the cover tab, including after a lyrics
                        // collapse. Accept its first layout before freezing the travel anchor.
                        if (!travelling || coverAnchor == null || (musicExpanded && expansion <= .02f)) coverAnchor = it
                    },
                    onOnlineLyrics = if (preferences.onlineLyrics && onlineConfig?.providers.orEmpty().any { it.lyric != null && it.lyricSearch != null }) activity::openOnlineLyrics else null)
            }
        }
        val start = miniBounds
        val anchor = coverAnchor ?: start?.let { MusicCoverAnchor(it, 23.dp, 0.dp) }
        val end = anchor?.bounds
        if (sharedMini && start != null && end != null) {
            val density = LocalDensity.current
            val targetWidth = with(density) { end.width.toDp() }
            val width = (start.width + (end.width - start.width) * expansion).coerceAtLeast(1f)
            val scale = width / end.width
            val p = expansion.coerceIn(0f, 1f)
            val corner = with(density) { ((start.width / 2f + (anchor.corner.toPx() - start.width / 2f) * p) / scale).toDp() }
            // This layer survives collapse and keeps drawing the dock artwork: no endpoint swap.
            Box(Modifier.size(targetWidth).graphicsLayer {
                alpha = if (!musicExpanded || travelling) 1f else 0f
                scaleX = scale; scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                translationX = start.left + (end.left - start.left) * expansion
                translationY = start.top + (end.top - start.top) * expansion
                rotationZ = -5f * (1 - expansion) * expansion
            }.testTag("music-shared-artwork")
                .shadow(anchor.elevation * p / scale, RoundedCornerShape(corner), clip = false)) {
                MediaArtwork(snapshot.current, Modifier.fillMaxSize(), corner)
            }
        }
    }
}
