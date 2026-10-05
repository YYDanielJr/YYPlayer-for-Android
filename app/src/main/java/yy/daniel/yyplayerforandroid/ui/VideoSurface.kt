package yy.daniel.yyplayerforandroid.ui

import android.graphics.Matrix
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.view.TextureView
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import yy.daniel.yyplayerforandroid.R
import yy.daniel.yyplayerforandroid.playback.PlaybackBackend
import yy.daniel.yyplayerforandroid.playback.PlaybackSnapshot
import kotlin.math.cos
import kotlin.math.sin

private data class VideoTransform(
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val panX: Float = 0f,
    val panY: Float = 0f,
)

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
) {
    var transform by remember(snapshot.current?.uri, resetTransformKey) { mutableStateOf(VideoTransform()) }
    var viewport by remember(player, surfaceActive) { mutableStateOf(IntSize.Zero) }
    val latestSpeed by rememberUpdatedState(snapshot.speed)
    val latestUri by rememberUpdatedState(snapshot.current?.uri)
    val latestTap by rememberUpdatedState(onTap)
    val latestBoost by rememberUpdatedState(onBoostChanged)

    Box(
        modifier = modifier
            .background(Color(0xFF050609)),
    ) {
        // Retain transform state across Activity stops; only the Android surface is detached.
        if (surfaceActive) AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .pointerInput(player, viewport, snapshot.current?.uri) {
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val baseSpeed = latestSpeed
                        val mediaUri = latestUri
                        var temporaryBoost = false
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
                                    temporaryBoost = true
                                    backend.setSpeed(3f)
                                    latestBoost(true)
                                    continue
                                }
                                elapsed = event.changes.maxOf { it.uptimeMillis } - firstDown.uptimeMillis
                                if (event.changes.any { it.id == firstDown.id && (it.position - firstDown.position).getDistance() > touchSlop }) moved = true
                                val pressedPointers = event.changes.count { it.pressed }
                                if (pressedPointers >= 2) {
                                    hadMultiplePointers = true
                                    if (temporaryBoost) {
                                        backend.setSpeed(baseSpeed)
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
                            if (temporaryBoost && latestUri == mediaUri) backend.setSpeed(baseSpeed)
                            latestBoost(false)
                        }
                    }
                },
            factory = { context ->
                val parent = FrameLayout(context)
                (LayoutInflater.from(context).inflate(R.layout.view_player_texture, parent, false) as PlayerView)
                    .apply { this.player = player }
            },
            update = { view ->
                view.player = player
                (view.videoSurfaceView as? TextureView)?.apply {
                    val centerX = width / 2f
                    val centerY = height / 2f
                    val matrix = Matrix().apply {
                        setScale(transform.scale, transform.scale, centerX, centerY)
                        postRotate(transform.rotation, centerX, centerY)
                        postTranslate(transform.panX * viewport.width, transform.panY * viewport.height)
                    }
                    setTransform(matrix)
                }
            },
            onRelease = { it.player = null },
        )
    }
}
