package yy.daniel.yyplayerforandroid.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import yy.daniel.yyplayerforandroid.playback.PlaybackBackend
import yy.daniel.yyplayerforandroid.playback.PlaybackSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun VideoSurface(
    modifier: Modifier = Modifier,
    player: androidx.media3.common.Player,
    backend: PlaybackBackend,
    snapshot: PlaybackSnapshot,
    resetTransformKey: Int,
    onTap: () -> Unit = {},
    onBoostChanged: (Boolean) -> Unit = {},
    gestureSettings: VideoGestureSettings = VideoGestureSettings(),
    onDoubleTap: () -> Unit = { backend.togglePlayPause() },
    readBrightness: () -> Float = { .5f },
    readVolume: () -> Float = { player.volume },
    onGestureFeedback: (VideoGestureFeedback?) -> Unit = {},
    surfaceActive: Boolean = true,
    gesturesEnabled: Boolean = true,
    fit: VideoFit = VideoFit.FIT,
    subtitleSize: Float = 20f,
    subtitleBottomPadding: Float = .08f,
    onTransformChanged: (Boolean) -> Unit = {},
    onViewReady: (VideoViewport?) -> Unit = {},
) {
    var transform by remember(snapshot.current?.uri, resetTransformKey) { mutableStateOf(VideoTransform()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val latestSpeed by rememberUpdatedState(snapshot.speed)
    val latestUri by rememberUpdatedState(snapshot.current?.uri)
    val latestTap by rememberUpdatedState(onTap)
    val latestBoost by rememberUpdatedState(onBoostChanged)
    val latestGeneration by rememberUpdatedState(snapshot.mediaGeneration)
    val latestSnapshot by rememberUpdatedState(snapshot)
    val latestDoubleTap by rememberUpdatedState(onDoubleTap)
    val latestFeedback by rememberUpdatedState(onGestureFeedback)
    val latestBrightness by rememberUpdatedState(readBrightness)
    val latestVolume by rememberUpdatedState(readVolume)
    val tapScope = rememberCoroutineScope()
    val hapticView = LocalView.current
    LaunchedEffect(transform.changed) { onTransformChanged(transform.changed) }

    Box(
        modifier = modifier
            .background(Color(0xFF050609)),
    ) {
        // Retain transform state across Activity stops; only the Android surface is detached.
        if (surfaceActive) AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .pointerInput(player, viewport, snapshot.mediaGeneration, gesturesEnabled, gestureSettings,
                    resetTransformKey, fit, snapshot.error != null) {
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    val touchSlop = viewConfiguration.touchSlop
                    val edge = 24.dp.toPx()
                    var tapJob: Job? = null
                    var lastUp = -1L
                    var lastTapPosition = Offset.Zero
                    try {
                        awaitEachGesture {
                            val firstDown = awaitFirstDown()
                            if (!gesturesEnabled || firstDown.position.x < edge || firstDown.position.x > size.width - edge)
                                return@awaitEachGesture
                            val isSecondTap = gestureSettings.doubleTap && lastUp >= 0 &&
                                firstDown.uptimeMillis - lastUp <= viewConfiguration.doubleTapTimeoutMillis &&
                                (firstDown.position - lastTapPosition).getDistance() < 48.dp.toPx()
                            if (isSecondTap) { tapJob?.cancel(); lastUp = -1 }
                            val baseSpeed = latestSpeed
                            val mediaUri = latestUri
                            val generation = latestGeneration
                            val startPosition = latestSnapshot.positionMs
                            val duration = latestSnapshot.durationMs
                            var drag: VideoDrag? = null
                            var levelStart = 0f
                            var seekTarget = startPosition
                            var temporaryBoost = false
                            var boostToken: Long? = null
                            var hadMultiplePointers = false
                            var moved = false
                            var elapsed = 0L
                            try {
                                do {
                                    val event = if (gestureSettings.boost && !temporaryBoost && !hadMultiplePointers && !moved) {
                                        withTimeoutOrNull((longPressTimeout - elapsed).coerceAtLeast(1L)) { awaitPointerEvent() }
                                    } else awaitPointerEvent()
                                    if (event == null) {
                                        tapJob?.cancel(); lastUp = -1
                                        boostToken = backend.beginSpeedBoost()
                                        temporaryBoost = boostToken != null
                                        if (temporaryBoost) {
                                            hapticView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            latestBoost(true)
                                        } else moved = true
                                        continue
                                    }
                                    if (event.changes.any { it.isConsumed }) break
                                    elapsed = event.changes.maxOf { it.uptimeMillis } - firstDown.uptimeMillis
                                    val change = event.changes.firstOrNull { it.id == firstDown.id }
                                    val delta = change?.let { it.position - firstDown.position } ?: Offset.Zero
                                    if (delta.getDistance() > touchSlop) moved = true
                                    val pressedPointers = event.changes.count { it.pressed }
                                    if (pressedPointers >= 2) {
                                        hadMultiplePointers = true; drag = null; latestFeedback(null)
                                        tapJob?.cancel(); lastUp = -1
                                        if (temporaryBoost) {
                                            boostToken?.let { backend.endSpeedBoost(it, baseSpeed) }
                                            latestBoost(false); temporaryBoost = false
                                        }
                                        val pan = event.calculatePan()
                                        val zoom = event.calculateZoom()
                                        val rotation = event.calculateRotation()
                                        val width = viewport.width.coerceAtLeast(1).toFloat()
                                        val height = viewport.height.coerceAtLeast(1).toFloat()
                                        val newScale = (transform.scale * zoom).coerceIn(0.5f, 5f)
                                        val effectiveZoom = newScale / transform.scale
                                        val centroid = event.calculateCentroid(useCurrent = false)
                                        val focus = centroid - Offset(width / 2f + transform.panX * width, height / 2f + transform.panY * height)
                                        val radians = Math.toRadians(rotation.toDouble())
                                        val transformedFocus = Offset(
                                            ((focus.x * cos(radians) - focus.y * sin(radians)) * effectiveZoom).toFloat(),
                                            ((focus.x * sin(radians) + focus.y * cos(radians)) * effectiveZoom).toFloat())
                                        val anchorCorrection = focus - transformedFocus
                                        transform = VideoTransform(newScale, ((transform.rotation + rotation) % 360f + 360f) % 360f,
                                            (transform.panX + (pan.x + anchorCorrection.x) / width).coerceIn(-1f, 1f),
                                            (transform.panY + (pan.y + anchorCorrection.y) / height).coerceIn(-1f, 1f))
                                        event.changes.forEach { it.consume() }
                                    } else if (temporaryBoost || hadMultiplePointers) {
                                        event.changes.forEach { it.consume() }
                                    } else if (moved) {
                                        tapJob?.cancel(); lastUp = -1
                                        if (drag == null) {
                                            drag = if (abs(delta.x) > abs(delta.y)) {
                                                if (gestureSettings.seek && duration > 0) VideoDrag.SEEK else null
                                            } else if (firstDown.position.x < size.width / 2f) {
                                                if (gestureSettings.brightness) VideoDrag.BRIGHTNESS else null
                                            } else if (gestureSettings.volume) VideoDrag.VOLUME else null
                                            levelStart = if (drag == VideoDrag.BRIGHTNESS) latestBrightness() else latestVolume()
                                        }
                                        when (drag) {
                                            VideoDrag.SEEK -> {
                                                seekTarget = videoSeekTarget(startPosition, duration, delta.x / size.width.coerceAtLeast(1))
                                                latestFeedback(VideoGestureFeedback(VideoDrag.SEEK, seekTarget))
                                            }
                                            VideoDrag.BRIGHTNESS, VideoDrag.VOLUME -> latestFeedback(VideoGestureFeedback(drag!!,
                                                fraction = (levelStart - delta.y / (size.height.coerceAtLeast(1) * .7f)).coerceIn(0f, 1f)))
                                            null -> Unit
                                        }
                                        if (drag != null) event.changes.forEach { it.consume() }
                                    }
                                    if (event.changes.none { it.pressed }) {
                                        if (drag == VideoDrag.SEEK && latestGeneration == generation && latestUri == mediaUri)
                                            backend.seekTo(seekTarget)
                                        if (!temporaryBoost && !hadMultiplePointers && !moved) {
                                            if (!gestureSettings.doubleTap) latestTap()
                                            else if (isSecondTap) { lastUp = -1; latestDoubleTap() }
                                            else {
                                                lastUp = change?.uptimeMillis ?: firstDown.uptimeMillis
                                                lastTapPosition = firstDown.position
                                                tapJob?.cancel()
                                                tapJob = tapScope.launch { delay(viewConfiguration.doubleTapTimeoutMillis); latestTap(); lastUp = -1 }
                                            }
                                        }
                                        break
                                    }
                                } while (true)
                            } finally {
                                if (temporaryBoost && latestUri == mediaUri && latestGeneration == generation)
                                    boostToken?.let { backend.endSpeedBoost(it, baseSpeed) }
                                latestBoost(false); latestFeedback(null)
                            }
                        }
                    } finally { tapJob?.cancel() }
                },
            factory = { context ->
                VideoViewport(context).also { it.bind(player); onViewReady(it) }
            },
            update = { view ->
                view.bind(player)
                view.updateTransform(transform, fit, subtitleSize, subtitleBottomPadding)
            },
            onRelease = { it.bind(null); onViewReady(null) },
        )
    }
}
