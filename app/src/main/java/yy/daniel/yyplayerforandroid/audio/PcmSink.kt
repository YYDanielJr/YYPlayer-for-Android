package yy.daniel.yyplayerforandroid.audio

/**
 * Single log tag for the whole audio output path, so an evidence run can filter one stream and see
 * the decision, the platform requests and the readbacks in the order they happened.
 */
internal const val YY_AUDIO_TAG = "YYPlayerAudio"

/**
 * Which output path playback uses.
 *
 * [SYSTEM] is the platform sink that Media3 creates itself: maximum compatibility, always shared, no
 * claim about sample transparency. [NATIVE_EXACT] routes decoded PCM through the app's own sink, which
 * is the only path that can ask the platform for a bit-perfect mixer contract on a USB device and
 * report what the platform actually did. The two are mutually exclusive by construction - only one of
 * them owns the output - which is also why switching the setting rebuilds the player.
 */
enum class OutputPath(val wire: String, val label: String, val summary: String) {
    SYSTEM("system", "系统输出", "由系统混音器处理，兼容性最好，不声明原样输出"),
    NATIVE_EXACT("native_exact", "自研输出（原样优先）", "使用应用 AudioTrack；支持时请求 BIT_PERFECT，否则共享输出。AudioTrack 不证明流独占"),
    ;

    companion object {
        fun fromWire(value: String?): OutputPath = entries.firstOrNull { it.wire == value } ?: SYSTEM
    }
}

/**
 * The boundary between the portable playback core and whatever actually consumes PCM on this
 * platform.
 *
 * The core owns the ring; a sink only ever *pulls* from it. That direction is deliberate: a sink can
 * be slow (a blocking platform write), and the producer must then lose frames it cannot deliver
 * instead of blocking the decoder. A sink therefore never receives sample data by value - it is
 * handed a ring handle and reads frames out of it on its own thread.
 *
 * Everything a sink reports back is what the platform said, never what was requested. A sink that
 * cannot see the actual sharing mode reports `ActualSharing.UNKNOWN` rather than assuming the
 * requested one.
 */
internal interface PcmSink {

    val kind: SinkKind

    /** The format the platform accepted, or null while the sink is not open. */
    val openFormat: OutputFormat?

    /** Whole frames this sink has taken out of the ring since it opened. */
    fun framesConsumed(): Long

    /**
     * Frames the ring could not supply while the sink was asking. This is the sink's own view; the
     * ring keeps the same count on its side and the two must agree.
     */
    fun starvingFrames(): Long

    /**
     * Opens the platform output for exactly [format].
     *
     * A sink must refuse rather than substitute a different format: the whole point of the output
     * decision is that the format which reaches the device is the one that was decided. Returns a
     * failure with a reason instead of throwing.
     */
    fun open(format: OutputFormat, requestedDeviceId: Int?): Result<Unit>

    /** Starts pulling from [ringHandle] on the sink's own thread. */
    fun start(ringHandle: Long): Result<Unit>

    fun pause()

    fun stop()

    /**
     * Releases the platform output. Idempotent; after this the sink is closed and [openFormat] is
     * null again.
     */
    fun close()
}

/** Why a sink refused to open. Codes are stable ASCII so they can be logged and compared. */
internal enum class SinkFailure(val code: String, val label: String) {
    NOT_SUPPORTED("sink_not_supported", "此设备/版本不支持该输出方式"),
    BAD_FORMAT("sink_bad_format", "输出格式无法交给平台"),
    DEVICE_GONE("sink_device_gone", "所选输出设备已不在设备列表中"),
    OPEN_FAILED("sink_open_failed", "平台拒绝创建输出流"),
    UNINITIALIZED("sink_uninitialized", "系统未能初始化输出流"),
    NO_RING("sink_no_ring", "原生 PCM 环不可用"),
}

/**
 * What the platform said about the opened output while it was actually playing.
 *
 * `routedDevices` comes from `AudioTrack.getRoutedDevices()` and is only meaningful during
 * playback, which is why this is captured after `play()` rather than at open time.
 */
data class SinkRouteReport(
    val routedDeviceIds: List<Int> = emptyList(),
    val routedDeviceNames: List<String> = emptyList(),
    val routedIsUsb: Boolean = false,
    /**
     * AudioTrack's configured API format. It does not describe HAL/hardware format or prove absence
     * of mixing/resampling. Only a qualified contract plus actual route can add system evidence.
     */
    val platformFormat: OutputFormat? = null,
    /** `AudioTrack.getSampleRate()`: the rate the track was actually configured with. */
    val trackSampleRate: Int = 0,
    /** `AudioTrack.getFormat()`: the encoding the track was actually opened with. */
    val trackEncoding: SampleEncoding = SampleEncoding.UNKNOWN,
    val trackChannelMask: Int = 0,
    val bufferSizeFrames: Int = 0,
    val underrunCount: Int? = null,
)
