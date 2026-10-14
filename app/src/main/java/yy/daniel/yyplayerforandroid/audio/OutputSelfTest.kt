package yy.daniel.yyplayerforandroid.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * One executed output verification: the portable core decided a format and a bit-perfect request, this
 * opened a real sink with exactly that format, asked the platform for the mixer contract, and then
 * read back what the platform actually did while sound was playing.
 *
 * Every field is an observation. [systemReportedBitPerfect] means "the platform accepted the mixer
 * contract for this format and reports the stream on that mixer"; it is not a measurement, and the
 * only thing that can raise this to captured evidence is an external digital capture, whose comparison
 * value is [digestHex].
 */
internal data class OutputSelfTestResult(
    val started: Boolean,
    val sink: SinkKind = SinkKind.UNKNOWN,
    val reasonCode: String = "",
    val reasonText: String = "",
    val decidedFormat: OutputFormat? = null,
    /** The device the caller asked the platform to use, or null when it followed the default route. */
    val requestedDeviceId: Int? = null,
    /**
     * True when the platform accepted the pin request. A refusal does not mean the route is wrong, and
     * an acceptance does not prove it either: `route` is the only real evidence. On the Xiaomi device
     * this was measured `false` while the platform still routed to the requested DAC.
     */
    val devicePinAccepted: Boolean = false,
    val bitPerfectRequested: Boolean = false,
    val mixerContractAccepted: Boolean = false,
    val mixerContractReadBack: MixerProfile? = null,
    val route: SinkRouteReport = SinkRouteReport(),
    val framesFed: Long = 0,
    val framesDropped: Long = 0,
    val framesConsumed: Long = 0,
    val framesPlayed: Long = 0,
    val ringUnderrunFrames: Long = 0,
    val sinkStarvingFrames: Long = 0,
    /**
     * How much starvation was counted up to the moment the whole signal had been handed to the sink.
     * Empty-ring polling never inserts PCM. This separates waiting for the producer from the final
     * counters; delivered frame counts and the playback head determine delivery completeness.
     */
    val starvingFramesAtFeedEnd: Long = 0,
    val partialWriteFrames: Long = 0,
    /** SHA-256 of the exact bytes that entered the ring, in the decided format, little-endian frames. */
    val digestHex: String? = null,
    val toneDescription: String = "",
    val notes: List<String> = emptyList(),
) {
    val formatMatchesTrack: Boolean
        get() {
            val decided = decidedFormat ?: return false
            // This is the configured API format; it cannot reveal HAL resampling or hardware format.
            val accepted = route.platformFormat ?: return false
            return accepted == decided
        }

    /** The platform gave the track a different format than the decision asked for. */
    val formatWasSubstituted: Boolean
        get() {
            val decided = decidedFormat ?: return false
            val accepted = route.platformFormat ?: return false
            return accepted != decided
        }

    /** The platform's reported route is the device that was asked for. */
    val routeMatchesRequest: Boolean
        get() = requestedDeviceId == null || route.routedDeviceIds.contains(requestedDeviceId)

    /** The platform accepted the contract, the stream reports the same format, and it is on USB. */
    val systemReportedBitPerfect: Boolean
        get() = mixerContractAccepted && bitPerfectRequested && formatMatchesTrack && route.routedIsUsb &&
            requestedDeviceId != null && routeMatchesRequest && deliveryComplete && route.underrunCount == 0 &&
            mixerContractReadBack == decidedFormat?.let {
                MixerProfile(it.encoding, it.sampleRate, it.channelMask, true, false) }

    /** Frames the ring could not supply while the reference signal was actually being delivered. */
    val starvingDuringSignal: Long get() = (sinkStarvingFrames - starvingFramesAtFeedEnd).coerceAtLeast(0)

    /** True when nothing was lost between the ring and the platform, so the digest describes the output. */
    val deliveryComplete: Boolean
        get() = started && framesDropped == 0L && starvingDuringSignal == 0L &&
            partialWriteFrames == 0L && framesConsumed >= framesFed && framesPlayed >= framesFed && framesFed > 0
}

/**
 * Drives one verification run. Kept out of `AudioOutputController` so the controller stays a pure
 * decision/evidence publisher: this class is allowed to make noise, and it is only ever invoked by an
 * explicit user action or a device test.
 */
