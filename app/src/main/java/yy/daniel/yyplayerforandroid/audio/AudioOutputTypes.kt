package yy.daniel.yyplayerforandroid.audio

/**
 * PCM sample encodings. The names are part of the contract with the portable C++ core
 * (`yy::core::SampleEncoding`) and are used verbatim in the JSON policy request, so they must not
 * be renamed without changing both sides.
 */
enum class SampleEncoding(val wire: String, val bytesPerSample: Int, val effectiveBits: Int) {
    UNKNOWN("unknown", 0, 0),
    U8("u8", 1, 8),
    S16("s16", 2, 16),
    S24_3("s24_3", 3, 24),
    S24_IN32("s24_in32", 4, 24),
    S32("s32", 4, 32),
    F32("f32", 4, 32);

    companion object {
        fun fromWire(value: String?): SampleEncoding =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

/**
 * What the user asked for regarding stream sharing. This is a request, never a result: only what the
 * opened output reports can be shown as a fact.
 */
enum class SharingRequest(val wire: String, val label: String) {
    SHARED("shared", "共享输出"),
    PREFER_EXCLUSIVE("prefer_exclusive", "优先独占"),
    STRICT_EXCLUSIVE("strict_exclusive", "严格独占"),
}

/** What the user asked for regarding sample processing. */
enum class TransparencyRequest(val wire: String, val label: String) {
    COMPATIBLE("compatible", "兼容播放"),
    PREFER_UNPROCESSED("prefer_unprocessed", "优先原样输出"),
    STRICT_UNPROCESSED("strict_unprocessed", "严格原样输出"),
}

enum class SinkKind(val wire: String, val label: String) {
    UNKNOWN("unknown", "未知"),
    AUDIO_TRACK_SYSTEM("audiotrack_system", "系统 AudioTrack"),
    OBOE("oboe", "AAudio / Oboe"),
    USB_DIRECT("usb_direct", "USB 直通"),
}

/** Actual stream sharing as reported by the opened output; unknown is a real state here. */
enum class ActualSharing(val wire: String, val label: String) {
    UNKNOWN("unknown", "未知"),
    SHARED("shared", "共享"),
    EXCLUSIVE("exclusive", "独占"),
}

/**
 * Two independent evidence levels. kSystemReported means the platform accepted a mixer/BIT_PERFECT
 * contract and the actual route and format matched; it is not a measurement. kCaptureVerified is
 * reserved for digital capture evidence and is never set by the app.
 */
enum class BitPerfectStatus(val wire: String, val label: String) {
    UNKNOWN("unknown", "未知"),
    UNAVAILABLE("unavailable", "不可用"),
    REQUESTED("requested", "已请求，待确认"),
    SYSTEM_REPORTED("system_reported", "系统报告位完美"),
    CAPTURE_VERIFIED("capture_verified", "数字捕获已验证"),
}

/** Software processing that is currently applied to the samples. */
data class ProcessingState(
    val softwareVolume: Boolean = false,
    val equalizer: Boolean = false,
    val replayGain: Boolean = false,
    val speedChange: Boolean = false,
) {
    val any: Boolean get() = softwareVolume || equalizer || replayGain || speedChange
}

/** One output format a route reports as usable, including whether it is a bit-perfect contract. */
data class MixerProfile(
    val encoding: SampleEncoding,
    val sampleRate: Int,
    val channelMask: Int,
    val bitPerfect: Boolean,
    val mixesWithOthers: Boolean,
)

/** A source format the player is about to decode into. */
data class SourceFormat(
    val sampleRate: Int,
    val channelCount: Int,
    val encoding: SampleEncoding,
) {
    val channelMask: Int get() = if (channelCount in 1..31) (1 shl channelCount) - 1 else 0
}

/** The format a sink must be opened with. */
data class OutputFormat(
    val sampleRate: Int,
    val channelCount: Int,
    val encoding: SampleEncoding,
    val channelMask: Int,
) {
    val effectiveBits: Int get() = encoding.effectiveBits
}

/** What the planner decided to do, or why it refused. */
data class OutputDecision(
    val accepted: Boolean,
    val reasonCode: String,
    val reasonText: String,
    val sink: SinkKind,
    val format: OutputFormat?,
    val requestedSharing: SharingRequest,
    val expectedSharing: ActualSharing,
    val requestBitPerfect: Boolean,
    val pristineExpected: Boolean,
    val sameRouteFallback: Boolean,
    val bitPerfect: BitPerfectStatus,
) {
    companion object {
        fun rejected(reasonCode: String, reasonText: String): OutputDecision = OutputDecision(
            accepted = false,
            reasonCode = reasonCode,
            reasonText = reasonText,
            sink = SinkKind.UNKNOWN,
            format = null,
            requestedSharing = SharingRequest.SHARED,
            expectedSharing = ActualSharing.UNKNOWN,
            requestBitPerfect = false,
            pristineExpected = false,
            sameRouteFallback = false,
            bitPerfect = BitPerfectStatus.UNKNOWN,
        )
    }
}

/**
 * One selectable output channel as reported by the platform. [id] is the AudioDeviceInfo id, which
 * is only valid for the current session; a stable identity has to use [type] plus [address].
 */
data class AudioOutputChannel(
    val id: Int,
    val type: Int,
    val typeLabel: String,
    val productName: String,
    val address: String,
    val isUsb: Boolean,
    val sampleRates: List<Int>,
    val channelCounts: List<Int>,
    val encodings: List<Int>,
    val sink: SinkKind,
    val bitPerfectProfiles: List<MixerProfile>,
    val supportedProfiles: List<MixerProfile>,
    /**
     * True when an exclusive stream is plausible on this route. Only AAudio/Oboe can report its
     * actual sharing mode, so the system AudioTrack path always reports false here even when the
     * route reports a bit-perfect mixer contract.
     */
    val exclusivePlausible: Boolean,
    val canReportActualSharing: Boolean,
) {
    val stableKey: String get() = "$type:$address"

    /** Every format the route reports, bit-perfect entries first so the UI shows them first. */
    val profiles: List<MixerProfile>
        get() = (bitPerfectProfiles + supportedProfiles).distinct()

    /** True when at least one profile is a system bit-perfect contract. */
    val hasBitPerfectContract: Boolean get() = bitPerfectProfiles.isNotEmpty()

    /**
     * True only for outputs where a system bit-perfect contract is even possible. The platform
     * allows preferred mixer attributes for USB devices only, so nothing else may ever claim one.
     */
    val bitPerfectPossible: Boolean get() = isUsb
}

/** What the app currently reports about its output path. Everything here is evidence, not intent. */
internal data class AudioOutputState(
    val channels: List<AudioOutputChannel> = emptyList(),
    val selectedDeviceId: Int? = null,
    val followDefault: Boolean = true,
    val decision: OutputDecision = OutputDecision.rejected("not_evaluated", "尚未评估输出计划"),
    val actualSharing: ActualSharing = ActualSharing.UNKNOWN,
    val bitPerfect: BitPerfectStatus = BitPerfectStatus.UNKNOWN,
    val actualSampleRate: Int? = null,
    val actualEncoding: SampleEncoding = SampleEncoding.UNKNOWN,
    val routedDeviceId: Int? = null,
    val mixerAttributesActive: Boolean = false,
    val underrunFrames: Long = 0,
    /** Result of the last explicit output self-test; null until one has been run in this session. */
    val selfTest: OutputSelfTestResult? = null,
    val selfTestRunning: Boolean = false,
    /**
     * What the playback output reported for the stream that is actually playing, or null when nothing
     * has been opened. Published by the sink that owns the output, so it is evidence rather than a
     * restatement of the setting.
     */
    val negotiatedOutput: NativeOutputAudioSink.Negotiated? = null,
    val message: String? = null,
    val lastUpdatedMs: Long = 0,
) {
    val selectedChannel: AudioOutputChannel?
        get() = channels.firstOrNull { it.id == selectedDeviceId }

    val routedChannel: AudioOutputChannel?
        get() = channels.firstOrNull { it.id == routedDeviceId }
}
