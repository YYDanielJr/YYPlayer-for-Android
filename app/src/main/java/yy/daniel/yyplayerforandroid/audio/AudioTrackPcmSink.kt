package yy.daniel.yyplayerforandroid.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import java.nio.ByteBuffer

/**
 * The system AudioTrack sink: it opens the platform stream with exactly the decided format and pulls
 * frames out of the portable core's ring on its own thread.
 *
 * What this class is allowed to claim:
 *  - the format it opened with is what `AudioTrack` itself reports, not what was asked for;
 *  - the route is what `getRoutedDevices()` reports while the track is playing;
 *  - sharing stays `UNKNOWN`: the platform exposes no stream-sharing evidence for AudioTrack, so a
 *    bit-perfect mixer contract is never presented as an exclusive stream.
 *
 * What it deliberately does not do: it never substitutes a format the caller did not ask for, never
 * runs a software mixer, and never converts samples. Track volume preserves the user's gain.
 * A conversion that keeps every
 * significant bit is the core's job and happens before frames enter the ring, using
 * `NativeAudioCore.convertPcm`.
 *
 * The [audioAttributes] here must match those used for `setPreferredMixerAttributes`: the
 * platform attaches a track to a preferred mixer by matching those attributes and the format, so a
 * different usage/content contract may produce a normal shared stream.
 */
