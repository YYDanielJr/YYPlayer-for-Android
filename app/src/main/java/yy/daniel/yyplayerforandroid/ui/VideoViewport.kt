package yy.daniel.yyplayerforandroid.ui

import android.content.Context
import android.graphics.Matrix
import android.view.TextureView
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

/** A full-window texture prevents AspectRatioFrameLayout from clipping zoom into letterbox bars. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class VideoViewport(context: Context) : FrameLayout(context) {
    val texture = TextureView(context)
    private val subtitles = SubtitleView(context)
    private var player: Player? = null
    private var transform = VideoTransform()
    private var fit = VideoFit.FIT
    private val listener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) = applyTransform()
        override fun onCues(cueGroup: CueGroup) { subtitles.setCues(cueGroup.cues) }
    }
    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(subtitles, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        subtitles.setStyle(CaptionStyleCompat(android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null))
        subtitles.setBottomPaddingFraction(.10f)
        subtitles.setApplyEmbeddedFontSizes(false)
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyTransform() }
    }
    fun bind(next: Player?) {
        if (player === next) return
        player?.removeListener(listener)
        player?.clearVideoTextureView(texture)
        player = next
        next?.setVideoTextureView(texture)
        next?.addListener(listener)
        subtitles.setCues(next?.currentCues?.cues ?: emptyList())
        applyTransform()
    }
    internal fun updateTransform(value: VideoTransform, mode: VideoFit, textSize: Float, bottomPadding: Float) {
        transform = value; fit = mode
        subtitles.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSize)
        subtitles.setBottomPaddingFraction(bottomPadding)
        applyTransform()
    }
    private fun applyTransform() {
        if (width == 0 || height == 0) return
        val size = player?.videoSize ?: VideoSize.UNKNOWN
        var ratio = if (size.height > 0) size.width * size.pixelWidthHeightRatio / size.height else width.toFloat() / height
        val rotation = size.unappliedRotationDegrees
        if (rotation % 180 != 0) ratio = 1f / ratio
        val (x, y) = videoFitScale(width.toFloat(), height.toFloat(), ratio, fit)
        val cx = width / 2f; val cy = height / 2f
        texture.setTransform(Matrix().apply {
            // Unapplied rotation is normally zero with modern MediaCodec. Normalize bounds once.
            if (rotation % 180 != 0) setScale(height.toFloat() / width, width.toFloat() / height, cx, cy)
            postRotate(rotation.toFloat(), cx, cy)
            postScale(x * transform.scale, y * transform.scale, cx, cy)
            postRotate(transform.rotation, cx, cy)
            postTranslate(transform.panX * width, transform.panY * height)
        })
        // A paused TextureView has no producer frame to invalidate Compose's AndroidView layer.
        // Invalidate the host as well so the new matrix is submitted even while playback is paused.
        texture.invalidate()
        invalidate()
        // Lay captions out in video coordinates without anisotropically stretching the glyphs.
        val captionWidth = (width * x).toInt().coerceAtLeast(1)
        val captionHeight = (height * y).toInt().coerceAtLeast(1)
        if (subtitles.layoutParams.width != captionWidth || subtitles.layoutParams.height != captionHeight) {
            subtitles.layoutParams = LayoutParams(captionWidth, captionHeight, android.view.Gravity.CENTER)
        }
        subtitles.scaleX = transform.scale; subtitles.scaleY = transform.scale
        subtitles.rotation = transform.rotation
        subtitles.translationX = transform.panX * width; subtitles.translationY = transform.panY * height
    }
}
