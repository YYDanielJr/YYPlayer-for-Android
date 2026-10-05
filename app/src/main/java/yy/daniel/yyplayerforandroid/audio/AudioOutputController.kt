package yy.daniel.yyplayerforandroid.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the audio output decision and the evidence about it.
 *
 * Order of authority: the portable core decides (pure function, host tested), this class performs
 * the platform requests the decision asks for, and only what the opened output reports is published
 * as fact. A request that the platform accepts is never promoted to "verified"; a request it
 * refuses is reported as refused instead of silently falling back.
 */
internal class AudioOutputController(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(AudioOutputState())
    val state: StateFlow<AudioOutputState> = _state.asStateFlow()

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

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh("输出设备已连接")
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val removedIds = removedDevices?.map { it.id }.orEmpty()
            if (_state.value.selectedDeviceId in removedIds) {
                // The user's chosen device is gone: stop claiming anything about it.
                selectDevice(null)
                publish(message = "所选输出设备已断开，已恢复自动选择")
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
        if (!registered) return
        runCatching { manager.unregisterAudioDeviceCallback(callback) }
        registered = false
        releaseMixerRequest()
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
        _state.value = _state.value.copy(
            selectedDeviceId = deviceId,
            followDefault = deviceId == null,
        )
        releaseMixerRequest()
        publish(message = null)
    }

    private fun refresh(message: String?) {
        publish(message = message)
    }

    /**
     * Recomputes the decision, performs the mixer request it asks for, and republishes. Safe to call
     * at any time: everything it reports is derived from the current device list and core decision.
     */
    private fun publish(message: String?) {
        val channels = AudioDeviceProbe.channels(manager)
        val selected = _state.value.selectedDeviceId?.let { id -> channels.firstOrNull { it.id == id } }
        val route = selected ?: channels.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?: channels.firstOrNull()

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

        var mixerActive = false
        var bitPerfect = decision.bitPerfect
        var actualEncoding = SampleEncoding.UNKNOWN
        var actualRate: Int? = null
        var routedId: Int? = null
        var note = message

        if (decision.accepted && decision.requestBitPerfect && selected != null) {
            val device = deviceInfoFor(selected.id)
            val profile = selected.bitPerfectProfiles.firstOrNull {
                it.sampleRate == source.sampleRate && it.encoding == source.encoding
            }
            if (device != null && profile != null) {
                val accepted = AudioDeviceProbe.requestMixerAttributes(manager, device, profile, audioAttributes)
                val confirmed = AudioDeviceProbe.currentMixerAttributes(manager, device, audioAttributes)
                mixerActive = accepted && confirmed != null
                if (mixerActive) {
                    // The platform accepted the contract for exactly this format. That is still
                    // "system reported", not a measurement, and the label says so.
                    bitPerfect = BitPerfectStatus.SYSTEM_REPORTED
                    actualEncoding = confirmed!!.encoding
                    actualRate = confirmed.sampleRate
                    routedId = device.id
                } else {
                    bitPerfect = BitPerfectStatus.UNAVAILABLE
                    note = note ?: "系统未接受位完美混音器请求（设备或版本不支持）"
                }
            } else {
                bitPerfect = BitPerfectStatus.UNAVAILABLE
                note = note ?: "该设备未报告与源格式一致的位完美组合"
            }
        }

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
            actualSharing = if (playing) decision.expectedSharing else ActualSharing.UNKNOWN,
            bitPerfect = bitPerfect,
            actualSampleRate = actualRate,
            actualEncoding = actualEncoding,
            routedDeviceId = routedId,
            mixerAttributesActive = mixerActive,
            message = note,
            lastUpdatedMs = System.currentTimeMillis(),
        )
    }

    /** Drops the app's preferred mixer attributes so the device is not held for a paused session. */
    fun releaseMixerRequest() {
        if (Build.VERSION.SDK_INT < 34) return
        val selectedId = _state.value.selectedDeviceId ?: return
        val device = deviceInfoFor(selectedId) ?: return
        AudioDeviceProbe.clearMixerAttributes(manager, device, audioAttributes)
    }

    fun deviceInfoFor(id: Int): AudioDeviceInfo? = runCatching {
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
    }.getOrNull()
}