internal class AudioTrackPcmSink(
    private val manager: AudioManager,
    private val audioAttributes: AudioAttributes,
) : PcmSink {

    override val kind: SinkKind get() = SinkKind.AUDIO_TRACK_SYSTEM

    private var track: AudioTrack? = null
    private var format: OutputFormat? = null
    private var ringHandle = 0L
    private var frameBytes = 0

    /** PCM storage reused by the ordinary worker; this is not a realtime callback. */
    private var source: ByteBuffer? = null

    private var bufferFrames = 0

    private var pumpThread: HandlerThread? = null
    private var pumpHandler: Handler? = null

    @Volatile private var stopRequested = false
    @Volatile private var paused = false

    /** Frames taken out of the ring and handed to the platform. */
    @Volatile private var consumedFrames = 0L

    /** Frames the ring could not supply, counted in the sink's own view of the shortfall. */
    @Volatile private var starvingFrames = 0L

    /** Frames handed to the platform but not yet played; negative never happens. */
    @Volatile private var submittedFrames = 0L
    private var lastPlaybackHead = 0L
    private var playbackHeadWraps = 0L

    @Volatile private var partialWriteFrames = 0L

    /** Set only when the platform accepted a device pin; the route itself always comes from readback. */
    @Volatile private var pinAccepted = false

    /**
     * What the platform says the track actually holds, which may differ from what was requested. Read
     * from `AudioTrack.getFormat()` at open time, and the only value a caller may present as the output
     * format.
     */
    @Volatile private var reportedFormat: OutputFormat? = null

    /** The format the platform accepted, or null while the sink is not open. */
    fun platformFormat(): OutputFormat? = reportedFormat
    fun bufferCapacityFrames(): Int = bufferFrames

    override val openFormat: OutputFormat? get() = format

    override fun framesConsumed(): Long = consumedFrames

    override fun starvingFrames(): Long = starvingFrames

    /** Frames the platform refused in the middle of a write block. */
    fun partialWriteFrames(): Long = partialWriteFrames

    /** True when a device pin was requested and the platform accepted the request. */
    fun devicePinAccepted(): Boolean = pinAccepted
    fun setVolume(volume: Float) { track?.setVolume(volume.coerceIn(0f, 1f)) }

    @Synchronized override fun open(format: OutputFormat, requestedDeviceId: Int?): Result<Unit> {
        close()
        val encoding = AudioDeviceProbe.audioFormatEncodingOf(format.encoding)
        val channelMask = AudioDeviceProbe.audioChannelMask(format.channelCount)
        if (encoding == AudioFormat.ENCODING_INVALID || channelMask == 0 || format.sampleRate !in 8000..768000 ||
            format.channelMask != channelMask) {
            // `AudioFormat` has no 24-significant-bits-in-4-bytes encoding: ENCODING_PCM_24BIT_PACKED is
            // the 3-byte form and ENCODING_PCM_32BIT is 32 significant bits, so the core's `s24_in32` has
            // no platform container at all. It is refused here with the reason rather than silently
            // opened as something else; carrying such a source would need an explicit widening to s32
            // (24 low significant bits → 32 significant bits) decided by the caller.
            return Result.failure(
                SinkRefusal(SinkFailure.BAD_FORMAT, "${format.encoding.wire} ${format.sampleRate}Hz ${format.channelCount}ch"),
            )
        }
        val minBytes = runCatching {
            AudioTrack.getMinBufferSize(format.sampleRate, channelMask, encoding)
        }.getOrDefault(AudioTrack.ERROR_BAD_VALUE)
        if (minBytes <= 0) {
            return Result.failure(SinkRefusal(SinkFailure.BAD_FORMAT, "platform has no buffer for this format"))
        }
        val bytesPerFrame = format.encoding.bytesPerSample * format.channelCount
        // Hold roughly 100 ms so a scheduling hiccup on the pump thread does not become an underrun,
        // and never less than the platform's own minimum. Buffer size is a latency choice only: it
        // cannot change the samples or the rate.
        val targetBytesLong = maxOf(minBytes.toLong() * 2, format.sampleRate.toLong() / 10 * bytesPerFrame)
        if (targetBytesLong > Int.MAX_VALUE) return Result.failure(SinkRefusal(SinkFailure.BAD_FORMAT, "buffer overflow"))
        val targetBytes = targetBytesLong.toInt()
        val audioFormat = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(format.sampleRate)
            .setChannelMask(channelMask)
            .build()
        val created = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(targetBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                // Stable playback does not request a low-latency path. Performance mode supplies no
                // evidence about HAL processing, resampling or stream exclusivity.
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
                .build()
        }.getOrNull() ?: return Result.failure(SinkRefusal(SinkFailure.OPEN_FAILED, "AudioTrack.Builder"))

        if (created.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { created.release() }
            return Result.failure(SinkRefusal(SinkFailure.UNINITIALIZED, "state=${created.state}"))
        }
        // The platform is free to give the track a different format than the one requested. That is
        // recorded as a real result rather than assumed away: a silent substitution would otherwise look
        // exactly like a successful open, and every later claim about the output format would be wrong.
        val reported = runCatching { created.format }.getOrNull()
        reportedFormat = reported?.let {
            OutputFormat(
                sampleRate = runCatching { created.sampleRate }.getOrDefault(0),
                channelCount = Integer.bitCount(it.channelMask),
                encoding = AudioDeviceProbe.encodingOf(it.encoding),
                channelMask = it.channelMask,
            )
        }

        // Pin the route only when the user explicitly chose a device. With no pin the platform keeps
        // its own routing decision, which is what "follow the system output" means.
        if (requestedDeviceId != null) {
            val device = runCatching {
                manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == requestedDeviceId }
            }.getOrNull()
            if (device == null) {
                runCatching { created.release() }
                return Result.failure(SinkRefusal(SinkFailure.DEVICE_GONE, "id=$requestedDeviceId"))
            }
            if (!runCatching { created.setPreferredDevice(device) }.getOrDefault(false)) {
                runCatching { created.release() }
                return Result.failure(SinkRefusal(SinkFailure.DEVICE_GONE, "preferred device refused"))
            }
            pinAccepted = true
        }

        val frames = targetBytes / bytesPerFrame
        val scratch = runCatching { ByteBuffer.allocateDirect(frames * bytesPerFrame).also { it.limit(0) } }
            .getOrElse { created.release(); return Result.failure(SinkRefusal(SinkFailure.OPEN_FAILED, "PCM buffer allocation")) }
        track = created
        this.format = format
        frameBytes = bytesPerFrame
        bufferFrames = frames
        source = scratch
        consumedFrames = 0
        starvingFrames = 0
        submittedFrames = 0
        lastPlaybackHead = 0L
        playbackHeadWraps = 0L
        partialWriteFrames = 0
        return Result.success(Unit)
    }

    /**
     * Writes PCM that a caller already owns, instead of pulling it out of the core ring.
     *
     * This is the mode the Media3 renderer uses: the renderer owns the decode thread and hands over
     * decoded frames as they become available, so there is nothing to pump. The two modes share the same
     * write path, so the interleave accounting and the partial-write counter mean the same thing in both.
     *
     * Returns the frames actually accepted, which may be fewer than [frames]; the caller must advance its
     * source by that amount and offer the rest again.
     */
    fun writeFromCaller(src: ByteBuffer, frames: Int): Int {
        val current = track ?: return 0
        val bytesPerFrame = frameBytes
        if (bytesPerFrame <= 0 || frames <= 0) return 0
        // Media3 must be able to pause/flush while preparing or while the track is full. A blocking
        // write on its playback looper deadlocks release and preparation when AudioTrack is paused.
        val written = current.write(src, frames * bytesPerFrame, AudioTrack.WRITE_NON_BLOCKING)
        check(written >= 0 && written % bytesPerFrame == 0) { "AudioTrack write failed: $written" }
        val accepted = written / bytesPerFrame
        consumedFrames += accepted
        submittedFrames += accepted
        return accepted
    }

    /**
     * Starts the output without the internal ring pump. Used when an external producer owns the frames;
     * the pump would otherwise compete for the same track with silence.
     */
    fun startForExternalProducer(): Result<Unit> {
        val current = track ?: return Result.failure(SinkRefusal(SinkFailure.OPEN_FAILED, "not open"))
        stopRequested = false
        paused = false
        playingDesired = true
        runCatching { current.play() }
        if (current.playState != AudioTrack.PLAYSTATE_PLAYING) {
            playingDesired = false
            return Result.failure(SinkRefusal(SinkFailure.OPEN_FAILED, "playState=${current.playState}"))
        }
        return Result.success(Unit)
    }

    /** A final short tail must not wait for the normal startup fill threshold. */
    fun beginDrain() {
        if (Build.VERSION.SDK_INT >= 31) track?.let { Api31Drain.startWithAnyFrame(it) }
    }

    @androidx.annotation.RequiresApi(31)
    private object Api31Drain {
        fun startWithAnyFrame(track: AudioTrack) { track.setStartThresholdInFrames(1) }
    }

    /**
     * Frames the platform has actually played, from its own playback head. This is the only clock the
     * caller may use for position: frames handed over are not frames heard.
     */
    @Synchronized fun framesPlayedTotal(): Long {
        val current = track ?: return 0L
        val head = runCatching { current.playbackHeadPosition }.getOrDefault(0)
        // The head is an unsigned 32-bit counter that wraps; treat the wrap as a continuation.
        val unsignedHead = head.toLong() and 0xFFFFFFFFL
        if (unsignedHead < lastPlaybackHead) {
            if (lastPlaybackHead > 0xF0000000L && unsignedHead < 0x10000000L) playbackHeadWraps++
            else return lastPlaybackHead + (playbackHeadWraps shl 32)
        }
        lastPlaybackHead = unsignedHead
        return unsignedHead + (playbackHeadWraps shl 32)
    }

    /** Frames the platform can still accept right now, used to report the track's buffer size. */
    fun bufferedFrames(): Int {
        val current = track ?: return 0
        val outstanding = submittedFrames - framesPlayedTotal()
        return outstanding.coerceIn(0L, bufferFrames.toLong()).toInt()
    }

    /**
     * Counters a caller can log without reaching into the platform objects: what was handed over, what
     * the platform reports as played, and what it refused. Used by the diagnostics path and the tests.
     */
    fun counters(): String =
        "submitted=$submittedFrames played=${framesPlayedTotal()} buffered=${bufferedFrames()} " +
            "starving=$starvingFrames partial=$partialWriteFrames bufferFrames=$bufferFrames"

    /**
     * Drops audio the platform is still holding. Used on seek: what is buffered belongs to the old
     * position, and letting it play would emit sound from before the jump.
     */
    fun flushPending() {
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        submittedFrames = 0L
        lastPlaybackHead = 0L
        playbackHeadWraps = 0L
        runCatching { if (playingDesired) track?.play() }
    }

    /** Tracks whether the caller wants the output running, so a flush can restore it. */
    private var playingDesired = false

    @Synchronized override fun start(ringHandle: Long): Result<Unit> {
        if (pumpThread != null && !paused && !stopRequested) return Result.success(Unit)
        val current = track ?: return Result.failure(SinkRefusal(SinkFailure.OPEN_FAILED, "not open"))
        if (ringHandle <= 0L || NativeAudioCore.ringStats(ringHandle) == null) {
            return Result.failure(SinkRefusal(SinkFailure.NO_RING, "handle=$ringHandle"))
        }
        this.ringHandle = ringHandle
        stopRequested = false
        paused = false
        playingDesired = true
        val thread = pumpThread ?: HandlerThread("yy-pcm-pump", Process.THREAD_PRIORITY_AUDIO).also {
            it.start()
            pumpThread = it
        }
        runCatching { current.play() }
        if (current.playState != AudioTrack.PLAYSTATE_PLAYING) {
            playingDesired = false
            return Result.failure(SinkRefusal(SinkFailure.OPEN_FAILED, "playState=${current.playState}"))
        }
        pumpHandler = Handler(thread.looper).also { it.post(pump) }
        return Result.success(Unit)
    }

    override fun pause() {
        paused = true
        playingDesired = false
        runCatching { track?.pause() }
    }

    @Synchronized override fun stop() {
        stopRequested = true
        playingDesired = false
        val thread = pumpThread
        pumpThread = null
        pumpHandler?.removeCallbacksAndMessages(null)
        pumpHandler = null
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        thread?.let { Handler(it.looper).removeCallbacksAndMessages(null); it.quitSafely() }
    }

    @Synchronized override fun close() {
        stop()
        runCatching { track?.release() }
        track = null
        format = null
        ringHandle = 0
        source = null
        bufferFrames = 0
        pinAccepted = false
        reportedFormat = null
    }

    /**
     * Reads the actual output state. Only meaningful while the track is playing, which is why the
     * caller asks at that moment instead of reading a value cached at open time.
     */
    fun routeReport(): SinkRouteReport {
        val current = track ?: return SinkRouteReport()
        val routed = routedDevices(current)
        val actualFormat = runCatching { current.format }.getOrNull()
        return SinkRouteReport(
            routedDeviceIds = routed.map { it.id },
            routedDeviceNames = routed.map { it.productName?.toString() ?: AudioDeviceProbe.typeLabel(it.type) },
            routedIsUsb = routed.any { AudioDeviceProbe.isUsbOutput(it) },
            platformFormat = reportedFormat,
            trackSampleRate = runCatching { current.sampleRate }.getOrDefault(0),
            trackEncoding = actualFormat?.let { AudioDeviceProbe.encodingOf(it.encoding) } ?: SampleEncoding.UNKNOWN,
            trackChannelMask = runCatching { actualFormat?.channelMask ?: 0 }.getOrDefault(0),
            bufferSizeFrames = runCatching { current.bufferSizeInFrames }.getOrDefault(0),
            underrunCount = runCatching { current.underrunCount }.getOrNull(),
        )
    }

    private fun routedDevices(current: AudioTrack): List<AudioDeviceInfo> {
        if (Build.VERSION.SDK_INT >= 36) {
            val devices = runCatching { current.routedDevices }.getOrNull()
            if (!devices.isNullOrEmpty()) return devices.toList()
        }
        return runCatching { listOfNotNull(current.routedDevice) }.getOrDefault(emptyList())
    }

    /** Frames the platform can still accept: buffer capacity minus what is written but not played. */
    private fun roomFrames(current: AudioTrack): Int {
        val outstanding = submittedFrames - framesPlayedTotal()
        return (bufferFrames - outstanding).coerceIn(0L, bufferFrames.toLong()).toInt()
    }

    /** One turn of the pump: move whatever the ring has into whatever the platform can take. */
    private val pump = object : Runnable {
        override fun run() {
            synchronized(this@AudioTrackPcmSink) {
                if (Thread.currentThread() === pumpThread) runOnce()
            }
        }
        private fun runOnce() {
            if (stopRequested || paused) return
            val current = track
            val dst = source
            val bytesPerFrame = frameBytes
            try {
                if (current == null || dst == null || bytesPerFrame <= 0) return
                val room = roomFrames(current)
                if (room <= 0) {
                    // The platform buffer is full: nothing can be accepted, so the ring is allowed to
                    // fill and drop. Retry shortly instead of blocking, so a pause request never has to
                    // wait for the buffer to drain.
                    return
                }
                if (!dst.hasRemaining()) {
                    dst.clear()
                    dst.limit(room * bytesPerFrame)
                    val frames = NativeAudioCore.ringRead(ringHandle, dst, room)
                    dst.position(0)
                    dst.limit(frames * bytesPerFrame)
                }
                // A short write leaves the pending tail in dst. Never discard it or insert silence.
                if (dst.hasRemaining()) {
                    val written = current.write(dst, minOf(dst.remaining(), room * bytesPerFrame),
                        AudioTrack.WRITE_NON_BLOCKING)
                    check(written >= 0 && written % bytesPerFrame == 0) { "pump write failed: $written" }
                    consumedFrames += written / bytesPerFrame
                    submittedFrames += written / bytesPerFrame
                }
            } catch (error: Throwable) {
                stopRequested = true
                partialWriteFrames += (dst?.remaining() ?: 0) / maxOf(1, bytesPerFrame)
                android.util.Log.w(YY_AUDIO_TAG, "PCM_PUMP_ERROR ${error::class.java.name}: ${error.message}")
            } finally {
                if (!stopRequested && !paused) pumpHandler?.postDelayed(this, 2)
            }
        }
    }

    override fun toString(): String =
        "AudioTrackPcmSink(format=$format, frames=$consumedFrames, starving=$starvingFrames)"
}

/** A sink refusal that carries a stable code the UI and logs can show without parsing text. */
internal class SinkRefusal(val failure: SinkFailure, val detail: String) : Exception("${failure.code}: $detail")