internal class OutputSelfTest(
    context: Context,
    private val audioAttributes: AudioAttributes,
) {
    private val manager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Opens the decided format, requests the mixer contract, plays the reference signal through the
     * portable core's ring, and returns everything the platform reported.
     *
     * A required mixer contract is requested before opening the track. Refusal terminates the test.
     */
    suspend fun run(
        source: SourceFormat,
        decision: OutputDecision,
        selectedDeviceId: Int?,
        volume: Float = 1f,
        onPlaying: (SinkRouteReport) -> Unit = {},
    ): OutputSelfTestResult = withContext(Dispatchers.IO) {
        val notes = mutableListOf<String>()
        val decided = decision.format?.let { it.copy(channelMask = AudioDeviceProbe.audioChannelMask(it.channelCount)) }
        if (!decision.accepted || decided == null) {
            return@withContext OutputSelfTestResult(
                started = false,
                reasonCode = decision.reasonCode,
                reasonText = decision.reasonText,
            )
        }
        if (!NativeAudioCore.available) {
            return@withContext OutputSelfTestResult(
                started = false,
                reasonCode = "native_unavailable",
                reasonText = "原生核心未装载，无法建立 PCM 输出",
            )
        }
        if (decided.channelCount != 2 || decision.requestedSharing == SharingRequest.STRICT_EXCLUSIVE) {
            return@withContext OutputSelfTestResult(false, reasonCode = "fixture_or_sharing_unsupported",
                reasonText = "参考信号仅支持立体声；AudioTrack 不提供独占证据")
        }
        if (decision.requestBitPerfect && volume != 1f) return@withContext OutputSelfTestResult(
            false, reasonCode = "software_volume_active", reasonText = "保留当前软件音量，不能请求位完美契约")
        if (decision.requestBitPerfect && manager.getStreamVolume(AudioManager.STREAM_MUSIC) !=
            manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) return@withContext OutputSelfTestResult(
            false, reasonCode = "system_volume_not_unity", reasonText = "保留系统音量，不能请求可能绕过音量控制的位完美契约")
        val device = selectedDeviceId?.let { id ->
            runCatching {
                manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
            }.getOrNull()
        }
        if (selectedDeviceId != null && device == null) {
            return@withContext OutputSelfTestResult(
                started = false,
                reasonCode = "device_gone",
                reasonText = "所选输出设备已不在设备列表中",
            )
        }

        val tone = AudioVerificationTone(sampleRate = decided.sampleRate)
        val rendered = tone.renderS16()
        // The tone is produced as s16 and widened by the portable core, so the samples reaching the
        // sink are the decided format without this file owning a conversion table.
        val converted = losslessWiden(rendered, SampleEncoding.S16, decided, notes)
            ?: return@withContext OutputSelfTestResult(
                started = false,
                reasonCode = "pcm_conversion_refused",
                reasonText = "核心拒绝把参考信号无损转换为目标格式",
                decidedFormat = decided,
                notes = notes,
            )

        val ringHandle = NativeAudioCore.createRing(
            sampleRate = decided.sampleRate,
            channels = decided.channelCount,
            encoding = decided.encoding,
            channelMask = decided.channelMask,
            capacityFrames = RING_CAPACITY_FRAMES,
        )
        if (ringHandle <= 0L) {
            return@withContext OutputSelfTestResult(
                started = false,
                reasonCode = "ring_unavailable",
                reasonText = "无法创建原生 PCM 环",
                decidedFormat = decided,
                notes = notes,
            )
        }
        val sink = AudioTrackPcmSink(manager, audioAttributes)
        var mixerPreferenceSet = false
        try {
            if (decision.requestBitPerfect) {
                notes += "核心要求在打开输出前先把混音器契约提交给系统（${decided.sampleRate}Hz / ${decided.encoding.wire}）"
            }
            if (selectedDeviceId == null) {
                notes += "未指定设备：由系统决定路由，回读结果可能不是 USB 设备"
            } else {
                notes += "已请求把输出固定到设备 id=$selectedDeviceId（${device?.productName}），平台是否照做以回读为准"
            }
            // The preferred mixer contract must precede AudioTrack construction.
            var mixerAccepted = false
            var mixerReadBack: MixerProfile? = null
            if (decision.requestBitPerfect && device != null) {
                val profile = MixerProfile(decided.encoding, decided.sampleRate, decided.channelMask, true, false)
                mixerAccepted = AudioDeviceProbe.requestMixerAttributes(manager, device, profile, audioAttributes)
                mixerPreferenceSet = mixerAccepted
                mixerReadBack = AudioDeviceProbe.currentMixerAttributes(manager, device, audioAttributes)
                if (!mixerAccepted || mixerReadBack != profile) {
                    // A core plan requiring a contract cannot be executed as an unqualified fallback.
                    return@withContext OutputSelfTestResult(false, reasonCode = "mixer_contract_refused",
                        reasonText = "系统未确认匹配的位完美契约", decidedFormat = decided,
                        requestedDeviceId = selectedDeviceId, bitPerfectRequested = true)
                }
            } else if (device != null && AudioDeviceProbe.isUsbOutput(device)) {
                val profile = AudioDeviceProbe.channels(manager).firstOrNull { it.id == device.id }
                    ?.supportedProfiles?.firstOrNull { !it.bitPerfect && it.encoding == decided.encoding &&
                        it.sampleRate == decided.sampleRate && it.channelMask == decided.channelMask }
                if (profile != null) {
                    mixerPreferenceSet = AudioDeviceProbe.requestMixerAttributes(manager, device, profile, audioAttributes)
                    notes += "匹配的普通共享 mixer 请求：$mixerPreferenceSet；它不提供位完美或独占证据"
                }
            }
            val opened = sink.open(decided, selectedDeviceId)
            val refusal = opened.exceptionOrNull()
            if (refusal != null) {
                return@withContext OutputSelfTestResult(
                    started = false,
                    reasonCode = (refusal as? SinkRefusal)?.failure?.code ?: "sink_open_failed",
                    reasonText = refusal.message ?: "平台拒绝创建输出流",
                    decidedFormat = decided,
                    requestedDeviceId = selectedDeviceId,
                    bitPerfectRequested = decision.requestBitPerfect,
                    notes = notes,
                )
            }
            sink.setVolume(volume)
            val started = sink.start(ringHandle)
            if (started.isFailure) {
                val error = started.exceptionOrNull()
                return@withContext OutputSelfTestResult(
                    started = false,
                    reasonCode = (error as? SinkRefusal)?.failure?.code ?: "sink_start_failed",
                    reasonText = error?.message ?: "无法启动输出",
                    decidedFormat = decided,
                    requestedDeviceId = selectedDeviceId,
                    bitPerfectRequested = decision.requestBitPerfect,
                    notes = notes,
                )
            }

            val coroutine = currentCoroutineContext()
            val routeDeadline = android.os.SystemClock.elapsedRealtime() + 3_000L
            var matchedSince = 0L
            while (selectedDeviceId != null) {
                coroutine.ensureActive()
                val now = android.os.SystemClock.elapsedRealtime()
                if (selectedDeviceId in sink.routeReport().routedDeviceIds) {
                    if (matchedSince == 0L) matchedSince = now
                    if (now - matchedSince >= 300L) break
                } else matchedSince = 0L
                if (now >= routeDeadline) return@withContext OutputSelfTestResult(false,
                    reasonCode = "route_not_confirmed", reasonText = "输出路由未确认，未投递参考信号",
                    decidedFormat = decided, requestedDeviceId = selectedDeviceId, route = sink.routeReport())
                Thread.sleep(10)
            }
            fun checkOutput() {
                coroutine.ensureActive()
                check(selectedDeviceId == null || selectedDeviceId in sink.routeReport().routedDeviceIds) {
                    "selected_output_lost"
                }
                check(!decision.requestBitPerfect || (volume == 1f &&
                    manager.getStreamVolume(AudioManager.STREAM_MUSIC) == manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))) {
                    "bitperfect_volume_changed"
                }
            }
            val feed = PcmRingFeeder.feed(ringHandle, converted, decided, checkActive = ::checkOutput)
            val starvingAtFeedEnd = sink.starvingFrames()
            if (!feed.complete) {
                notes += "参考信号未能全部写入环（写入 ${feed.framesWritten}/${feed.expectedFrames}，丢弃 ${feed.framesDropped}）"
            }

            // Wait until the sink has taken the whole signal. Starvation is sampled at the instant the
            // last frame is handed over, not after a polling delay: the tail of the reference signal is
            // silence, so any sample taken later only measures how long the loop waited.
            val drainDeadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MILLIS
            var consumedAtReport = sink.framesConsumed()
            var starvingAtDrainEnd = sink.starvingFrames()
            while (consumedAtReport < feed.framesWritten && System.currentTimeMillis() < drainDeadline) {
                checkOutput()
                Thread.sleep(10)
                consumedAtReport = sink.framesConsumed()
                starvingAtDrainEnd = sink.starvingFrames()
            }
            while (sink.framesPlayedTotal() < feed.framesWritten && System.currentTimeMillis() < drainDeadline) {
                checkOutput()
                Thread.sleep(10)
            }

            // Read the route only after playback has really started: the platform reports nothing
            // useful about routing before that.
            val route = sink.routeReport()
            onPlaying(route)
            if (route.platformFormat != null && !formatMatches(route.platformFormat, decided)) {
                notes += "平台没有按决策格式打开输出：请求 ${decided.encoding.wire}/${decided.sampleRate}Hz，" +
                    "实际 ${route.platformFormat.encoding.wire}/${route.platformFormat.sampleRate}Hz（已记录，未据此声称原样输出）"
            }
            if (selectedDeviceId != null && !route.routedDeviceIds.contains(selectedDeviceId)) {
                notes += "平台没有把输出路由到请求的设备（请求 id=$selectedDeviceId，实际 ${route.routedDeviceNames}）"
            }
            if (decision.requestBitPerfect && device != null)
                mixerReadBack = AudioDeviceProbe.currentMixerAttributes(manager, device, audioAttributes)
            OutputSelfTestResult(
                started = true,
                sink = sink.kind,
                reasonCode = if (feed.complete && consumedAtReport >= feed.framesWritten &&
                    sink.framesPlayedTotal() >= feed.framesWritten) "ok" else "delivery_incomplete",
                reasonText = "参考信号输出已结束，完整性以帧计数与实际播放头为准",
                decidedFormat = decided,
                requestedDeviceId = selectedDeviceId,
                devicePinAccepted = sink.devicePinAccepted(),
                bitPerfectRequested = decision.requestBitPerfect,
                mixerContractAccepted = mixerAccepted,
                mixerContractReadBack = mixerReadBack,
                route = route,
                framesFed = feed.framesWritten,
                framesDropped = feed.framesDropped,
                framesConsumed = consumedAtReport,
                framesPlayed = sink.framesPlayedTotal(),
                ringUnderrunFrames = NativeAudioCore.ringStats(ringHandle)?.getOrNull(5) ?: -1,
                sinkStarvingFrames = starvingAtDrainEnd,
                starvingFramesAtFeedEnd = starvingAtFeedEnd,
                partialWriteFrames = sink.partialWriteFrames(),
                digestHex = feed.digestHex,
                toneDescription = tone.description(),
                notes = notes,
            )
        } finally {
            sink.close()
            NativeAudioCore.releaseRing(ringHandle)
            if (device != null && (decision.requestBitPerfect || mixerPreferenceSet))
                AudioDeviceProbe.clearMixerAttributes(manager, device, audioAttributes)
        }
    }

    /**
     * Widens the rendered s16 signal to the decided encoding using the portable core, so the rule
     * "never drop a significant bit, never go through float" is enforced by the same code the player
     * will use. Returns null when the core refuses the pair.
     */
    private fun losslessWiden(
        source: ByteBuffer,
        sourceEncoding: SampleEncoding,
        decided: OutputFormat,
        notes: MutableList<String>,
    ): ByteBuffer? {
        if (sourceEncoding == decided.encoding) return source.order(ByteOrder.LITTLE_ENDIAN)
        if (!isPristinePair(sourceEncoding, decided.encoding)) {
            notes += "核心不支持 ${sourceEncoding.wire} → ${decided.encoding.wire} 的无损转换"
            return null
        }
        val frameBytes = decided.encoding.bytesPerSample * decided.channelCount
        val frames = source.remaining() / (sourceEncoding.bytesPerSample * decided.channelCount)
        val target = ByteBuffer.allocateDirect(frames * frameBytes).order(ByteOrder.LITTLE_ENDIAN)
        val converted = NativeAudioCore.convertPcm(
            from = sourceEncoding,
            to = decided.encoding,
            src = source.order(ByteOrder.LITTLE_ENDIAN),
            srcBytes = source.remaining(),
            dst = target,
            dstBytes = target.capacity(),
            channels = decided.channelCount,
        )
        if (converted != frames) {
            notes += "核心只转换了 $converted/$frames 帧（源 ${source.remaining()} 字节，目标 ${target.capacity()} 字节）"
            return null
        }
        target.position(0)
        target.limit(converted * frameBytes)
        notes += "参考信号按 ${sourceEncoding.wire} → ${decided.encoding.wire} 无损加宽（未丢弃有效位）"
        return target
    }

    private fun isPristinePair(from: SampleEncoding, to: SampleEncoding): Boolean =
        NativeAudioCore.isPristineConversion(from, to)

    private fun formatMatches(accepted: OutputFormat, decided: OutputFormat): Boolean =
        accepted == decided

    private companion object {
        /** About one second at 48 kHz: enough to absorb jitter, small enough to stay responsive. */
        const val RING_CAPACITY_FRAMES = 48000L

        /** The reference signal is a few seconds long; this only bounds a stalled sink. */
        const val DRAIN_TIMEOUT_MILLIS = 20000L
    }
}
