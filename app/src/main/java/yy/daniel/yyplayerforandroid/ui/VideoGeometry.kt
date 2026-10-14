package yy.daniel.yyplayerforandroid.ui

import kotlin.math.abs
import kotlin.math.max

enum class VideoFit(val label: String) { FIT("适应画面"), FILL("铺满屏幕"), WIDE("16:9"), CLASSIC("4:3") }

internal data class VideoTransform(
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    val changed: Boolean get() = abs(scale - 1f) > .001f || abs(rotation) > .01f || abs(panX) > .001f || abs(panY) > .001f
}

/** Texture occupies the entire viewport; aspect fit belongs in its matrix, never its bounds. */
internal fun videoFitScale(width: Float, height: Float, ratio: Float, fit: VideoFit): Pair<Float, Float> {
    if (width <= 0 || height <= 0 || !ratio.isFinite() || ratio <= 0) return 1f to 1f
    val aspect = when (fit) { VideoFit.WIDE -> 16f / 9f; VideoFit.CLASSIC -> 4f / 3f; else -> ratio }
    val viewportRatio = width / height
    val x = if (aspect < viewportRatio) aspect / viewportRatio else 1f
    val y = if (aspect > viewportRatio) viewportRatio / aspect else 1f
    val crop = if (fit == VideoFit.FILL) max(1f / x, 1f / y) else 1f
    return x * crop to y * crop
}
