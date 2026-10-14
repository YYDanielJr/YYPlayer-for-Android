package yy.daniel.yyplayerforandroid.playback

import android.content.Context
import androidx.core.content.edit
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
    private val orderStorage = appContext.getSharedPreferences("playback_order_v1", Context.MODE_PRIVATE)
    private var playOrder = PlayOrder.entries.firstOrNull { it.name == orderStorage.getString("music", null) } ?: PlayOrder.SEQUENTIAL
    private var fallbackReason: String? = null
    private var expectedRoute: Int? = null
    private var fallbackRoute: Int? = null
    private fun routeId(): Int? = selectedDeviceId() ?: yy.daniel.yyplayerforandroid.audio.AudioDeviceProbe.anticipatedDeviceId(
        appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager,
        android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
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
        if (path == appliedOutputPath && fallbackReason == null) return
        appliedOutputPath = path
        fallbackReason = null; fallbackRoute = null
        negotiated = null
        rebuildPlayer()
    }
    internal var onPlayerReplaced: ((ExoPlayer) -> Unit)? = null
    internal fun reconfigureOutput() { negotiated = null; fallbackReason = null; fallbackRoute = null; rebuildPlayer() }

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
        val desiredPath = if (queue[startIndex].kind == MediaKind.VIDEO || fallbackReason != null)
            yy.daniel.yyplayerforandroid.audio.OutputPath.SYSTEM else appliedOutputPath
        if (desiredPath != playerOutputPath) {
            rebuildPlayer(0L)
        }
        player.setMediaItems(queue.map(::toMediaItem), startIndex, 0L)
        applyOrder(player)
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

    override fun next() { if (!outputTestActive && selectedDevicePresent()) { player.seekToNextMediaItem(); publish() } }

    override fun previous() {
        if (outputTestActive || !selectedDevicePresent()) return
        if (player.currentPosition > 3_000L) seekTo(0) else { player.seekToPreviousMediaItem(); publish() }
    }

    override fun setPlayOrder(order: PlayOrder) {
        playOrder = order
        orderStorage.edit { putString("music", order.name) }
        applyOrder(player); publish()
    }
    private fun applyOrder(target: ExoPlayer) {
        val order = if (queue.getOrNull(index)?.kind == MediaKind.VIDEO) PlayOrder.SEQUENTIAL else playOrder
        target.repeatMode = when (order) { PlayOrder.SEQUENTIAL -> Player.REPEAT_MODE_OFF; PlayOrder.SINGLE -> Player.REPEAT_MODE_ONE; PlayOrder.RANDOM -> Player.REPEAT_MODE_ALL }
        target.shuffleModeEnabled = order == PlayOrder.RANDOM
    }
    override fun jumpTo(index: Int) {
        if (outputTestActive || index !in queue.indices || !selectedDevicePresent()) return
        if (queue[index].isOnline && !networkAllowed()) return
        error = null
        player.seekToDefaultPosition(index)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play(); publish()
    }
    override fun enqueue(items: List<LocalMedia>, next: Boolean) {
        if (outputTestActive || items.isEmpty()) return
        if (queue.isEmpty()) { play(items, 0); return }
        val requested = items.filter { it.kind == queue.first().kind && (!it.isOnline || networkAllowed()) }.distinctBy { it.identity }
        val additions = requested.filterNot { item -> queue.any { it.identity == item.identity } }.take((20_000 - queue.size).coerceAtLeast(0))
        val at = if (next) (index + 1).coerceIn(0, queue.size) else queue.size
        queue = queue.toMutableList().apply { addAll(at, additions) }
        mediaHeaderSnapshot.set(queue.associate { it.uri.toString() to it.mediaHeaders })
        player.addMediaItems(at, additions.map(::toMediaItem))
        if (next) {
            requested.asReversed().forEach { item ->
                val from = queue.indexOfFirst { it.identity == item.identity }
                if (from >= 0 && from != index) moveQueueItem(from, (index + if (from < index) 0 else 1).coerceAtMost(queue.lastIndex))
            }
            if (player.shuffleModeEnabled) {
                val priority = requested.mapNotNull { item -> queue.indexOfFirst { it.identity == item.identity }.takeIf { it >= 0 && it != index } }
                val order = listOf(index) + priority + queue.indices.filter { it != index && it !in priority }.shuffled()
                player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
            }
        }
        publish()
    }
    override fun removeQueueItem(index: Int) {
        if (outputTestActive || index !in queue.indices) return
        queue = queue.toMutableList().apply { removeAt(index) }
        mediaHeaderSnapshot.set(queue.associate { it.uri.toString() to it.mediaHeaders })
        player.removeMediaItem(index)
        this.index = if (queue.isEmpty()) -1 else player.currentMediaItemIndex
        publish()
    }
    override fun moveQueueItem(from: Int, to: Int) {
        if (outputTestActive || from !in queue.indices || to !in queue.indices || from == to) return
        queue = queue.toMutableList().apply { add(to, removeAt(from)) }
        player.moveMediaItem(from, to); index = player.currentMediaItemIndex; publish()
    }
    override fun clearQueue() {
        if (outputTestActive) return
        queue = emptyList(); index = -1
        player.stop(); player.clearMediaItems(); mediaHeaderSnapshot.set(emptyMap()); error = null; publish()
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
        expectedRoute = routeId()
        // The current AudioTrack backend cannot prove exclusive sharing. Prefer mode uses the
        // compatible system sink; strict mode continues to report refusal from the policy gate.
        if (appliedOutputPath == yy.daniel.yyplayerforandroid.audio.OutputPath.NATIVE_EXACT &&
            sharingRequest() == yy.daniel.yyplayerforandroid.audio.SharingRequest.PREFER_EXCLUSIVE &&
            transparencyRequest() != yy.daniel.yyplayerforandroid.audio.TransparencyRequest.STRICT_UNPROCESSED &&
            selectedDevicePresent()) {
            fallbackReason = "当前输出不支持已验证独占，已使用系统输出"
            fallbackRoute = expectedRoute
        }
        playerOutputPath = if (queue.getOrNull(index)?.kind == MediaKind.VIDEO || fallbackReason != null)
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
                applyOrder(it)
                it.playbackParameters = if (rendererPath == yy.daniel.yyplayerforandroid.audio.OutputPath.NATIVE_EXACT)
                    PlaybackParameters.DEFAULT else PlaybackParameters(speed)
                if (rendererPath == yy.daniel.yyplayerforandroid.audio.OutputPath.SYSTEM) {
                    val id = fallbackRoute ?: selectedDeviceId()
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
                if (target === this@AndroidMedia3PlaybackBackend.player) {
                    if (queue.getOrNull(index)?.kind == MediaKind.AUDIO &&
                        (events.contains(Player.EVENT_REPEAT_MODE_CHANGED) || events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED))) {
                        playOrder = when { player.shuffleModeEnabled -> PlayOrder.RANDOM; player.repeatMode == Player.REPEAT_MODE_ONE -> PlayOrder.SINGLE; else -> PlayOrder.SEQUENTIAL }
                        orderStorage.edit { putString("music", playOrder.name) }
                    }
                    publish()
                }
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (target !== this@AndroidMedia3PlaybackBackend.player) return
                this@AndroidMedia3PlaybackBackend.videoSize = videoSize
                publish()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (target !== this@AndroidMedia3PlaybackBackend.player) return
                negotiated = null
                onNegotiated(null)
                val causes = generateSequence<Throwable>(error) { it.cause }.take(12).toList()
                val outputError = causes.any { it is androidx.media3.exoplayer.audio.AudioSink.ConfigurationException ||
                    it is androidx.media3.exoplayer.audio.AudioSink.InitializationException || it is androidx.media3.exoplayer.audio.AudioSink.WriteException }
                val routeFailure = causes.any { it.message.orEmpty().contains("disconnected", true) || it.message.orEmpty().contains("route", true) }
                val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val present = manager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { it.id == expectedRoute }
                if (playerOutputPath == yy.daniel.yyplayerforandroid.audio.OutputPath.NATIVE_EXACT && fallbackReason == null && outputError && !routeFailure &&
                    yy.daniel.yyplayerforandroid.audio.OutputFallbackPolicy.allowed(sharingRequest(), transparencyRequest(), expectedRoute, routeId(), present)) {
                    val revision = outputRevision
                    val generation = mediaGeneration
                    val failedRoute = expectedRoute
                    playerHandler.post {
                        if (target === this@AndroidMedia3PlaybackBackend.player && revision == outputRevision && generation == mediaGeneration &&
                            failedRoute == routeId() && selectedDevicePresent()) {
                            fallbackReason = "自研输出不可用（${error.errorCodeName}），已回退系统输出"
                            fallbackRoute = failedRoute
                            rebuildPlayer()
                        }
                    }
                }
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
            playOrder = if (queue.getOrNull(index)?.kind == MediaKind.VIDEO) PlayOrder.SEQUENTIAL else playOrder,
            outputFallback = fallbackReason.takeIf { queue.getOrNull(index)?.kind == MediaKind.AUDIO },
        )
    }
}
