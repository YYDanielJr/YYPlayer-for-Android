package yy.daniel.yyplayerforandroid.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the audio output decision and the evidence about it.
 *
 * Order of authority: the portable core decides (pure function, host tested), the opened sink performs
 * the platform requests, and only what that output reports is published
 * as fact. A request that the platform accepts is never promoted to "verified"; a request it
 * refuses is reported as refused instead of silently falling back.
 */
internal class AudioOutputController(context: Context, private val onDeviceLost: () -> Unit = {}) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(AudioOutputState())
    val state: StateFlow<AudioOutputState> = _state.asStateFlow()

    /** Only used by the explicit self-test: normal playback never opens a second output here. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The media Session's own attributes: the platform requires USAGE_MEDIA for mixer attributes. */
    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var source: SourceFormat = SourceFormat(48000, 2, SampleEncoding.S16)
    private var sharing: SharingRequest = SharingRequest.SHARED
    private var transparency: TransparencyRequest = TransparencyRequest.PREFER_UNPROCESSED
    private var processing: ProcessingState = ProcessingState()
    private var playing = false
    private var registered = false
    private var testJob: Job? = null
    private var revision = 0L

    private val selfTest: OutputSelfTest by lazy { OutputSelfTest(appContext, audioAttributes) }

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh("输出设备已连接")
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val removedIds = removedDevices?.map { it.id }.orEmpty()
            if (_state.value.selectedDeviceId in removedIds || _state.value.routedDeviceId in removedIds ||
                (_state.value.followDefault && removedDevices?.any {
                    it.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } == true)) {
                // Retain a missing explicit pin rather than silently selecting the speaker.
                cancelTest()
                clearPlaybackEvidence()
                onDeviceLost()
                publish(message = "输出设备已断开，播放已停止；请选择设备后重新播放")
                return
            }
            refresh("输出设备已断开")
        }
    }

    fun start() {
        if (registered) return
        runCatching { manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper())) }
        registered = true
        refresh(null)
    }

    fun stop() {
        cancelTest()
        if (!registered) return
        runCatching { manager.unregisterAudioDeviceCallback(callback) }
        registered = false
        clearPlaybackEvidence()
    }

    fun setSourceFormat(format: SourceFormat) {
        source = format
        publish(message = null)
    }

    fun setProcessing(state: ProcessingState) {
        processing = state
        publish(message = null)
    }

    fun setPolicy(sharing: SharingRequest, transparency: TransparencyRequest) {
        this.sharing = sharing
        this.transparency = transparency
        publish(message = null)
    }

    fun setPlaying(value: Boolean) {
        playing = value
    }

    /** Follows the system default route or pins one device for this session. */
    fun selectDevice(deviceId: Int?) {
        if (deviceId != null && deviceInfoFor(deviceId) == null) {
            publish("所选设备已断开，请重新选择")
            return
        }
        cancelTest()
        _state.value = _state.value.copy(
            selectedDeviceId = deviceId,
            followDefault = deviceId == null,
        )
        clearPlaybackEvidence()
        publish(message = null)
    }

    private fun refresh(message: String?) {
        publish(message = message)
    }

    /**
     * Recomputes the pure decision and republishes without changing an active mixer. Safe to call
     * at any time: everything it reports is derived from the current device list and core decision.
     */
    private fun publish(message: String?) {
        val channels = AudioDeviceProbe.channels(manager)
        val selected = _state.value.selectedDeviceId?.let { id -> channels.firstOrNull { it.id == id } }
        val route = if (_state.value.selectedDeviceId != null) selected else channels.firstOrNull {
            it.id == AudioDeviceProbe.anticipatedDeviceId(manager, audioAttributes) }

        val decision = NativeAudioCore.decide(
            source = source,
            route = route,
            sharing = sharing,
            transparency = transparency,
            processing = processing,
            // The decision is about whichever route will actually be opened: an explicitly selected
            // device, or the default route the platform reports. The core refuses when neither
            // exists, which is why this is a plain "is there a route" answer and not a preference.
            routeSelected = route != null,
        )

        var note = message

        if (route?.isUsb == true && !route.hasBitPerfectContract) {
            note = note ?: if (Build.VERSION.SDK_INT < 34) {
                "Android ${Build.VERSION.RELEASE} 不支持混音器属性，USB 设备只能走共享输出"
            } else {
                "该 USB 设备未报告任何位完美（BIT_PERFECT）组合"
            }
        }

        _state.value = _state.value.copy(
            channels = channels,
            decision = decision,
            // Capability planning cannot supply live route, format or sharing evidence.
            actualSharing = ActualSharing.UNKNOWN,
            message = note,
            lastUpdatedMs = System.currentTimeMillis(),
        )
    }

    fun clearPlaybackEvidence() {
        _state.value = _state.value.copy(negotiatedOutput = null, actualSampleRate = null,
            actualEncoding = SampleEncoding.UNKNOWN, routedDeviceId = null,
            actualSharing = ActualSharing.UNKNOWN, bitPerfect = BitPerfectStatus.UNKNOWN,
            mixerAttributesActive = false)
    }

    private fun cancelTest() {
        revision++
        // Keep the gate until the IO finally block has closed its track and mixer request.
        testJob?.cancel()
        if (testJob == null) _state.value = _state.value.copy(selfTestRunning = false)
    }

    /**
     * Runs one real output verification: the portable core's decision is opened on an actual sink, the
     * mixer contract is requested, the reference signal is played out of the core's ring, and what the
     * platform reports during playback is published unchanged.
     *
     * The reference signal is an explicit user action, separate from normal decoded music playback.
     * Nothing is retried with a different format: if the platform refuses, the
     * refusal is the result.
     */
    fun runOutputSelfTest(volume: Float = 1f, onFinished: (OutputSelfTestResult?) -> Unit = {}) {
        if (_state.value.selfTestRunning) return
        val current = _state.value
        // The route that is evaluated and the route that gets opened must be described by the same
        // entry, otherwise the readback would describe a different output than the decision does.
        val selected = current.selectedDeviceId?.let { id ->
            current.channels.firstOrNull { it.id == id } ?: described(deviceInfoFor(id))
        }
        val effective = if (current.selectedDeviceId != null) selected else
            current.channels.firstOrNull { it.id == AudioDeviceProbe.anticipatedDeviceId(manager, audioAttributes) }
        val fixture = SourceFormat(source.sampleRate, 2, SampleEncoding.S16)
        val decision = NativeAudioCore.decide(
            source = fixture,
            route = effective,
            sharing = sharing,
            transparency = transparency,
            processing = processing.copy(softwareVolume = volume != 1f),
            routeSelected = effective != null,
        )
        _state.value = current.copy(selfTestRunning = true, message = "正在用决策格式打开输出并回读系统报告…")
        val token = ++revision
        testJob = scope.launch {
            try {
            val result = try {
                selfTest.run(fixture, decision, effective?.id, volume = volume)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                OutputSelfTestResult(
                    started = false,
                    reasonCode = "self_test_failed",
                    reasonText = error::class.java.simpleName + ": " + (error.message ?: "无详细信息"),
                )
            }
            if (token == revision) {
                publish(message = result.reasonText)
                _state.value = _state.value.copy(selfTest = result, selfTestRunning = false)
            }
            } finally {
                testJob = null
                _state.value = _state.value.copy(selfTestRunning = false)
                onFinished(null)
            }
        }
    }

    /**
     * Publishes what the playback sink negotiated. Called by the sink itself, so the settings screen
     * shows the opened output rather than what was asked for.
     */
    fun publishNegotiatedOutput(negotiated: NativeOutputAudioSink.Negotiated?) {
        if (negotiated == null) { clearPlaybackEvidence(); return }
        negotiated.decoderFormat?.let {
            val pcm = SourceFormat(it.sampleRate, it.channelCount, it.encoding)
            if (pcm != source) { source = pcm; publish(null) }
        }
        val report = negotiated.route
        _state.value = _state.value.copy(
            negotiatedOutput = negotiated,
            actualSampleRate = report.trackSampleRate.takeIf { it > 0 },
            actualEncoding = report.trackEncoding,
            routedDeviceId = report.routedDeviceIds.singleOrNull(),
            bitPerfect = if (negotiated.bitPerfectActive) BitPerfectStatus.SYSTEM_REPORTED
                else if (negotiated.bitPerfectRequested) BitPerfectStatus.REQUESTED else BitPerfectStatus.UNAVAILABLE,
            mixerAttributesActive = negotiated.bitPerfectActive,
        )
    }

    /**
     * Records the device the platform actually routed to during playback. Only the platform's own
     * answer is stored, so this cannot become a claim the app made up.
     */
    private fun publishRoutedDevice(route: SinkRouteReport) {
        _state.value = _state.value.copy(
            routedDeviceId = route.routedDeviceIds.firstOrNull(),
            actualSampleRate = route.trackSampleRate.takeIf { it > 0 },
            actualEncoding = route.trackEncoding,
        )
    }

    private fun described(device: AudioDeviceInfo?): AudioOutputChannel? {
        if (device == null) return null
        return AudioDeviceProbe.channels(manager).firstOrNull { it.id == device.id }
    }

    fun deviceInfoFor(id: Int): AudioDeviceInfo? = runCatching {
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
    }.getOrNull()
}
