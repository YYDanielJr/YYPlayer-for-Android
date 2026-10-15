package yy.daniel.yyplayerforandroid.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

internal data class MusicCoverAnchor(val bounds: Rect, val corner: Dp = 24.dp, val elevation: Dp = 24.dp)

/** Alpha does not disable hit testing. Gate the detail subtree before its children see a down. */
internal fun Modifier.musicInputGate(blocked: Boolean): Modifier = pointerInput(blocked) {
    if (blocked) awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

/** Non-consuming tilt: the pager/clickable keeps ownership of click, drag and cancellation. */
@Composable
internal fun Modifier.gentleCoverTilt(reduced: Boolean, identity: String, paging: Boolean = false): Modifier {
    var target by remember(identity, reduced) { mutableStateOf(Offset.Zero) }
    val x by animateFloatAsState(if (reduced) 0f else target.x,
        spring(dampingRatio = 1f, stiffness = 95f), label = "cover tilt x")
    val y by animateFloatAsState(if (reduced) 0f else target.y,
        spring(dampingRatio = 1f, stiffness = 95f), label = "cover tilt y")
    return pointerInput(reduced, identity, paging) {
        if (!reduced && !paging) try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                fun project(position: Offset) = Offset((position.x / size.width * 2 - 1).coerceIn(-1f, 1f),
                    (position.y / size.height * 2 - 1).coerceIn(-1f, 1f))
                target = project(down.position)
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        // clickable consumes the initial down; only a later consumed move means
                        // the pager/scroll container took over the gesture.
                        val active = pointer != null && pointer.pressed && !(pointer.previousPressed && pointer.isConsumed) && event.changes.count { it.pressed } == 1
                        if (!active) break
                        target = project(pointer.position)
                    } while (true)
                } finally { target = Offset.Zero }
            }
        } finally { target = Offset.Zero }
    }.graphicsLayer {
        rotationX = if (paging) 0f else -y * 9f; rotationY = if (paging) 0f else x * 9f
        cameraDistance = 18f * density
    }
}

/** Move the center without reflowing text, matching the desktop bottom-left journey. */
@Composable
internal fun Modifier.musicJourney(progress: Float, origin: Offset, reduced: Boolean): Modifier {
    var center by remember { mutableStateOf(origin) }
    return onGloballyPositioned { center = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) }
        .graphicsLayer {
            val p = if (reduced) 1f else progress
            translationX = (origin.x - center.x) * (1 - p)
            translationY = (origin.y - center.y) * (1 - p)
            alpha = ((p - .18f) / .82f).coerceIn(0f, 1f)
        }
}
