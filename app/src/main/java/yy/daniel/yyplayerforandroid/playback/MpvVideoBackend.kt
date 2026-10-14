package yy.daniel.yyplayerforandroid.playback

import android.content.*
import android.graphics.SurfaceTexture
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.*
import android.system.Os
import android.system.OsConstants
import android.view.Surface
import android.view.TextureView
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One native video engine, a real Media3 session adapter, and bounded off-main control transport. */
@UnstableApi
internal class MpvVideoBackend(private val context: Context, generation: Long, initialVolume: Float,
    private val changed: (PlaybackSnapshot) -> Unit) : PlaybackBackend {
    override val state = MutableStateFlow(PlaybackSnapshot(mediaGeneration = generation, videoEngine = VideoEngine.MPV))
    val player = SessionPlayer()
    private val main = Handler(Looper.getMainLooper())
    private val io = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(64)) { task ->
        Thread(task, "YY-mpv-control").apply { isDaemon = true }
    }
    @Volatile private var closed = false
    @Volatile private var revision = generation
    // Native handle and FD belong exclusively to io. Surface leases retire after native destruction.
    private var handle = 0L
    private var descriptor: ParcelFileDescriptor? = null
    private var output: Surface? = null
    private var texture: TextureView? = null
    private val surfaces = java.util.concurrent.ConcurrentHashMap<Surface, Long>()
    private val retiredTextures = java.util.concurrent.ConcurrentHashMap.newKeySet<SurfaceTexture>()
    private val nativeStopped = AtomicBoolean(false)
    private var volume = initialVolume.coerceIn(0f, 1f)
    private var wantsPlay = false
    private var stopped = false
    private var baseSpeed = 1f
    val normalSpeed: Float get() = baseSpeed
    private var boost: Long? = null
    private var boostSequence = 0L
    private var external: VideoSubtitle? = null
    private val copies = mutableSetOf<java.io.File>()
    private val settings = linkedMapOf<String, String>()
    private val polling = AtomicBoolean(false)
    private val seekPending = java.util.concurrent.atomic.AtomicReference<Pair<Long, Long>?>(null)
    private val seeking = AtomicBoolean(false)
    private var endHandled = false
    private var textureRevision = 0L
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build())
        .setOnAudioFocusChangeListener({ change -> if (change < 0) pause() }, main).build()
    private val noisy = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { pause() } }
    init {
        androidx.core.content.ContextCompat.registerReceiver(context, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        main.post(object : Runnable {
            override fun run() { if (!closed) { refresh(); main.postDelayed(this, 120) } }
        })
    }
    private fun emit(value: PlaybackSnapshot) {
        if (closed) return
        state.value = value; player.changed(); changed(value)
    }
    private fun failure(error: Throwable) {
        if (!closed) { wantsPlay = false; emit(state.value.copy(isPlaying = false, error = error.message ?: "libmpv 操作失败", phase = PlaybackPhase.ERROR)) }
    }
    private fun work(expected: Long = revision, block: () -> Unit): ListenableFuture<Void> {
        val result = SettableFuture.create<Void>()
        if (closed) { result.cancel(false); return result }
        try { io.execute {
            if (closed || expected != revision) { result.cancel(false); return@execute }
            try { block(); main.post { if (!closed && expected == revision) result.set(null) else result.cancel(false) } }
            catch (e: Exception) { main.post { if (expected == revision) failure(e); result.setException(e) } }
        } } catch (e: java.util.concurrent.RejectedExecutionException) { result.setException(e); failure(IllegalStateException("操作过于频繁，请稍后重试")) }
        return result
    }
    private fun command(vararg args: String): ListenableFuture<Void> = work {
        check(handle != 0L) { "视频仍在加载" }; MpvNative.call(handle, arrayOf(*args))
    }
    private fun property(name: String, value: String): ListenableFuture<Void> {
        settings[name] = value
        return command("set", name, value)
    }
    private fun seconds(ms: Long) = (ms / 1000.0).toString()
    private fun destroyNative() {
        if (handle != 0L) { MpvNative.close(handle); handle = 0 }
        descriptor?.close(); descriptor = null
        retiredTextures.toList().forEach { if (retiredTextures.remove(it)) it.release() }
    }
    private fun rebuild(nextOutput: Surface? = output, retired: SurfaceTexture? = null) {
        output = nextOutput
        val surfaceId = ++textureRevision
        if (nextOutput != null) surfaces[nextOutput] = surfaceId
        if (retired != null) retiredTextures.add(retired)
        val target = state.value.current ?: run {
            // Clearing the queue can remove the Compose page before mpv's stop has finished.
            // Keep exactly the same destruction fence as an ordinary surface replacement.
            work { destroyNative(); releaseSurfacesBefore(surfaceId) }
            return
        }
        val retained = state.value
        val play = wantsPlay
        val style = settings.toMap()
        val subtitle = external
        val speed = baseSpeed
        boost = null; endHandled = false
        val generation = revision
        emit(retained.copy(phase = PlaybackPhase.BUFFERING, isPlaying = false, error = null))
        work {
            destroyNative()
            releaseSurfacesBefore(surfaceId)
            // Opening and seekability checks are background work. No pathname guessing for SAF URIs.
            descriptor = context.contentResolver.openFileDescriptor(target.uri, "r") ?: error("无法打开视频")
            try { Os.lseek(descriptor!!.fileDescriptor, 0, OsConstants.SEEK_CUR) }
            catch (_: Exception) { descriptor?.close(); descriptor = null; error("此来源不支持随机读取，请先用系统文件管理器保存到本地") }
            handle = MpvNative.create(context.applicationContext, nextOutput?.takeIf { it.isValid })
            MpvNative.call(handle, arrayOf("set", "hwdec", if (retained.decoderMode == DecoderMode.SOFTWARE) "no" else "mediacodec-copy"))
            MpvNative.call(handle, arrayOf("set", "volume", (volume * 100).toString()))
            MpvNative.call(handle, arrayOf("set", "speed", speed.toString()))
            style.forEach { (name, value) -> MpvNative.call(handle, arrayOf("set", name, value)) }
            MpvNative.call(handle, arrayOf("loadfile", "/proc/self/fd/${descriptor!!.fd}", "replace", "-1",
                "start=${seconds(retained.positionMs)}"))
            // Wait on this IO thread for actual FILE_LOADED, not merely command acceptance.
            var loaded = false
            for (attempt in 0 until 200) {
                if (closed || generation != revision) return@work
                val data = JSONObject(MpvNative.call(handle, arrayOf("snapshot")))
                if (data.optString("error").isNotBlank()) error(data.optString("error"))
                if (data.optBoolean("loaded")) { loaded = true; break }
                Thread.sleep(20)
            }
            check(loaded) { "视频加载超时" }
            if (closed || generation != revision) return@work
            if (subtitle != null) MpvNative.call(handle, arrayOf("sub-add", subtitle.uri.path!!, "select", subtitle.name))
            MpvNative.call(handle, arrayOf("set", "pause", if (play) "no" else "yes"))
            main.post { if (!closed && textureRevision == surfaceId) refresh() }
        }
    }
    private fun releaseSurfacesBefore(surfaceId: Long) {
        surfaces.entries.filter { it.value < surfaceId }.forEach { entry ->
            if (surfaces.remove(entry.key, entry.value)) entry.key.release()
        }
    }
    override fun play(queue: List<LocalMedia>, startIndex: Int) {
        if (queue.isEmpty() || startIndex !in queue.indices || queue.any { it.kind != MediaKind.VIDEO || it.isOnline }) return
        revision++; external = null; settings.clear(); boost = null; baseSpeed = 1f; wantsPlay = true; stopped = false
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) wantsPlay = false
        emit(PlaybackSnapshot(mediaGeneration = revision, queue = queue.toList(), queueIndex = startIndex,
            phase = PlaybackPhase.BUFFERING, videoEngine = VideoEngine.MPV, decoderMode = state.value.decoderMode,
            playOrder = state.value.playOrder))
        rebuild()
    }
    fun pause(): ListenableFuture<Void> { wantsPlay = false; cancelBoost(); return property("pause", "yes") }
    private fun resume(): ListenableFuture<Void> {
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return pause()
        wantsPlay = true
        if (stopped) { stopped = false; rebuild(); return work { } }
        if (state.value.phase == PlaybackPhase.ENDED) seekTo(0)
        return property("pause", "no")
    }
    override fun togglePlayPause() { if (wantsPlay) pause() else resume() }
    private fun stopPlayback(): ListenableFuture<Void> {
        wantsPlay = false; cancelBoost(); stopped = true
        val generation = revision
        return work {
            if (handle != 0L) {
                MpvNative.call(handle, arrayOf("stop"))
                for (attempt in 0 until 150) {
                    if (!JSONObject(MpvNative.call(handle, arrayOf("snapshot"))).optBoolean("loaded")) break
                    Thread.sleep(20)
                }
            }
            main.post { if (!closed && revision == generation && stopped) emit(state.value.copy(isPlaying = false, phase = PlaybackPhase.IDLE)) }
        }
    }
    override fun seekTo(positionMs: Long) {
        val s = state.value
        if (s.loopEndMs != null && positionMs !in s.loopStartMs!!..s.loopEndMs) clearLoop()
        endHandled = false
        seekPending.set(revision to positionMs.coerceIn(0, s.durationMs.coerceAtLeast(0)))
        drainSeek()
    }
    private fun drainSeek() {
        if (!seeking.compareAndSet(false, true)) return
        val future = work {
            var count = 0
            while (!closed && count++ < 4) {
                val target = seekPending.getAndSet(null) ?: break
                if (target.first == revision && handle != 0L) MpvNative.call(handle, arrayOf("seek", seconds(target.second), "absolute+exact"))
            }
        }
        future.addListener({ main.post { seeking.set(false); if (!closed && seekPending.get() != null) drainSeek() } }, java.util.concurrent.Executor { it.run() })
    }
    override fun seekBy(deltaMs: Long) = seekTo(state.value.positionMs + deltaMs)
    override fun setSpeed(speed: Float) {
        if (!speed.isFinite()) return
        baseSpeed = speed.coerceIn(.1f, 10f); boost = null; property("speed", baseSpeed.toString())
    }
    override fun beginSpeedBoost(): Long? {
        if (closed || state.value.phase != PlaybackPhase.READY) return null
        val token = ++boostSequence; boost = token; property("speed", "3"); return token
    }
    override fun endSpeedBoost(token: Long, baseSpeed: Float) { if (boost == token) cancelBoost() }
    private fun cancelBoost() { if (boost != null) { boost = null; property("speed", baseSpeed.toString()) } }
    override fun setDecoderMode(mode: DecoderMode) {
        // Strict remains on the established codec-qualified backend until hardware identity is proven.
        if (mode == DecoderMode.STRICT_HARDWARE) { failure(IllegalStateException("libmpv 严格硬解尚未通过设备资格验证，请切换 Media3 使用仅硬解")); pause(); return }
        emit(state.value.copy(decoderMode = mode)); rebuild()
    }
    override fun next() { val s = state.value; if (s.queueIndex < s.queue.lastIndex) jumpTo(s.queueIndex + 1) }
    override fun previous() { if (state.value.positionMs > 3000) seekTo(0) else if (state.value.queueIndex > 0) jumpTo(state.value.queueIndex - 1) }
    override fun jumpTo(index: Int) = play(state.value.queue, index)
    override fun setPlayOrder(order: PlayOrder) { emit(state.value.copy(playOrder = order)) }
    override fun enqueue(items: List<LocalMedia>, next: Boolean) {
        val s = state.value
        val additions = items.filter { it.kind == MediaKind.VIDEO && !it.isOnline && it.identity !in s.queue.map(LocalMedia::identity) }.distinctBy(LocalMedia::identity)
        emit(s.copy(queue = s.queue.toMutableList().apply { addAll(if (next) s.queueIndex + 1 else size, additions) }.take(20_000)))
    }
    override fun removeQueueItem(index: Int) {
        val s = state.value; if (index !in s.queue.indices) return
        val queue = s.queue.toMutableList().apply { removeAt(index) }
        if (queue.isEmpty()) clearQueue() else if (index == s.queueIndex) play(queue, index.coerceAtMost(queue.lastIndex))
        else emit(s.copy(queue = queue, queueIndex = s.queueIndex - if (index < s.queueIndex) 1 else 0))
    }
    override fun moveQueueItem(from: Int, to: Int) {
        val s = state.value; if (from !in s.queue.indices || to !in s.queue.indices) return
        val queue = s.queue.toMutableList().apply { add(to, removeAt(from)) }
        emit(s.copy(queue = queue, queueIndex = queue.indexOf(s.current)))
    }
    override fun clearQueue() { pause(); command("stop"); emit(state.value.copy(queue = emptyList(), queueIndex = -1, phase = PlaybackPhase.IDLE)) }
    override fun selectTrack(type: Int, group: Int?, track: Int, generation: Long) {
        if (generation != revision || type !in listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)) return
        if (group != null && state.value.tracks.none { it.group == group && it.type == type }) return
        property(if (type == C.TRACK_TYPE_AUDIO) "aid" else "sid", group?.toString() ?: "no")
    }
    fun attachSubtitle(subtitle: VideoSubtitle, uri: android.net.Uri, generation: Long): Boolean {
        if (revision != generation || uri != state.value.current?.uri) return false
        copies.add(java.io.File(subtitle.uri.path!!))
        val result = command("sub-add", subtitle.uri.path!!, "select", subtitle.name)
        result.addListener({ main.post {
            if (!closed && generation == revision && !result.isCancelled && runCatching { result.get() }.isSuccess) {
                external = subtitle; emit(state.value.copy(subtitleName = subtitle.name))
            }
        } }, java.util.concurrent.Executor { it.run() })
        return true
    }
    fun removeExternalSubtitle() { external = null; settings.remove("sid"); emit(state.value.copy(subtitleName = null)); rebuild() }
    fun step(forward: Boolean) {
        wantsPlay = false; cancelBoost(); settings["pause"] = "yes"
        work {
            check(handle != 0L) { "视频仍在加载" }
            MpvNative.call(handle, arrayOf("set", "pause", "yes"))
            // A seek command ACK precedes the decoded target frame. Wait for the real seek to
            // settle so a quick tap cannot consume the seek's first frame as the requested step.
            var settled = false
            for (attempt in 0 until 150) {
                if (closed) return@work
                val data = JSONObject(MpvNative.call(handle, arrayOf("snapshot")))
                if (!data.optBoolean("seeking")) { settled = true; break }
                Thread.sleep(20)
            }
            check(settled) { "正在定位画面，请稍后逐帧" }
            MpvNative.call(handle, arrayOf(if (forward) "frame-step" else "frame-back-step"))
        }
    }
    fun setDelay(subtitle: Boolean, ms: Long) { property(if (subtitle) "sub-delay" else "audio-delay", seconds(ms.coerceIn(-10000, 10000))) }
    fun setFont(font: String, overrideAss: Boolean) {
        if (font !in listOf("sans-serif", "serif", "monospace")) return
        property("sub-font", font); property("sub-ass-override", if (overrideAss) "force" else "no")
    }
    fun setSubtitleAppearance(size: Float, bottomPadding: Float) {
        val fontSize = (size * 1.8f).toInt().coerceIn(16, 80).toString()
        val position = ((1f - bottomPadding) * 100).toInt().coerceIn(50, 100).toString()
        if (settings["sub-font-size"] != fontSize) property("sub-font-size", fontSize)
        if (settings["sub-pos"] != position) property("sub-pos", position)
    }
    fun setDeband(enabled: Boolean) { property("deband", if (enabled) "yes" else "no") }
    override fun markLoop() {
        val s = state.value
        when {
            s.loopEndMs != null -> clearLoop()
            s.loopStartMs == null -> { property("ab-loop-a", seconds(s.positionMs)); emit(s.copy(loopStartMs = s.positionMs)) }
            s.positionMs >= s.loopStartMs + 500 -> { property("ab-loop-b", seconds(s.positionMs)); emit(s.copy(loopEndMs = s.positionMs)) }
        }
    }
    override fun clearLoop() { property("ab-loop-a", "no"); property("ab-loop-b", "no"); emit(state.value.copy(loopStartMs = null, loopEndMs = null)) }
    internal fun refresh() {
        if (closed || !polling.compareAndSet(false, true)) return
        val expected = revision
        val surfaceId = textureRevision
        val future = work { if (handle != 0L) {
            val data = JSONObject(MpvNative.call(handle, arrayOf("snapshot")))
            main.post { if (!closed && expected == revision && surfaceId == textureRevision) project(data) }
        } }
        future.addListener({ polling.set(false) }, java.util.concurrent.Executor { it.run() })
    }
    private fun project(data: JSONObject) {
        val s = state.value
        if (s.current == null || stopped) return
        val error = data.optString("error").takeIf { it.isNotBlank() }
        val loaded = data.optBoolean("loaded")
        val ended = data.optBoolean("eof-reached")
        val params = data.optJSONObject("video-params")
        val tracks = data.optJSONArray("track-list")
        val chapters = data.optJSONArray("chapter-list")
        val paused = data.optBoolean("pause", true)
        val hw = data.optString("hwdec-current").takeUnless { it == "null" || it == "no" || it.isBlank() }
        emit(s.copy(phase = when { error != null -> PlaybackPhase.ERROR; ended -> PlaybackPhase.ENDED; loaded -> PlaybackPhase.READY; else -> PlaybackPhase.BUFFERING },
            isPlaying = loaded && !paused && !ended && error == null, positionMs = (data.optDouble("time-pos", 0.0) * 1000).toLong().coerceAtLeast(0),
            durationMs = (data.optDouble("duration", 0.0) * 1000).toLong().coerceAtLeast(0), speed = data.optDouble("speed", 1.0).toFloat(),
            videoWidth = params?.optInt("dw") ?: 0, videoHeight = params?.optInt("dh") ?: 0,
            videoDecoderName = listOfNotNull(data.optString("video-codec").takeUnless { it == "null" }, hw).joinToString(" · "),
            // hwdec-current is factual path evidence, not a MediaCodec hardware-acceleration certificate.
            videoDecoderHardwareFlag = null, videoDecoderSoftwareFlag = if (hw == null && loaded) true else null,
            audioDecoderName = data.optString("audio-codec-name"), error = error,
            audioSampleRate = data.optJSONObject("audio-params")?.optInt("samplerate")?.takeIf { it > 0 },
            audioChannels = data.optJSONObject("audio-params")?.optInt("channel-count")?.takeIf { it > 0 },
            subtitleFont = data.optString("sub-font", "sans-serif"), overrideAssStyle = data.optString("sub-ass-override") == "force",
            deband = data.optBoolean("deband"),
            tracks = (0 until (tracks?.length() ?: 0)).mapNotNull { i -> tracks!!.getJSONObject(i).let { t ->
                val type = when (t.optString("type")) { "audio" -> C.TRACK_TYPE_AUDIO; "sub" -> C.TRACK_TYPE_TEXT; else -> return@mapNotNull null }
                PlaybackTrack(t.getInt("id"), 0, type, listOf(t.optString("title"), t.optString("lang"), t.optString("codec")).filter(String::isNotBlank).joinToString(" · ")
                    .ifBlank { "轨道 ${t.getInt("id")}" }, t.optBoolean("selected"), true)
            } },
            chapters = (0 until (chapters?.length() ?: 0)).map { i -> chapters!!.getJSONObject(i).let { VideoChapter(it.optString("title").ifBlank { "章节 ${i + 1}" }, (it.optDouble("time") * 1000).toLong()) } },
            chapterIndex = data.optInt("chapter", -1), subtitleDelayMs = (data.optDouble("sub-delay", 0.0) * 1000).toLong(),
            audioDelayMs = (data.optDouble("audio-delay", 0.0) * 1000).toLong()))
        if (ended && !endHandled && wantsPlay) {
            endHandled = true; cancelBoost()
            when (s.playOrder) {
                PlayOrder.SINGLE -> { seekTo(0); resume() }
                PlayOrder.RANDOM -> jumpTo(s.queue.indices.random())
                PlayOrder.SEQUENTIAL -> if (s.queueIndex < s.queue.lastIndex) next() else wantsPlay = false
            }
        }
    }
    fun closeAsync(done: () -> Unit = {}) {
        if (closed) { if (nativeStopped.get()) main.post(done) else main.postDelayed({ closeAsync(done) }, 20); return }
        closed = true; revision++; textureRevision++; boost = null; wantsPlay = false
        texture = null
        context.unregisterReceiver(noisy); audio.abandonAudioFocusRequest(focus)
        // A dedicated terminal task has a reserved path even if the ordinary queue was full.
        val terminal = Runnable {
            destroyNative(); surfaces.keys.toList().forEach { it.release() }; surfaces.clear(); output = null
            nativeStopped.set(true)
            retiredTextures.toList().forEach { if (retiredTextures.remove(it)) it.release() }
            val directory = java.io.File(context.cacheDir, "video-subtitles").canonicalFile
            copies.forEach { if (it.canonicalFile.parentFile == directory) it.delete() }
            main.post(done)
        }
        try { io.execute(terminal) }
        catch (_: java.util.concurrent.RejectedExecutionException) {
            Thread({ io.shutdown(); while (!io.awaitTermination(1, TimeUnit.SECONDS)) { /* keep leases until IO has ended */ }; terminal.run() }, "YY-mpv-close").start()
        }
        io.shutdown()
    }
    override fun close() = closeAsync()
    private fun bindTexture(next: TextureView?) {
        if (texture === next) return
        texture = next
        if (next == null) { rebuild(null); return }
        next.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) { if (texture === next) rebuild(Surface(surface)) }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                if (nativeStopped.get()) return true
                retiredTextures.add(surface)
                if (nativeStopped.get() && retiredTextures.remove(surface)) surface.release()
                if (texture === next && !closed) rebuild(null)
                return false
            }
        }
        if (next.isAvailable) rebuild(Surface(next.surfaceTexture!!))
    }
    inner class SessionPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        fun changed() = invalidateState()
        override fun getState(): State {
            val s = this@MpvVideoBackend.state.value
            return State.Builder().setAvailableCommands(Player.Commands.Builder().addAll(
                COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_RELEASE, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_METADATA, COMMAND_SET_VIDEO_SURFACE,
                COMMAND_SET_SPEED_AND_PITCH, COMMAND_SET_VOLUME, COMMAND_GET_VOLUME, COMMAND_SET_REPEAT_MODE, COMMAND_SET_SHUFFLE_MODE).build())
                .setPlaylist(s.queue.mapIndexed { i, item -> MediaItemData.Builder("${s.mediaGeneration}:$i")
                    .setMediaItem(MediaItem.Builder().setUri(item.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build()).build())
                    .setIsSeekable(true).setDurationUs(if (i == s.queueIndex && s.durationMs > 0) s.durationMs * 1000 else C.TIME_UNSET).build() })
                .setCurrentMediaItemIndex(if (s.queueIndex >= 0) s.queueIndex else C.INDEX_UNSET)
                .setPlayWhenReady(wantsPlay, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(when (s.phase) { PlaybackPhase.READY -> STATE_READY; PlaybackPhase.BUFFERING -> STATE_BUFFERING; PlaybackPhase.ENDED -> STATE_ENDED; else -> STATE_IDLE })
                .setContentPositionMs(s.positionMs).setPlaybackParameters(PlaybackParameters(s.speed)).setVolume(this@MpvVideoBackend.volume)
                .setVideoSize(VideoSize(s.videoWidth, s.videoHeight))
                .setRepeatMode(if (s.playOrder == PlayOrder.SINGLE) REPEAT_MODE_ONE else REPEAT_MODE_OFF)
                .setShuffleModeEnabled(s.playOrder == PlayOrder.RANDOM).build()
        }
        override fun handleSetPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) resume() else this@MpvVideoBackend.pause()
        override fun handlePrepare(): ListenableFuture<*> { if (stopped) { stopped = false; rebuild() }; return work { } }
        override fun handleStop() = stopPlayback()
        override fun handleRelease(): ListenableFuture<*> { val f = SettableFuture.create<Void>(); closeAsync { f.set(null) }; return f }
        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            if (mediaItemIndex != this@MpvVideoBackend.state.value.queueIndex) jumpTo(mediaItemIndex)
            this@MpvVideoBackend.seekTo(if (positionMs == C.TIME_UNSET) 0 else positionMs); return work { }
        }
        override fun handleSetPlaybackParameters(parameters: PlaybackParameters): ListenableFuture<*> { setSpeed(parameters.speed); return work { } }
        override fun handleSetVolume(volume: Float): ListenableFuture<*> { this@MpvVideoBackend.volume = volume; return property("volume", (volume * 100).toString()) }
        override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> { setPlayOrder(if (repeatMode == REPEAT_MODE_ONE) PlayOrder.SINGLE else PlayOrder.SEQUENTIAL); return work { } }
        override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> { setPlayOrder(if (shuffleModeEnabled) PlayOrder.RANDOM else PlayOrder.SEQUENTIAL); return work { } }
        override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> { bindTexture(videoOutput as? TextureView); return work { } }
        override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> { if (videoOutput == null || videoOutput === texture) bindTexture(null); return work { } }
    }
}
