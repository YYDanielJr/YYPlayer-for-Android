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
import kotlin.math.cos
import kotlin.math.sin

@Composable
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
fun VideoSurface(
    modifier: Modifier = Modifier,
    player: androidx.media3.common.Player,
    backend: PlaybackBackend,
    snapshot: PlaybackSnapshot,
    resetTransformKey: Int,
    onTap: () -> Unit = {},
    onBoostChanged: (Boolean) -> Unit = {},
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
                .pointerInput(player, viewport, snapshot.mediaGeneration, gesturesEnabled, resetTransformKey, fit,
                    snapshot.phase == yy.daniel.yyplayerforandroid.playback.PlaybackPhase.ENDED || snapshot.error != null) {
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val firstDown = awaitFirstDown()
                        if (!gesturesEnabled) return@awaitEachGesture
                        val baseSpeed = latestSpeed
                        val mediaUri = latestUri
                        val generation = latestGeneration
                        var temporaryBoost = false
                        var boostToken: Long? = null
                        var hadMultiplePointers = false
                        var moved = false
                        var elapsed = 0L
                        try {
                            do {
                                // AwaitPointerEventScope's timeout keeps pointer dispatch active:
                                // a second down enters transforms immediately instead of waiting for long press.
                                val event = if (!temporaryBoost && !hadMultiplePointers && !moved) {
                                    withTimeoutOrNull((longPressTimeout - elapsed).coerceAtLeast(1L)) { awaitPointerEvent() }
                                } else awaitPointerEvent()
                                if (event == null) {
                                    boostToken = backend.beginSpeedBoost()
                                    temporaryBoost = boostToken != null
                                    if (temporaryBoost) {
                                        hapticView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        latestBoost(true)
                                    } else moved = true
                                    continue
                                }
                                elapsed = event.changes.maxOf { it.uptimeMillis } - firstDown.uptimeMillis
                                if (event.changes.any { it.id == firstDown.id && (it.position - firstDown.position).getDistance() > touchSlop }) moved = true
                                val pressedPointers = event.changes.count { it.pressed }
                                if (pressedPointers >= 2) {
                                    hadMultiplePointers = true
                                    if (temporaryBoost) {
                                        boostToken?.let { backend.endSpeedBoost(it, baseSpeed) }
                                        latestBoost(false)
                                        temporaryBoost = false
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
                                    transform = VideoTransform(
                                        scale = newScale,
                                        rotation = ((transform.rotation + rotation) % 360f + 360f) % 360f,
                                        panX = (transform.panX + (pan.x + anchorCorrection.x) / width).coerceIn(-1f, 1f),
                                        panY = (transform.panY + (pan.y + anchorCorrection.y) / height).coerceIn(-1f, 1f),
                                    )
                                    event.changes.forEach { it.consume() }
                                } else if (temporaryBoost || hadMultiplePointers) {
                                    event.changes.forEach { it.consume() }
                                }
                                if (event.changes.none { it.pressed }) {
                                    if (!temporaryBoost && !hadMultiplePointers && !moved) latestTap()
                                    break
                                }
                            } while (true)
                        } finally {
                            if (temporaryBoost && latestUri == mediaUri && latestGeneration == generation) boostToken?.let { backend.endSpeedBoost(it, baseSpeed) }
                            latestBoost(false)
                        }
                    }
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
