package yy.daniel.yyplayerforandroid.audio

/**
 * Detects real output capabilities. Nothing here invents a capability: a missing list, a
 * non-USB device or an older platform produces an empty result and the UI explains it.
 */
internal object AudioDeviceProbe {

    fun channels(manager: android.media.AudioManager): List<AudioOutputChannel> {
        val devices = runCatching {
            manager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrNull() ?: return emptyList()
        return devices.mapNotNull { describe(manager, it) }
    }

    /** A planning hint, never evidence of the route of an opened track. */
    fun anticipatedDeviceId(manager: android.media.AudioManager, attributes: android.media.AudioAttributes): Int? =
        if (android.os.Build.VERSION.SDK_INT >= 33) Api33Route.deviceId(manager, attributes) else null

    @androidx.annotation.RequiresApi(33)
    private object Api33Route {
        fun deviceId(manager: android.media.AudioManager, attributes: android.media.AudioAttributes): Int? =
            runCatching { manager.getAudioDevicesForAttributes(attributes).singleOrNull()?.id }.getOrNull()
    }

    private fun describe(
        manager: android.media.AudioManager,
        device: android.media.AudioDeviceInfo,
    ): AudioOutputChannel? {
        val isUsb = device.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY
        val supported = if (isUsb && android.os.Build.VERSION.SDK_INT >= 34) {
            Api34Mixer.profiles(manager, device)
        } else {
            emptyList()
        }
        return AudioOutputChannel(
            id = device.id,
            type = device.type,
            typeLabel = typeLabel(device.type),
            productName = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeLabel(device.type),
            address = if (android.os.Build.VERSION.SDK_INT >= 28) runCatching { device.address }.getOrDefault("") else "",
            isUsb = isUsb,
            sampleRates = runCatching { device.sampleRates.toList() }.getOrDefault(emptyList()),
            channelCounts = runCatching { device.channelCounts.toList() }.getOrDefault(emptyList()),
            encodings = runCatching { device.encodings.toList() }.getOrDefault(emptyList()),
            // The platform only exposes stream-sharing evidence for an AAudio/Oboe stream, so the
            // system path must never claim exclusivity.
            sink = if (android.os.Build.VERSION.SDK_INT >= 34 && isUsb) {
                SinkKind.AUDIO_TRACK_SYSTEM
            } else {
                SinkKind.AUDIO_TRACK_SYSTEM
            },
            bitPerfectProfiles = supported.filter { it.bitPerfect },
            supportedProfiles = supported.filterNot { it.bitPerfect },
            exclusivePlausible = false,
            canReportActualSharing = false,
        )
    }

    fun typeLabel(type: Int): String = when (type) {
        android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "听筒"
        android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "内置扬声器"
        android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "内置扬声器（安全）"
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳机（带麦）"
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
        android.media.AudioDeviceInfo.TYPE_LINE_ANALOG -> "模拟线路输出"
        android.media.AudioDeviceInfo.TYPE_LINE_DIGITAL -> "数字线路输出"
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙 A2DP"
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙通话"
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙 LE 耳机"
        android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER -> "蓝牙 LE 扬声器"
        android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> "USB 音频设备"
        android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 耳机"
        android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB 配件音频"
        android.media.AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        android.media.AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
        android.media.AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI eARC"
        android.media.AudioDeviceInfo.TYPE_DOCK -> "底座"
        android.media.AudioDeviceInfo.TYPE_AUX_LINE -> "AUX"
        android.media.AudioDeviceInfo.TYPE_IP -> "网络音频"
        android.media.AudioDeviceInfo.TYPE_BUS -> "总线输出"
        android.media.AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "远程混音"
        else -> "未知类型（$type）"
    }

    /** Maps an AudioFormat encoding to the core's sample encoding, or UNKNOWN when unsupported. */
    fun encodingOf(encoding: Int): SampleEncoding = when (encoding) {
        android.media.AudioFormat.ENCODING_PCM_16BIT -> SampleEncoding.S16
        android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> SampleEncoding.S24_3
        android.media.AudioFormat.ENCODING_PCM_32BIT -> SampleEncoding.S32
        android.media.AudioFormat.ENCODING_PCM_FLOAT -> SampleEncoding.F32
        else -> SampleEncoding.UNKNOWN
    }

    fun audioFormatEncodingOf(encoding: SampleEncoding): Int = when (encoding) {
        SampleEncoding.S16 -> android.media.AudioFormat.ENCODING_PCM_16BIT
        SampleEncoding.S24_3 -> if (android.os.Build.VERSION.SDK_INT >= 31)
            android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED else android.media.AudioFormat.ENCODING_INVALID
        SampleEncoding.S32 -> if (android.os.Build.VERSION.SDK_INT >= 31)
            android.media.AudioFormat.ENCODING_PCM_32BIT else android.media.AudioFormat.ENCODING_INVALID
        SampleEncoding.F32 -> android.media.AudioFormat.ENCODING_PCM_FLOAT
        else -> android.media.AudioFormat.ENCODING_INVALID
    }

    /**
     * The positional channel mask for a plain interleaved channel count. `ENCODING_PCM_24BIT_PACKED`
     * and the other integer encodings are only valid with a positional mask, and the platform's
     * preset masks cover at most the layouts this core produces.
     */
    fun audioChannelMask(channels: Int): Int = when (channels) {
        1 -> android.media.AudioFormat.CHANNEL_OUT_MONO
        2 -> android.media.AudioFormat.CHANNEL_OUT_STEREO
        3 -> android.media.AudioFormat.CHANNEL_OUT_STEREO or android.media.AudioFormat.CHANNEL_OUT_FRONT_CENTER
        4 -> android.media.AudioFormat.CHANNEL_OUT_QUAD
        6 -> android.media.AudioFormat.CHANNEL_OUT_5POINT1
        8 -> android.media.AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
        else -> 0
    }

    /** True for the device types the platform treats as a USB audio output. */
    fun isUsbOutput(device: android.media.AudioDeviceInfo): Boolean =
        device.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY

    /**
     * Preferred mixer attributes exist only on API 34+. Keeping every reference in this nested
     * object means API 26-33 never loads the class, which is the same isolation rule the blur
     * facade uses (see ADR 0002).
     */
    @androidx.annotation.RequiresApi(34)
    private object Api34Mixer {
        fun profiles(
            manager: android.media.AudioManager,
            device: android.media.AudioDeviceInfo,
        ): List<MixerProfile> {
            val attributes = runCatching { manager.getSupportedMixerAttributes(device) }.getOrNull()
                ?: return emptyList()
            return attributes.mapNotNull { mixer ->
                val format = runCatching { mixer.format }.getOrNull() ?: return@mapNotNull null
                val encoding = encodingOf(format.encoding)
                if (encoding == SampleEncoding.UNKNOWN) return@mapNotNull null
                val bitPerfect = runCatching {
                    mixer.mixerBehavior == android.media.AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
                }.getOrDefault(false)
                MixerProfile(
                    encoding = encoding,
                    sampleRate = format.sampleRate,
                    channelMask = format.channelMask,
                    bitPerfect = bitPerfect,
                    mixesWithOthers = !bitPerfect,
                )
            }
        }

        /**
         * Asks the platform to configure the USB mixer for one format. Returns false when the
         * platform refused, which is a real result the UI must show; it never throws.
         */
        fun request(
            manager: android.media.AudioManager,
            device: android.media.AudioDeviceInfo,
            profile: MixerProfile,
            attributes: android.media.AudioAttributes,
        ): Boolean {
            val encoding = audioFormatEncodingOf(profile.encoding)
            if (encoding == android.media.AudioFormat.ENCODING_INVALID) return false
            val format = android.media.AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(profile.sampleRate)
                .setChannelMask(profile.channelMask)
                .build()
            val mixer = runCatching {
                android.media.AudioMixerAttributes.Builder(format)
                    .setMixerBehavior(
                        if (profile.bitPerfect) {
                            android.media.AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
                        } else {
                            android.media.AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT
                        },
                    )
                    .build()
            }.getOrNull() ?: return false
            return runCatching { manager.setPreferredMixerAttributes(attributes, device, mixer) }
                .getOrDefault(false)
        }

        fun clear(
            manager: android.media.AudioManager,
            device: android.media.AudioDeviceInfo,
            attributes: android.media.AudioAttributes,
        ): Boolean = runCatching {
            manager.clearPreferredMixerAttributes(attributes, device)
        }.getOrDefault(false)

        fun current(
            manager: android.media.AudioManager,
            device: android.media.AudioDeviceInfo,
            attributes: android.media.AudioAttributes,
        ): MixerProfile? {
            val mixer = runCatching {
                manager.getPreferredMixerAttributes(attributes, device)
            }.getOrNull() ?: return null
            val format = runCatching { mixer.format }.getOrNull() ?: return null
            val encoding = encodingOf(format.encoding)
            if (encoding == SampleEncoding.UNKNOWN) return null
            val bitPerfect = runCatching {
                mixer.mixerBehavior == android.media.AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
            }.getOrDefault(false)
            return MixerProfile(encoding, format.sampleRate, format.channelMask, bitPerfect, !bitPerfect)
        }
    }

    /** Null-safe wrapper so callers on API < 34 get a plain "not supported" answer. */
    fun requestMixerAttributes(
        manager: android.media.AudioManager,
        device: android.media.AudioDeviceInfo,
        profile: MixerProfile,
        attributes: android.media.AudioAttributes,
    ): Boolean = if (android.os.Build.VERSION.SDK_INT >= 34) {
        Api34Mixer.request(manager, device, profile, attributes)
    } else {
        false
    }

    fun clearMixerAttributes(
        manager: android.media.AudioManager,
        device: android.media.AudioDeviceInfo,
        attributes: android.media.AudioAttributes,
    ): Boolean = if (android.os.Build.VERSION.SDK_INT >= 34) {
        Api34Mixer.clear(manager, device, attributes)
    } else {
        false
    }

    fun currentMixerAttributes(
        manager: android.media.AudioManager,
        device: android.media.AudioDeviceInfo,
        attributes: android.media.AudioAttributes,
    ): MixerProfile? = if (android.os.Build.VERSION.SDK_INT >= 34) {
        Api34Mixer.current(manager, device, attributes)
    } else {
        null
    }
}
