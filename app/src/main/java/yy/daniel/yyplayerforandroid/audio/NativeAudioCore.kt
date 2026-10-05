package yy.daniel.yyplayerforandroid.audio

import yy.daniel.yyplayerforandroid.audio.json.JsonValue

/**
 * The only entry point to the portable C++ core (`native/jni/yy_jni.cpp`).
 *
 * Everything the core owns - PCM format rules, pristine conversion, the sink ring and the output
 * policy decision - is reached through this object, so the same logic can be reused by a future
 * desktop frontend without the Android layer. Control data crosses as JSON, PCM as direct byte
 * buffers; no player or Context type is exposed and no allocation happens here.
 *
 * The library is loaded in a nested object so that the pure request/response translation below stays
 * usable from host unit tests, where no native library exists. Those two translation functions are
 * public precisely so `AudioPolicyWireTest` can lock the wire format on the host; everything else
 * about this class is an implementation detail.
 */
object NativeAudioCore {
    /** Bumped whenever the JNI contract changes; Kotlin refuses to run on a version mismatch. */
    const val EXPECTED_VERSION = 1

    /**
     * The native methods live in a nested object so the pure translation above stays usable from
     * host unit tests. JNI resolves them by the mangled class name, so these are the names the C++
     * bridge must export: `Java_yy_daniel_yyplayerforandroid_audio_NativeAudioCore_00024Bridge_*`.
     */
    private object Bridge {
        init {
            System.loadLibrary("yyplayer_jni")
        }

        external fun nativeBridgeVersion(): Int
        external fun nativeEvaluateOutputPolicy(requestJson: String): String?
        external fun createRing(
            sampleRate: Int,
            channels: Int,
            encodingWire: String,
            channelMask: Int,
            capacityFrames: Long,
        ): Long

        external fun ringWrite(handle: Long, buffer: java.nio.ByteBuffer, frames: Int): Int
        external fun ringRead(handle: Long, buffer: java.nio.ByteBuffer, maxFrames: Int): Int
        external fun ringClear(handle: Long): Boolean
        external fun ringStats(handle: Long): LongArray?
        external fun releaseRing(handle: Long): Boolean
        external fun convertPcm(
            fromWire: String,
            toWire: String,
            src: java.nio.ByteBuffer,
            srcBytes: Int,
            dst: java.nio.ByteBuffer,
            dstBytes: Int,
            channels: Int,
        ): Int
    }

    private val loaded: Boolean by lazy {
        runCatching { Bridge.nativeBridgeVersion() == EXPECTED_VERSION }.getOrDefault(false)
    }

    val available: Boolean get() = loaded

    fun version(): Int? = runCatching { Bridge.nativeBridgeVersion() }.getOrNull()

    fun createRing(
        sampleRate: Int,
        channels: Int,
        encoding: SampleEncoding,
        channelMask: Int,
        capacityFrames: Long,
    ): Long = runCatching {
        Bridge.createRing(sampleRate, channels, encoding.wire, channelMask, capacityFrames)
    }.getOrDefault(0L)

    fun ringWrite(handle: Long, buffer: java.nio.ByteBuffer, frames: Int): Int =
        runCatching { Bridge.ringWrite(handle, buffer, frames) }.getOrDefault(0)

    fun ringRead(handle: Long, buffer: java.nio.ByteBuffer, maxFrames: Int): Int =
        runCatching { Bridge.ringRead(handle, buffer, maxFrames) }.getOrDefault(0)

    fun ringClear(handle: Long): Boolean = runCatching { Bridge.ringClear(handle) }.getOrDefault(false)

    fun ringStats(handle: Long): LongArray? = runCatching { Bridge.ringStats(handle) }.getOrNull()

    fun releaseRing(handle: Long): Boolean = runCatching { Bridge.releaseRing(handle) }.getOrDefault(false)

    fun convertPcm(
        from: SampleEncoding,
        to: SampleEncoding,
        src: java.nio.ByteBuffer,
        srcBytes: Int,
        dst: java.nio.ByteBuffer,
        dstBytes: Int,
        channels: Int,
    ): Int = runCatching {
        Bridge.convertPcm(from.wire, to.wire, src, srcBytes, dst, dstBytes, channels)
    }.getOrDefault(-1)

    /**
     * Asks the core what to do with one output request. Returns a rejection carrying the reason
     * instead of guessing when the native library is missing, so a broken build degrades to
     * "unavailable" rather than to a false bit-perfect claim.
     */
    fun decide(
        source: SourceFormat,
        route: AudioOutputChannel?,
        sharing: SharingRequest,
        transparency: TransparencyRequest,
        processing: ProcessingState,
        routeSelected: Boolean = route != null,
    ): OutputDecision {
        if (!loaded) {
            return OutputDecision.rejected("native_unavailable", "原生核心未装载，无法评估输出计划")
        }
        val request = buildRequest(source, route, sharing, transparency, processing, routeSelected)
        val response = try {
            Bridge.nativeEvaluateOutputPolicy(request)
        } catch (error: Throwable) {
            android.util.Log.e("YYPlayerAudio", "YYPLAYER_POLICY_THREW ${error::class.java.name}: ${error.message}")
            null
        } ?: return OutputDecision.rejected("native_error", "原生核心调用失败")
        return parseDecision(response)
    }

