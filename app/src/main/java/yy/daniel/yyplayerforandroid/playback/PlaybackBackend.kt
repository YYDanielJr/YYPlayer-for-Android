package yy.daniel.yyplayerforandroid.playback

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import yy.daniel.yyplayerforandroid.audio.NativeOutputAudioSink
import yy.daniel.yyplayerforandroid.audio.OutputPath

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
    val mediaHeaders: Map<String, Map<String, String>> = emptyMap(),
    val onlineKey: String? = null,
    val onlineQuality: String? = null,
    val artworkUri: Uri? = null,
    val appOwnedDownload: Boolean = false,
) {
    val isOnline: Boolean get() = uri.scheme == "https" || uri.scheme == "http"
    val identity: String get() = onlineKey ?: uri.toString()
    val musicTitle: String get() = songTitle?.takeIf(String::isNotBlank) ?: title.substringBeforeLast('.', title)
    val musicSubtitle: String get() = listOf(artist ?: "未知歌手", album ?: "未知专辑").joinToString(" · ")
}

enum class DecoderMode(val title: String) {
    AUTO("自动"), PREFER_HARDWARE("优先硬解"), STRICT_HARDWARE("仅硬解"), SOFTWARE("软件解码"),
}

enum class PlaybackPhase { IDLE, BUFFERING, READY, ENDED, ERROR }
enum class PlayOrder(val label: String) { SEQUENTIAL("顺序播放"), SINGLE("单曲循环"), RANDOM("随机播放") }

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
    /** Which output path the engine was built with; a switch rebuilds the player. */
    val outputPath: OutputPath = OutputPath.SYSTEM,
    /**
     * What the native output negotiated for the current stream, or null on the system path. This is
     * evidence about the opened output, not a request.
     */
    val negotiatedOutput: NativeOutputAudioSink.Negotiated? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoPixelRatio: Float = 1f,
    val videoRotationDegrees: Int = 0,
    val error: String? = null,
    val playOrder: PlayOrder = PlayOrder.SEQUENTIAL,
    val outputFallback: String? = null,
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
    fun setPlayOrder(order: PlayOrder)
    fun jumpTo(index: Int) { play(state.value.queue, index) }
    fun enqueue(items: List<LocalMedia>, next: Boolean = false)
    fun removeQueueItem(index: Int)
    fun moveQueueItem(from: Int, to: Int)
    fun clearQueue()
}
