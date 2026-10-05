package yy.daniel.yyplayerforandroid.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import yy.daniel.yyplayerforandroid.online.OnlineAudioDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Real local Media3 playback for the emulator-first vertical slice; no UI-owned player state. */
@UnstableApi
class AndroidMedia3PlaybackBackend(context: Context, private val networkAllowed: () -> Boolean = { false }) : PlaybackBackend {
    private val appContext = context.applicationContext
    // newPlayer reads speed during property initialization, so initialize it before player.
    private var speed = 1f
    private val mediaHeaderSnapshot = java.util.concurrent.atomic.AtomicReference<Map<String, Map<String, Map<String, String>>>>(emptyMap())
    private var player: ExoPlayer = newPlayer(DecoderMode.AUTO)
    private var queue: List<LocalMedia> = emptyList()
    private var index: Int = -1
    private var mediaGeneration = 0L
    private var decoderMode = DecoderMode.AUTO
    private var videoDecoderName: String? = null
    private var audioDecoderName: String? = null
    private var audioSampleRate: Int? = null
    private var audioChannels: Int? = null
    private var videoSize = VideoSize.UNKNOWN
    private var error: String? = null
    private val _state = MutableStateFlow(PlaybackSnapshot())
    override val state: StateFlow<PlaybackSnapshot> = _state.asStateFlow()

    init { attachListeners(player) }

    override fun play(queue: List<LocalMedia>, startIndex: Int) {
        if (queue.isEmpty() || startIndex !in queue.indices) return
        if (queue[startIndex].isOnline && !networkAllowed()) { error = "联网音乐已关闭"; publish(); return }
        this.queue = queue.toList()
        mediaHeaderSnapshot.set(queue.associate { it.uri.toString() to it.mediaHeaders })
        index = startIndex
        error = null
        videoDecoderName = null
        audioDecoderName = null
        videoSize = VideoSize.UNKNOWN
        player.setMediaItems(queue.map(::toMediaItem), startIndex, 0L)
        player.prepare()
        player.playWhenReady = true
        publish()
    }