    /** Host-test hooks so the wire format is locked without needing a device. */
    fun buildRequestForTest(
        source: SourceFormat,
        route: AudioOutputChannel?,
        sharing: SharingRequest,
        transparency: TransparencyRequest,
        processing: ProcessingState,
        routeSelected: Boolean,
    ): String = buildRequest(source, route, sharing, transparency, processing, routeSelected)

    fun parseDecisionForTest(response: String): OutputDecision = parseDecision(response)

    private fun buildRequest(
        source: SourceFormat,
        route: AudioOutputChannel?,
        sharing: SharingRequest,
        transparency: TransparencyRequest,
        processing: ProcessingState,
        routeSelected: Boolean,
    ): String = buildString(512) {
        append("{\"format\":{")
        append("\"sampleRate\":").append(source.sampleRate).append(',')
        append("\"channels\":").append(source.channelCount).append(',')
        append("\"encoding\":\"").append(source.encoding.wire).append("\",")
        append("\"channelMask\":").append(source.channelMask)
        append("},\"sharing\":\"").append(sharing.wire).append("\",")
        append("\"transparency\":\"").append(transparency.wire).append("\",")
        append("\"routeSelected\":").append(routeSelected).append(',')
        append("\"processing\":{")
        append("\"softwareVolume\":").append(processing.softwareVolume).append(',')
        append("\"equalizer\":").append(processing.equalizer).append(',')
        append("\"replayGain\":").append(processing.replayGain).append(',')
        append("\"speedChange\":").append(processing.speedChange)
        append("},\"route\":{")
        append("\"deviceId\":").append(route?.id ?: 0).append(',')
        append("\"sink\":\"").append(route?.sink?.wire ?: SinkKind.UNKNOWN.wire).append("\",")
        append("\"supportsBitPerfect\":").append(route?.hasBitPerfectContract == true).append(',')
        append("\"supportsExclusive\":").append(route?.exclusivePlausible == true).append(',')
        append("\"canReportActualSharing\":").append(route?.canReportActualSharing == true).append(',')
        append("\"profiles\":[")
        route?.profiles?.forEachIndexed { index, profile ->
            if (index > 0) append(',')
            append("{\"encoding\":\"").append(profile.encoding.wire).append("\",")
            append("\"sampleRate\":").append(profile.sampleRate).append(',')
            append("\"channelMask\":").append(profile.channelMask).append(',')
            append("\"bitPerfect\":").append(profile.bitPerfect).append(',')
            append("\"mixesWithOthers\":").append(profile.mixesWithOthers)
            append('}')
        }
        append("]}}")
    }

    private fun parseDecision(response: String): OutputDecision {
        // The bridge returns the response as an object body (the text between its braces), which is
        // the same shape the request uses for nested objects, so it is wrapped exactly once here.
        val root = JsonValue.parse(response.trim().let { if (it.startsWith("{")) it else "{$it}" })
            ?: return OutputDecision.rejected("native_error", "原生核心返回了无法解析的结果")
        val sharing = SharingRequest.entries.firstOrNull { it.wire == root.string("requestedSharing") }
            ?: SharingRequest.SHARED
        val accepted = root.bool("accepted") == true
        // The response carries the chosen format as flat fields next to the decision itself.
        val chosen = if (accepted) {
            OutputFormat(
                sampleRate = root.int("sampleRate")?.toInt() ?: 0,
                channelCount = root.int("channels")?.toInt() ?: 0,
                encoding = SampleEncoding.fromWire(root.string("encoding")),
                channelMask = root.int("channelMask")?.toInt() ?: 0,
            )
        } else {
            null
        }
        return OutputDecision(
            accepted = accepted,
            reasonCode = root.string("reasonCode") ?: "unknown",
            reasonText = root.string("reasonText") ?: "未知情况",
            sink = SinkKind.entries.firstOrNull { it.wire == root.string("sink") } ?: SinkKind.UNKNOWN,
            format = chosen,
            requestedSharing = sharing,
            expectedSharing = ActualSharing.entries.firstOrNull { it.wire == root.string("expectedSharing") }
                ?: ActualSharing.UNKNOWN,
            requestBitPerfect = root.bool("requestBitPerfect") == true,
            pristineExpected = root.bool("pristineExpected") == true,
            sameRouteFallback = root.bool("sameRouteFallback") == true,
            bitPerfect = BitPerfectStatus.entries.firstOrNull { it.wire == root.string("bitPerfect") }
                ?: BitPerfectStatus.UNKNOWN,
        )
    }
}
