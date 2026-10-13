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
class AndroidMedia3PlaybackBackend(
    context: Context,
    private val networkAllowed: () -> Boolean = { false },
    /**
     * Which output path the player is built with. Read at construction and whenever the setting
     * changes, because an audio sink owns the platform stream for the life of the player.
     */
    private val outputPath: () -> yy.daniel.yyplayerforandroid.audio.OutputPath = { yy.daniel.yyplayerforandroid.audio.OutputPath.SYSTEM },
    /** The device the user pinned, or null to follow the system route. */
    private val selectedDeviceId: () -> Int? = { null },
    /** Reports what the native output negotiated, so the UI can present it as evidence. */
    private val onNegotiated: (yy.daniel.yyplayerforandroid.audio.NativeOutputAudioSink.Negotiated?) -> Unit = {},
    private val sharingRequest: () -> yy.daniel.yyplayerforandroid.audio.SharingRequest = { yy.daniel.yyplayerforandroid.audio.SharingRequest.SHARED },
    private val transparencyRequest: () -> yy.daniel.yyplayerforandroid.audio.TransparencyRequest = { yy.daniel.yyplayerforandroid.audio.TransparencyRequest.PREFER_UNPROCESSED },
) : PlaybackBackend {
    private val appContext = context.applicationContext
    // Property initialization order is part of the contract here: `player` is built by newPlayer(),
    // which reads both `speed` and `appliedOutputPath`, so those must be declared before it. Leaving
    // `appliedOutputPath` after `player` made the renderers factory read a null enum.
    private var speed = 1f
    private var appliedOutputPath = outputPath()
    private val mediaHeaderSnapshot = java.util.concurrent.atomic.AtomicReference<Map<String, Map<String, Map<String, String>>>>(emptyMap())
    private var queue: List<LocalMedia> = emptyList()
    private var index: Int = -1
    private var playerOutputPath = yy.daniel.yyplayerforandroid.audio.OutputPath.SYSTEM
    private val playerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var outputRevision = 0L
    private var player: ExoPlayer = newPlayer(DecoderMode.AUTO)
    private var mediaGeneration = 0L
    private var decoderMode = DecoderMode.AUTO
    private var videoDecoderName: String? = null
    private var audioDecoderName: String? = null
    private var audioSampleRate: Int? = null
    private var audioChannels: Int? = null
    private var videoSize = VideoSize.UNKNOWN
    private var error: String? = null
    private var outputTestActive = false
    private var testPositionMs = 0L
    private fun selectedDevicePresent(): Boolean {
        val id = selectedDeviceId() ?: return true
        val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        return manager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { it.id == id }
    }
    private var negotiated: yy.daniel.yyplayerforandroid.audio.NativeOutputAudioSink.Negotiated? = null
    private val _state = MutableStateFlow(PlaybackSnapshot())
    override val state: StateFlow<PlaybackSnapshot> = _state.asStateFlow()

    init { attachListeners(player) }

    /**
     * Applies the output-path setting by rebuilding the player at the current position.
     *
     * Both paths own the platform stream, so they cannot coexist; this is the same rebuild the decoder
     * mode already performs, and it keeps the queue, position and play intent.
     */
    fun setOutputPath(path: yy.daniel.yyplayerforandroid.audio.OutputPath) {
        if (path == appliedOutputPath) return
        appliedOutputPath = path
        negotiated = null
        rebuildPlayer()
    }
    internal var onPlayerReplaced: ((ExoPlayer) -> Unit)? = null
    internal fun reconfigureOutput() { negotiated = null; rebuildPlayer() }

    private fun rebuildPlayer(positionOverride: Long? = null) {
        if (outputTestActive) return
        val old = player
        val position = positionOverride ?: old.currentPosition.coerceAtLeast(0L)
        val wantsPlay = old.playWhenReady
        val retainedVolume = old.volume
        old.pause()
        val replacement = newPlayer(decoderMode)
        attachListeners(replacement)
        player = replacement
        replacement.volume = retainedVolume
        onPlayerReplaced?.invoke(replacement)
        // Release the previous output before preparing a replacement stream.
        old.release()
        videoDecoderName = null
        audioDecoderName = null
        error = null
        if (queue.isNotEmpty() && index in queue.indices) {
            replacement.setMediaItems(queue.map(::toMediaItem), index, position)
            replacement.prepare()
            replacement.playWhenReady = wantsPlay
        }
        publish()
    }

    override fun play(queue: List<LocalMedia>, startIndex: Int) {
        if (outputTestActive) return
        if (!selectedDevicePresent()) { error = "所选输出设备已断开"; publish(); return }
        if (queue.isEmpty() || startIndex !in queue.indices) return
        if (queue.any { it.kind != queue[startIndex].kind }) {
            error = "音频和视频需使用独立播放队列"; publish(); return
        }
        if (queue[startIndex].isOnline && !networkAllowed()) { error = "联网音乐已关闭"; publish(); return }
        this.queue = queue.toList()
        mediaHeaderSnapshot.set(queue.associate { it.uri.toString() to it.mediaHeaders })
        index = startIndex
        error = null
        videoDecoderName = null
        audioDecoderName = null
        videoSize = VideoSize.UNKNOWN
        val desiredPath = if (queue[startIndex].kind == MediaKind.VIDEO)
            yy.daniel.yyplayerforandroid.audio.OutputPath.SYSTEM else appliedOutputPath
        if (desiredPath != playerOutputPath) {
            rebuildPlayer(0L)
        }
        player.setMediaItems(queue.map(::toMediaItem), startIndex, 0L)
        player.prepare()
        player.playWhenReady = true
        publish()
    }

    override fun togglePlayPause() {
        if (outputTestActive) return
        if (!selectedDevicePresent()) { error = "所选输出设备已断开"; publish(); return }
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
        if (playerOutputPath == yy.daniel.yyplayerforandroid.audio.OutputPath.NATIVE_EXACT) {
            player.playbackParameters = PlaybackParameters.DEFAULT
            publish(); return
        }
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
        // Only a video stream needs the decoder rebuilt; an audio-only stream keeps playing.
        if (_state.value.current?.kind == MediaKind.VIDEO && index >= 0) {
            error = null
            rebuildPlayer()
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

    internal fun stopForOutputLoss() {
        player.pause()
        player.stop()
        error = "输出已停止，请选择设备后重新播放"
        publish()
    }

    internal fun setOutputTestActive(active: Boolean) {
        outputTestActive = active
        if (active) {
            // release() acknowledges renderer/track teardown before the fixture opens its track.
            val old = player
            testPositionMs = old.currentPosition.coerceAtLeast(0L)
            val retainedVolume = old.volume
            old.pause()
            val replacement = newPlayer(decoderMode)
            player = replacement
            replacement.volume = retainedVolume
            attachListeners(replacement)
            onPlayerReplaced?.invoke(replacement)
            old.release()
            negotiated = null
            onNegotiated(null)
        } else if (queue.isNotEmpty() && index in queue.indices && selectedDevicePresent()) {
            player.setMediaItems(queue.map(::toMediaItem), index, testPositionMs)
            player.prepare()
            player.playWhenReady = false
        }
        publish()
    }

    override fun close() {
        outputRevision++
        player.release()
        mediaHeaderSnapshot.set(emptyMap())
        queue = emptyList()
        index = -1
        _state.value = PlaybackSnapshot()
    }

    private fun newPlayer(mode: DecoderMode): ExoPlayer {
        val configuringRevision = ++outputRevision
        playerOutputPath = if (queue.getOrNull(index)?.kind == MediaKind.VIDEO)
            yy.daniel.yyplayerforandroid.audio.OutputPath.SYSTEM else appliedOutputPath
        val rendererPath = playerOutputPath
        val selector = MediaCodecSelector { mimeType, secure, tunneling ->
            val codecs = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
            if (!mimeType.startsWith("video/")) codecs else DecoderSelectionPolicy.select(
                candidates = codecs,
                mode = mode,
                isHardwareAccelerated = { it.hardwareAccelerated },
                isSoftwareOnly = { it.softwareOnly || DecoderSelectionPolicy.isKnownPlatformSoftwareCodec(it.name) },
            )
        }
        val renderers = OutputPathRenderersFactory(
            appContext = appContext,
            outputPath = { rendererPath },
            selectedDeviceId = selectedDeviceId,
            onNegotiated = { result ->
                // Sink configuration runs on ExoPlayer's playback thread. UI projection and Player
                // getters belong to its application looper, and an old sink may complete after rebuild.
                playerHandler.post {
                    if (outputRevision == configuringRevision) {
                        negotiated = result
                        onNegotiated(result)
                        publish()
                    }
                }
            },
            sharingRequest = sharingRequest,
            transparencyRequest = transparencyRequest,
        ).setMediaCodecSelector(selector)
            .setEnableDecoderFallback(mode != DecoderMode.STRICT_HARDWARE)
        val dataSource = DefaultDataSource.Factory(appContext, OnlineAudioDataSource.Factory(networkAllowed) { url ->
            mediaHeaderSnapshot.get()[url]?.get(java.net.URI(url).host).orEmpty()
        })
        return ExoPlayer.Builder(appContext, renderers).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build().also {
                it.playbackParameters = if (rendererPath == yy.daniel.yyplayerforandroid.audio.OutputPath.NATIVE_EXACT)
                    PlaybackParameters.DEFAULT else PlaybackParameters(speed)
                if (rendererPath == yy.daniel.yyplayerforandroid.audio.OutputPath.SYSTEM) {
                    val id = selectedDeviceId()
                    val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    it.setPreferredAudioDevice(manager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                        .firstOrNull { device -> device.id == id })
                }
            }
    }

    private fun attachListeners(target: ExoPlayer) {
        target.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (target !== this@AndroidMedia3PlaybackBackend.player) return
                index = target.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
                mediaGeneration++
                publish()
            }
            override fun onEvents(player: Player, events: Player.Events) {
                if (target === this@AndroidMedia3PlaybackBackend.player) publish()
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (target !== this@AndroidMedia3PlaybackBackend.player) return
                this@AndroidMedia3PlaybackBackend.videoSize = videoSize
                publish()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (target !== this@AndroidMedia3PlaybackBackend.player) return
                negotiated = null
                this@AndroidMedia3PlaybackBackend.error = "播放失败（${error.errorCodeName}）"
                publish()
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                if (target !== this@AndroidMedia3PlaybackBackend.player) return
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
                if (target !== this@AndroidMedia3PlaybackBackend.player) return
                videoDecoderName = decoderName
                publish()
            }
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                if (target !== this@AndroidMedia3PlaybackBackend.player) return
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
            speed = player.playbackParameters.speed,
            decoderMode = decoderMode,
            videoDecoderName = videoDecoderName,
            videoDecoderHardwareFlag = hardwareFlag,
            videoDecoderSoftwareFlag = softwareFlag,
            audioDecoderName = audioDecoderName,
            audioSampleRate = audioSampleRate,
            audioChannels = audioChannels,
            outputPath = playerOutputPath,
            negotiatedOutput = negotiated,
            videoWidth = videoSize.width,
            videoHeight = videoSize.height,
            videoPixelRatio = videoSize.pixelWidthHeightRatio,
            videoRotationDegrees = videoSize.unappliedRotationDegrees,
            error = error,
        )
    }
}
