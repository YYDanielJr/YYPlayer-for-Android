package yy.daniel.yyplayerforandroid.playback

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

enum class MediaKind { AUDIO, VIDEO }

data class LocalMedia(
    val uri: Uri,
    val title: String,
    val kind: MediaKind,
    val mimeType: String?,
    val songTitle: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val metadataRead: Boolean = false,
) {
    val musicTitle: String get() = songTitle?.takeIf(String::isNotBlank) ?: title.substringBeforeLast('.', title)
    val musicSubtitle: String get() = listOf(artist ?: "未知歌手", album ?: "未知专辑").joinToString(" · ")
}

enum class DecoderMode(val title: String) {
    AUTO("自动"), PREFER_HARDWARE("优先硬解"), STRICT_HARDWARE("仅硬解"), SOFTWARE("软件解码"),
}

enum class PlaybackPhase { IDLE, BUFFERING, READY, ENDED, ERROR }

data class PlaybackSnapshot(
    val mediaGeneration: Long = 0,
    val queue: List<LocalMedia> = emptyList(),
    val queueIndex: Int = -1,
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val decoderMode: DecoderMode = DecoderMode.AUTO,
    val videoDecoderName: String? = null,
    val videoDecoderHardwareFlag: Boolean? = null,
    val videoDecoderSoftwareFlag: Boolean? = null,
    val audioDecoderName: String? = null,
    val audioSampleRate: Int? = null,
    val audioChannels: Int? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoPixelRatio: Float = 1f,
    val videoRotationDegrees: Int = 0,
    val error: String? = null,
) {
    val current: LocalMedia? get() = queue.getOrNull(queueIndex)
    val isVideo: Boolean get() = current?.kind == MediaKind.VIDEO
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** UI-facing intent/state boundary. The implementation owns the single playback clock. */
interface PlaybackBackend : AutoCloseable {
    val state: StateFlow<PlaybackSnapshot>
    fun play(queue: List<LocalMedia>, startIndex: Int = 0)
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    fun setSpeed(speed: Float)
    fun setDecoderMode(mode: DecoderMode)
    fun next()
    fun previous()
}
