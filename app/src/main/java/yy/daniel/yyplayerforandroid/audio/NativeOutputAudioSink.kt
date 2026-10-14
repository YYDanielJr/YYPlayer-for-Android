package yy.daniel.yyplayerforandroid.audio

import android.content.Context
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The Media3 audio sink that is not an `AudioTrack` owned by Media3: decoded PCM leaves the renderer
 * here and goes into the app's own platform sink, which is the one that can request a bit-perfect
 * mixer contract and reports what the platform actually did.
 *
 * Why an `AudioSink` adapter rather than a decoder: the project's portable core owns sample handling
 * and the Android layer owns device/output capability, so the only thing that has to come from Media3
 * is decoding. Replacing the sink keeps that split and avoids writing an FFmpeg decode path before
 * S06 needs one.
 *
 * Widen-only conversion: a decoded 16-bit stream feeding a device that only offers wider containers is
 * widened by the portable core, which is exact (no rounding, no float, no dither). If the source has
 * more significant bits than the chosen container the sink refuses instead of truncating.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class NativeOutputAudioSink(
    context: Context,
    /** The device the user pinned, or null to follow the system route. */
    private val selectedDeviceId: () -> Int?,
    /** Reports the negotiated output state so the UI can show it as evidence. */
    private val onNegotiated: (Negotiated?) -> Unit = {},
    private val sharingRequest: () -> SharingRequest = { SharingRequest.SHARED },
    private val transparencyRequest: () -> TransparencyRequest = { TransparencyRequest.PREFER_UNPROCESSED },
) : AudioSink {

    /** What was actually opened, for the settings screen and logs. */
    data class Negotiated(
        val format: OutputFormat,
        val widened: Boolean,
        val bitPerfectRequested: Boolean,
        val devicePinAccepted: Boolean,
        val reasonCode: String,
        val route: SinkRouteReport = SinkRouteReport(),
        val bitPerfectActive: Boolean = false,
        val decoderFormat: OutputFormat? = null,
    )

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * The same attributes instance is used for the platform track and for the mixer request: the
     * platform attaches a stream to a preferred mixer by matching attributes and format together.
     */
    private val platformAttributes: PlatformAudioAttributes = PlatformAudioAttributes.Builder()
        .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
        .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var sink: AudioTrackPcmSink? = null
    private var listener: AudioSink.Listener? = null
    private var inputFormat: Format? = null
    private var pendingFormat: Format? = null
    private var openFormat: OutputFormat? = null
    private var sourceEncoding: SampleEncoding = SampleEncoding.UNKNOWN
    private var route: AudioOutputChannel? = null
    private var requestingDeviceId: Int? = null
    private var mixerRequested = false
    private var mixerApplied = false
    private var mixerPreferenceSet = false
    private var lastReport: Negotiated? = null
    private var widenedForOutput = false
    private var routeEstablished = false
    private var routeDeadlineMs = 0L
    private var routeMatchedSinceMs = 0L

    private var playing = false
    private var ended = false
    private var skipSilence = false
    private var volume = 1f
    private var playbackParameters = PlaybackParameters.DEFAULT
    private var audioAttributes: AudioAttributes = AudioAttributes.DEFAULT

    /** Frames handed to the platform but not yet played, so Media3 knows whether data is pending. */
    private var submittedFrames = 0L
    private var startMediaTimeUs = AudioSink.CURRENT_POSITION_NOT_SET
    private var positionAdvancingReported = false
    private var needsDiscontinuity = false

    /** Frames accepted since the sink was configured, across seeks; the decode total for diagnostics. */
    private var fedFramesTotal = 0L

    /** Scratch for widening and for making a heap source readable by the JNI bridge. */
    private var transfer: ByteBuffer? = null
    private var widened: ByteBuffer? = null

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatSupport(format: Format): Int {
        // Compressed formats can also carry pcmEncoding metadata. Accepting them here bypasses
        // MediaCodec and sends encoded FLAC bytes to AudioTrack as if they were PCM.
        if (format.sampleMimeType != MimeTypes.AUDIO_RAW) return AudioSink.SINK_FORMAT_UNSUPPORTED
        val encoding = encodingOf(format)
        if (encoding == SampleEncoding.UNKNOWN || encoding == SampleEncoding.F32) return AudioSink.SINK_FORMAT_UNSUPPORTED
        if (format.channelCount !in 1..8) return AudioSink.SINK_FORMAT_UNSUPPORTED
        if (AudioDeviceProbe.audioChannelMask(format.channelCount) == 0) return AudioSink.SINK_FORMAT_UNSUPPORTED
        if (format.sampleRate !in 8000..768000) return AudioSink.SINK_FORMAT_UNSUPPORTED
        val target = OutputFormatPlanner.plan(
            sampleRate = format.sampleRate,
            sourceEncoding = encoding,
            sourceChannels = format.channelCount,
            route = currentRoute(),
        )
        // Report what the device would really do: an exact container is direct, a wider one has to be
        // widened by the core, and anything the core cannot widen losslessly is unsupported here even
        // though Media3's own sink might have accepted it by resampling.
        if (target.requiresWidening && !NativeAudioCore.isPristineConversion(encoding, target.format.encoding)) {
            return AudioSink.SINK_FORMAT_UNSUPPORTED
        }
        return if (target.requiresWidening) {
            AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
        } else {
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        }
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val current = sink ?: return AudioSink.CURRENT_POSITION_NOT_SET
        val rate = openFormat?.sampleRate ?: return AudioSink.CURRENT_POSITION_NOT_SET
        if (rate <= 0 || startMediaTimeUs == AudioSink.CURRENT_POSITION_NOT_SET) {
            return AudioSink.CURRENT_POSITION_NOT_SET
        }
        val played = current.framesPlayedTotal().coerceIn(0L, submittedFrames)
        if (playing && played > 0 && !positionAdvancingReported) {
            positionAdvancingReported = true
            listener?.onPositionAdvancing(System.currentTimeMillis())
        }
        // Media3 supplies renderer-offset timestamps, including after a seek or queue transition.
        // Report the first input timestamp plus the audible frames, not the write-ahead position.
        return startMediaTimeUs + played * C.MICROS_PER_SECOND / rate
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun configure(format: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        if (!supportsFormat(format)) throw AudioSink.ConfigurationException("不支持的 PCM 格式", format)
        if (outputChannels != null && !outputChannels.contentEquals(IntArray(format.channelCount) { it }))
            throw AudioSink.ConfigurationException("channel_remapping_not_supported", format)
        if (format.encoderDelay != 0 || format.encoderPadding != 0)
            throw AudioSink.ConfigurationException("gapless_trimming_requires_system_output", format)
        if (sink != null && hasPendingData()) {
            pendingFormat = format
            sink?.beginDrain()
            return
        }
        val wantsPlay = playing
        reset()
        playing = wantsPlay
        inputFormat = format
        val encoding = encodingOf(format)
        sourceEncoding = encoding
        ended = false
        submittedFrames = 0L
        fedFramesTotal = 0L
        startMediaTimeUs = AudioSink.CURRENT_POSITION_NOT_SET
        positionAdvancingReported = false
        route = currentRoute()
        if (selectedDeviceId() != null && route == null)
            throw AudioSink.ConfigurationException("selected_device_disconnected", format)
        val planned = OutputFormatPlanner.plan(
            sampleRate = format.sampleRate,
            sourceEncoding = encoding,
            sourceChannels = format.channelCount,
            route = route,
        )
        val refusal = when {
            sharingRequest() == SharingRequest.STRICT_EXCLUSIVE -> "exclusive_not_proven_by_audiotrack"
            transparencyRequest() == TransparencyRequest.STRICT_UNPROCESSED &&
                (!planned.bitPerfectContractAvailable || route?.isUsb != true) -> "bitperfect_contract_unavailable"
            transparencyRequest() == TransparencyRequest.STRICT_UNPROCESSED && volume != 1f -> "software_volume_active"
            transparencyRequest() == TransparencyRequest.STRICT_UNPROCESSED && !systemVolumeIsUnity() -> "system_volume_not_unity"
            transparencyRequest() == TransparencyRequest.STRICT_UNPROCESSED && planned.requiresWidening ->
                "strict_unprocessed_would_convert"
            else -> null
        }
        if (refusal != null) throw AudioSink.ConfigurationException(refusal, format)
        openFormat = planned.format
        widenedForOutput = planned.requiresWidening
        requestingDeviceId = selectedDeviceId()
        routeEstablished = false
        routeMatchedSinceMs = 0L
        routeDeadlineMs = android.os.SystemClock.elapsedRealtime() + 3_000L
        mixerRequested = volume == 1f && systemVolumeIsUnity() && transparencyRequest() != TransparencyRequest.COMPATIBLE &&
            OutputFormatPlanner.shouldRequestBitPerfect(route, planned)
        // Request before opening AudioTrack; its attributes and format must match the mixer.
        val mixerDevice = (requestingDeviceId ?: route?.id)?.let { deviceInfoFor(it) }
        if (mixerRequested && mixerDevice != null) {
            requestingDeviceId = mixerDevice.id
            val profile = MixerProfile(planned.format.encoding, planned.format.sampleRate,
                planned.format.channelMask, bitPerfect = true, mixesWithOthers = false)
            mixerApplied = AudioDeviceProbe.requestMixerAttributes(manager, mixerDevice, profile, platformAttributes) &&
                AudioDeviceProbe.currentMixerAttributes(manager, mixerDevice, platformAttributes) == profile
            mixerPreferenceSet = mixerApplied
        } else if (route?.isUsb == true && mixerDevice != null) {
            requestingDeviceId = mixerDevice.id
            // USB native containers may require their DEFAULT mixer too. A shared mixer request is
            // independent of BIT_PERFECT, and supplies no transparency or exclusivity evidence.
            val profile = route?.supportedProfiles?.firstOrNull {
                !it.bitPerfect && it.encoding == planned.format.encoding && it.sampleRate == planned.format.sampleRate &&
                    it.channelMask == planned.format.channelMask }
            if (profile != null) mixerPreferenceSet =
                AudioDeviceProbe.requestMixerAttributes(manager, mixerDevice, profile, platformAttributes)
        }
        if (transparencyRequest() == TransparencyRequest.STRICT_UNPROCESSED && !mixerApplied) {
            reset()
            throw AudioSink.ConfigurationException("mixer_contract_refused", format)
        }
        val sink = AudioTrackPcmSink(manager, platformAttributes)
        val opened = sink.open(planned.format, requestingDeviceId)
        if (opened.isFailure) {
            val refusal = opened.exceptionOrNull()
            val code = (refusal as? SinkRefusal)?.failure?.code ?: "sink_open_failed"
            this.sink = null
            onNegotiated(
                Negotiated(
                    format = planned.format,
                    widened = planned.requiresWidening,
                    bitPerfectRequested = false,
                    devicePinAccepted = false,
                    reasonCode = code,
                ),
            )
            reset()
            throw AudioSink.ConfigurationException(
                refusal?.message ?: "无法按 ${planned.format.encoding.wire} 打开输出",
                format,
            )
        }
        if (playing) sink.startForExternalProducer().onFailure { failure ->
            sink.close()
            this.sink = null
            throw AudioSink.ConfigurationException(failure.message ?: "无法启动输出", format)
        }
        this.sink = sink
        sink.setVolume(volume)
        publishEvidence()
        android.util.Log.i(
            YY_AUDIO_TAG,
            "YYPLAYER_MEDIA3_SINK open=${planned.format.encoding.wire}/${planned.format.sampleRate}Hz/" +
                "ch=${planned.format.channelCount} widened=${planned.requiresWidening} " +
                "mixerRequested=$mixerRequested mixerApplied=$mixerApplied source=${encoding.wire}",
        )
    }

    override fun play() {
        playing = true
        sink?.startForExternalProducer()?.getOrThrow()
    }

    override fun handleDiscontinuity() {
        // Media3 also calls this at queue boundaries; flushing would discard the audible tail.
        // Wait for the old tail to drain before rebasing the next buffer's timestamp.
        needsDiscontinuity = true
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        pendingFormat?.let { next ->
            if (hasPendingData()) return false
            pendingFormat = null
            try { configure(next, 0, null) }
            catch (failure: AudioSink.ConfigurationException) {
                throw AudioSink.WriteException(android.media.AudioTrack.ERROR_INVALID_OPERATION, next, false)
            }
        }
        val current = sink ?: return true
        val target = openFormat ?: return true
        val source = sourceEncoding
        val frameBytes = source.bytesPerSample * target.channelCount
        val framesInBuffer = buffer.remaining() / frameBytes
        if (buffer.remaining() % frameBytes != 0) {
            throw AudioSink.WriteException(android.media.AudioTrack.ERROR_BAD_VALUE, inputFormat!!, false)
        }
        if (framesInBuffer <= 0) return true
        if (!checkRoute(current)) return false
        if (needsDiscontinuity) {
            if (hasPendingData()) return false
            startMediaTimeUs = presentationTimeUs - submittedFrames * C.MICROS_PER_SECOND / target.sampleRate
            needsDiscontinuity = false
            listener?.onPositionDiscontinuity()
        }
        if (startMediaTimeUs == AudioSink.CURRENT_POSITION_NOT_SET) startMediaTimeUs = presentationTimeUs

        val payload = if (target.encoding == source) {
            directCopy(buffer)
        } else {
            widen(buffer, source, target, framesInBuffer) ?: run {
                throw AudioSink.WriteException(android.media.AudioTrack.ERROR_BAD_VALUE, inputFormat!!, false)
            }
        }
        val accepted = try {
            current.writeFromCaller(payload, framesInBuffer)
        } catch (failure: IllegalStateException) {
            throw AudioSink.WriteException(android.media.AudioTrack.ERROR_INVALID_OPERATION, inputFormat!!, false)
        }
        submittedFrames += accepted.toLong()
        fedFramesTotal += accepted.toLong()
        checkRoute(current)
        publishEvidence()
        if (accepted < framesInBuffer) {
            // Not all of it fit: consume only what the platform took and let the renderer offer the rest
            // again, rather than dropping audio on the floor.
            buffer.position(buffer.position() + accepted * frameBytes)
            return false
        }
        buffer.position(buffer.limit())
        return true
    }

    override fun playToEndOfStream() {
        ended = true
        sink?.beginDrain()
    }

    override fun isEnded(): Boolean = sink == null || (ended && !hasPendingData())

    override fun hasPendingData(): Boolean {
        val current = sink ?: return false
        val played = runCatching { current.framesPlayedTotal() }.getOrDefault(0L)
        return submittedFrames - played > 0
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        // No time-stretch processor is implemented on this path. Return the supported value honestly.
        this.playbackParameters = PlaybackParameters.DEFAULT
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        skipSilence = false
        listener?.onSkipSilenceEnabledChanged(false)
    }

    override fun getSkipSilenceEnabled(): Boolean = skipSilence

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        this.audioAttributes = audioAttributes
    }

    override fun getAudioAttributes(): AudioAttributes = audioAttributes

    override fun setAudioSessionId(audioSessionId: Int) = Unit

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = Unit

    override fun getAudioTrackBufferSizeUs(): Long {
        val current = sink ?: return C.TIME_UNSET
        val rate = openFormat?.sampleRate ?: return C.TIME_UNSET
        val frames = current.bufferCapacityFrames().toLong()
        return if (rate > 0) frames * C.MICROS_PER_SECOND / rate else C.TIME_UNSET
    }

    override fun enableTunnelingV21() { error("tunneling_not_supported") }

    override fun disableTunneling() = Unit

    override fun setVolume(volume: Float) {
        this.volume = volume
        if (volume != 1f && (mixerApplied || transparencyRequest() == TransparencyRequest.STRICT_UNPROCESSED)) {
            sink?.pause()
            sink?.flushPending()
            check(sink == null) { "software_volume_active" }
        }
        sink?.setVolume(volume)
    }

    override fun pause() {
        playing = false
        positionAdvancingReported = false
        sink?.pause()
        lastReport = null
        onNegotiated(null)
    }

    override fun flush() {
        // A queued decoder format still applies to the next buffer after a seek/flush. Keeping it
        // lets handleBuffer reconfigure after the old tail has been discarded.
        sink?.flushPending()
        submittedFrames = 0L
        startMediaTimeUs = AudioSink.CURRENT_POSITION_NOT_SET
        positionAdvancingReported = false
        needsDiscontinuity = false
        ended = false
    }

    override fun reset() {
        val device = requestingDeviceId?.let { deviceInfoFor(it) }
        sink?.close()
        sink = null
        openFormat = null
        inputFormat = null
        pendingFormat = null
        submittedFrames = 0L
        startMediaTimeUs = AudioSink.CURRENT_POSITION_NOT_SET
        positionAdvancingReported = false
        needsDiscontinuity = false
        ended = false
        playing = false
        transfer = null
        widened = null
        if ((mixerRequested || mixerPreferenceSet) && device != null) {
            AudioDeviceProbe.clearMixerAttributes(manager, device, platformAttributes)
        }
        mixerRequested = false
        mixerApplied = false
        mixerPreferenceSet = false
        requestingDeviceId = null
        lastReport = null
        onNegotiated(null)
    }

    override fun release() = reset()

    /** The format the output was opened with, or null when nothing is open. */
    fun openedFormat(): OutputFormat? = openFormat

    /** True when the platform accepted a bit-perfect contract for the opened format. */
    fun bitPerfectActive(): Boolean = volume == 1f && systemVolumeIsUnity() && mixerApplied && sink?.routeReport()?.let { report ->
        report.routedIsUsb && requestingDeviceId in report.routedDeviceIds &&
            report.platformFormat == openFormat && report.underrunCount == 0 && currentMixerMatches()
    } == true

    /** Counter snapshot of the platform sink, for logs and tests. */
    fun counters(): String = sink?.counters() ?: "not-open"

    /**
     * Frames this sink accepted from the renderer since it was configured. Compare with the file's
     * declared duration to tell "playback stopped early" apart from "the file's metadata is wrong".
     */
    fun framesAccepted(): Long = fedFramesTotal

    /** The sink's own view of what it has played, in microseconds, for diagnostics. */
    fun positionUs(): Long = getCurrentPositionUs(false)

    private fun currentRoute(): AudioOutputChannel? {
        val channels = AudioDeviceProbe.channels(manager)
        val pinned = selectedDeviceId()
        if (pinned != null) return channels.firstOrNull { it.id == pinned }
        val anticipated = AudioDeviceProbe.anticipatedDeviceId(manager, platformAttributes)
        return channels.firstOrNull { it.id == anticipated }
    }

    private fun currentMixerMatches(): Boolean {
        val device = requestingDeviceId?.let(::deviceInfoFor) ?: return false
        val target = openFormat ?: return false
        return AudioDeviceProbe.currentMixerAttributes(manager, device, platformAttributes) ==
            MixerProfile(target.encoding, target.sampleRate, target.channelMask, true, false)
    }

    private fun systemVolumeIsUnity(): Boolean = manager.getStreamVolume(AudioManager.STREAM_MUSIC) ==
        manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private fun checkRoute(current: AudioTrackPcmSink): Boolean {
        val expected = requestingDeviceId
        val report = current.routeReport()
        val strict = transparencyRequest() == TransparencyRequest.STRICT_UNPROCESSED
        val lost = expected != null && deviceInfoFor(expected) == null
        val wrongRoute = expected != null && report.routedDeviceIds.isNotEmpty() &&
            expected !in report.routedDeviceIds
        val unconfirmed = expected != null && expected !in report.routedDeviceIds
        val now = android.os.SystemClock.elapsedRealtime()
        if (!routeEstablished && expected != null) {
            if (unconfirmed) routeMatchedSinceMs = 0L
            else if (routeMatchedSinceMs == 0L) routeMatchedSinceMs = now
            if (!lost && now < routeDeadlineMs &&
                (unconfirmed || now - routeMatchedSinceMs < 300L)) return false
        }
        // Pinning recreates the platform track asynchronously. During startup wait for readback
        // without sending PCM to the old route. Once established, any mismatch fails immediately.
        if (!lost && !routeEstablished && unconfirmed &&
            android.os.SystemClock.elapsedRealtime() < routeDeadlineMs) return false
        if (lost || wrongRoute || unconfirmed || (mixerApplied && !systemVolumeIsUnity()) || (strict && !bitPerfectActive())) {
            android.util.Log.e(YY_AUDIO_TAG, "YYPLAYER_ROUTE_REFUSED requested=$expected " +
                "actual=${report.routedDeviceIds} established=$routeEstablished lost=$lost strict=$strict")
            playing = false
            current.pause()
            current.flushPending()
            onNegotiated(null)
            throw AudioSink.WriteException(android.media.AudioTrack.ERROR_DEAD_OBJECT, inputFormat!!, false)
        }
        if (expected != null) routeEstablished = true
        return true
    }

    private fun publishEvidence() {
        val current = sink ?: return
        val target = openFormat ?: return
        val report = Negotiated(target, widenedForOutput, mixerRequested, current.devicePinAccepted(),
            if (mixerRequested && !mixerApplied) "mixer_contract_refused" else "ok",
            current.routeReport(), bitPerfectActive(),
            inputFormat?.let { OutputFormat(it.sampleRate, it.channelCount, sourceEncoding,
                AudioDeviceProbe.audioChannelMask(it.channelCount)) })
        if (report != lastReport) { lastReport = report; onNegotiated(report) }
    }

    private fun deviceInfoFor(id: Int) = runCatching {
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
    }.getOrNull()

    private fun encodingOf(format: Format): SampleEncoding = when (format.pcmEncoding) {
        C.ENCODING_PCM_16BIT -> SampleEncoding.S16
        C.ENCODING_PCM_24BIT -> SampleEncoding.S24_3
        C.ENCODING_PCM_32BIT -> SampleEncoding.S32
        C.ENCODING_PCM_FLOAT -> SampleEncoding.F32
        // A format the decoder left unspecified is unknown, not assumed: guessing here would let the
        // sink open with a container the samples do not match.
        else -> SampleEncoding.UNKNOWN
    }

    /** Copies the caller's buffer into direct memory, because the bridge reads direct buffers only. */
    private fun directCopy(buffer: ByteBuffer): ByteBuffer {
        val needed = buffer.remaining()
        var scratch = transfer
        if (scratch == null || scratch.capacity() < needed) {
            scratch = ByteBuffer.allocateDirect(maxOf(needed, 64 * 1024)).order(ByteOrder.LITTLE_ENDIAN)
            transfer = scratch
        }
        scratch.clear()
        scratch.put(buffer.duplicate())
        scratch.flip()
        return scratch
    }

    /** Widens the caller's samples with the portable core; never narrows and never rounds. */
    private fun widen(
        buffer: ByteBuffer,
        source: SampleEncoding,
        target: OutputFormat,
        frames: Int,
    ): ByteBuffer? {
        if (!NativeAudioCore.isPristineConversion(source, target.encoding)) return null
        val sourceBytes = frames * source.bytesPerSample * target.channelCount
        val targetBytes = frames * target.encoding.bytesPerSample * target.channelCount
        val src = directCopy(buffer)
        var dst = widened
        if (dst == null || dst.capacity() < targetBytes) {
            dst = ByteBuffer.allocateDirect(maxOf(targetBytes, 64 * 1024)).order(ByteOrder.LITTLE_ENDIAN)
            widened = dst
        }
        dst.clear()
        val converted = NativeAudioCore.convertPcm(
            from = source,
            to = target.encoding,
            src = src,
            srcBytes = sourceBytes,
            dst = dst,
            dstBytes = dst.capacity(),
            channels = target.channelCount,
        )
        if (converted != frames) return null
        dst.position(0)
        dst.limit(targetBytes)
        return dst
    }
}
