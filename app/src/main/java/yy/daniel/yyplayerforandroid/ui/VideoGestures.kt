package yy.daniel.yyplayerforandroid.ui

import android.content.Context
import androidx.core.content.edit

internal data class VideoGestureSettings(
    val doubleTap: Boolean = true,
    val seek: Boolean = true,
    val brightness: Boolean = true,
    val volume: Boolean = true,
    val boost: Boolean = true,
) {
    fun save(context: Context) = context.getSharedPreferences("video_gestures_v1", 0).edit {
        putBoolean("double_tap", doubleTap); putBoolean("seek", seek)
        putBoolean("brightness", brightness); putBoolean("volume", volume); putBoolean("boost", boost)
    }
    companion object {
        fun load(context: Context): VideoGestureSettings {
            val p = context.getSharedPreferences("video_gestures_v1", 0)
            fun read(key: String) = (p.all[key] as? Boolean) ?: true
            return VideoGestureSettings(read("double_tap"), read("seek"), read("brightness"), read("volume"), read("boost"))
        }
    }
}

internal enum class VideoDrag { SEEK, BRIGHTNESS, VOLUME }
internal data class VideoGestureFeedback(val drag: VideoDrag, val positionMs: Long = 0, val fraction: Float = 0f)

/** A full-width sweep covers up to two minutes; short clips remain controllable. */
internal fun videoSeekTarget(startMs: Long, durationMs: Long, fraction: Float): Long =
    (startMs + (fraction * durationMs.coerceAtMost(120_000L)).toLong()).coerceIn(0L, durationMs.coerceAtLeast(0L))