    override fun togglePlayPause() {
        if (_state.value.current?.isOnline == true && !networkAllowed()) { error = "联网音乐已关闭"; publish(); return }
        error = null
        if (player.playbackState == Player.STATE_IDLE && queue.isNotEmpty()) player.prepare()
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
            player.play()
        } else if (player.isPlaying || player.playWhenReady) player.pause() else player.play()
        publish()
    }

    override fun seekTo(positionMs: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo(positionMs.coerceIn(0L, duration))
        publish()
    }

    override fun seekBy(deltaMs: Long) = seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.25f, 3f)
        player.playbackParameters = PlaybackParameters(this.speed)
        publish()
    }

    override fun setDecoderMode(mode: DecoderMode) {
        if (mode == decoderMode) return
        val videoMime = player.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 }
            ?.getTrackFormat(0)?.sampleMimeType
        if (videoMime != null && mode != DecoderMode.AUTO) {
            val available = try {
                MediaCodecSelector.DEFAULT.getDecoderInfos(videoMime, false, false)
            } catch (failure: Exception) {
                error = "无法查询 $videoMime 解码器：${failure.message.orEmpty()}"
                publish()
                return
            }
            val selected = DecoderSelectionPolicy.select(
                candidates = available,
                mode = mode,
                isHardwareAccelerated = { it.hardwareAccelerated },
                isSoftwareOnly = { it.softwareOnly || DecoderSelectionPolicy.isKnownPlatformSoftwareCodec(it.name) },
            )
            if (selected.isEmpty()) {
                val requirement = when (mode) {
                    DecoderMode.AUTO -> "自动"
                    DecoderMode.PREFER_HARDWARE -> "硬件优先"
                    DecoderMode.STRICT_HARDWARE -> "纯硬件"
                    DecoderMode.SOFTWARE -> "软件"
                }
                val availableNames = available.joinToString {
                    "${it.name}(系统软件标记=${it.softwareOnly},名称识别=${DecoderSelectionPolicy.isKnownPlatformSoftwareCodec(it.name)})"
                }
                error = "此设备没有可用的${requirement}解码器${if (availableNames.isBlank()) "" else "；检测到：$availableNames"}"
                publish()
                return
            }
        }
        decoderMode = mode
        val current = _state.value.current
        if (current?.kind == MediaKind.VIDEO && index >= 0) {
            val old = player
            val position = old.currentPosition.coerceAtLeast(0L)
            val wantsPlay = old.playWhenReady
            old.pause()
            val replacement = newPlayer(mode)
            attachListeners(replacement)
            player = replacement
            videoDecoderName = null
            error = null
            replacement.setMediaItems(queue.map(::toMediaItem), index, position)
            replacement.prepare()
            replacement.playWhenReady = wantsPlay
            old.release()
        }
        publish()
    }

    override fun next() {
        if (index + 1 < queue.size) { play(queue, index + 1) }
    }

    override fun previous() {
        if (player.currentPosition > 3_000L) seekTo(0) else if (index > 0) play(queue, index - 1)
    }

    fun refreshPosition() = publish()

    fun stopOnline() { if (queue.getOrNull(index)?.isOnline == true) { player.stop(); publish() } }

    fun exoPlayer(): ExoPlayer = player

    override fun close() {
        player.release()
        mediaHeaderSnapshot.set(emptyMap())
        queue = emptyList()
        index = -1
        _state.value = PlaybackSnapshot()
    }

    private fun newPlayer(mode: DecoderMode): ExoPlayer {
        val selector = MediaCodecSelector { mimeType, secure, tunneling ->
            val codecs = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
            DecoderSelectionPolicy.select(
                candidates = codecs,
                mode = mode,
                isHardwareAccelerated = { it.hardwareAccelerated },
                isSoftwareOnly = { it.softwareOnly || DecoderSelectionPolicy.isKnownPlatformSoftwareCodec(it.name) },
            )
        }
        val renderers = DefaultRenderersFactory(appContext)
            .setMediaCodecSelector(selector)
            .setEnableDecoderFallback(mode != DecoderMode.STRICT_HARDWARE)
        val dataSource = DefaultDataSource.Factory(appContext, OnlineAudioDataSource.Factory(networkAllowed) { url ->
            mediaHeaderSnapshot.get()[url]?.get(java.net.URI(url).host).orEmpty()
        })
        return ExoPlayer.Builder(appContext, renderers).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build().also { it.playbackParameters = PlaybackParameters(speed) }
    }

    private fun attachListeners(target: ExoPlayer) {
        target.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (target !== this@AndroidMedia3PlaybackBackend.player) return
                index = target.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
                mediaGeneration++
                publish()
            }
            override fun onEvents(player: Player, events: Player.Events) = publish()
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                this@AndroidMedia3PlaybackBackend.videoSize = videoSize
                publish()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                this@AndroidMedia3PlaybackBackend.error = "播放失败（${error.errorCodeName}）"
                publish()
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val audio = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
                    ?.getTrackFormat(0)
                audioSampleRate = audio?.sampleRate?.takeIf { it > 0 }
                audioChannels = audio?.channelCount?.takeIf { it > 0 }
                publish()
            }
        })
        target.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                videoDecoderName = decoderName
                publish()
            }
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                audioDecoderName = decoderName
                publish()
            }
        })
    }

    private fun toMediaItem(media: LocalMedia): MediaItem = MediaItem.Builder()
        .setUri(media.uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(if (media.kind == MediaKind.AUDIO) media.musicTitle else media.title)
            .setArtist(media.artist).setAlbumTitle(media.album).build())
        .apply { media.mimeType?.let(::setMimeType) }
        .build()

    private fun publish() {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0L } ?: 0L
        val activeVideoMime = player.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 }
            ?.getTrackFormat(0)?.sampleMimeType
        val decoderInfo = if (videoDecoderName != null && activeVideoMime != null) {
            runCatching {
                MediaCodecSelector.DEFAULT.getDecoderInfos(activeVideoMime, false, false)
                    .firstOrNull { it.name == videoDecoderName }
            }.getOrNull()
        } else {
            null
        }
        val hardwareFlag = decoderInfo?.hardwareAccelerated
        val softwareFlag = decoderInfo?.softwareOnly
        _state.value = PlaybackSnapshot(
            mediaGeneration = mediaGeneration,
            queue = queue,
            queueIndex = index,
            phase = when {
                error != null -> PlaybackPhase.ERROR
                player.playbackState == Player.STATE_BUFFERING -> PlaybackPhase.BUFFERING
                player.playbackState == Player.STATE_READY -> PlaybackPhase.READY
                player.playbackState == Player.STATE_ENDED -> PlaybackPhase.ENDED
                else -> PlaybackPhase.IDLE
            },
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            speed = speed,
            decoderMode = decoderMode,
            videoDecoderName = videoDecoderName,
            videoDecoderHardwareFlag = hardwareFlag,
            videoDecoderSoftwareFlag = softwareFlag,
            audioDecoderName = audioDecoderName,
            audioSampleRate = audioSampleRate,
            audioChannels = audioChannels,
            videoWidth = videoSize.width,
            videoHeight = videoSize.height,
            videoPixelRatio = videoSize.pixelWidthHeightRatio,
            videoRotationDegrees = videoSize.unappliedRotationDegrees,
            error = error,
        )
    }
}
