package yy.daniel.yyplayerforandroid.audio

/**
 * Chooses the format a sink is opened with, given what the source actually contains and what the
 * device reports it can accept.
 *
 * Kept separate from both the Media3 adapter and the self-test so the two agree by construction: the
 * self-test proves the path the player then uses, instead of exercising a parallel rule that only the
 * test knows about.
 */
internal object OutputFormatPlanner {

    /** The source as the decoder produced it, before any widening. */
    data class Planned(
        val format: OutputFormat,
        /** True when the format differs from the source container and the core has to widen it. */
        val requiresWidening: Boolean,
        /** True when the route reports a bit-perfect contract for exactly this format. */
        val bitPerfectContractAvailable: Boolean,
    )

    /**
     * [sourceEncoding] and [sourceChannels] describe the decoded PCM; [route] is the device that will be
     * opened, or null when nothing was selected.
     *
     * Preference order: a format the device actually reported for this sample rate and channel count,
     * then a wider container at the same rate, then the source format itself (the platform may still
     * accept it, and the sink refuses rather than substituting).
     */
    fun plan(
        sampleRate: Int,
        sourceEncoding: SampleEncoding,
        sourceChannels: Int,
        route: AudioOutputChannel?,
    ): Planned {
        val channelMask = AudioDeviceProbe.audioChannelMask(sourceChannels)
        val source = OutputFormat(sampleRate, sourceChannels, sourceEncoding, channelMask)
        val profiles = route?.profiles.orEmpty().filter {
            it.sampleRate == sampleRate && (it.channelMask == 0 || it.channelMask == channelMask)
        }
        // An exact container match needs no conversion at all, so it wins whenever the device offers it.
        val exact = profiles.firstOrNull { it.encoding == sourceEncoding }
        if (exact != null) {
            return Planned(
                format = source.copy(channelMask = exact.channelMask.takeIf { it != 0 } ?: channelMask),
                requiresWidening = false,
                bitPerfectContractAvailable = exact.bitPerfect,
            )
        }
        // Otherwise take the narrowest wider container the device offers that the core can widen into
        // without dropping a bit; 24-in-32 is skipped because AudioFormat has no such encoding.
        val wider = profiles
            .filter { it.encoding.bytesPerSample > 0 && it.encoding != SampleEncoding.S24_IN32 }
            .filter { NativeAudioCore.isPristineConversion(sourceEncoding, it.encoding) }
            .filter { it.encoding.effectiveBits > sourceEncoding.effectiveBits }
            .minByOrNull { it.encoding.effectiveBits }
        if (wider != null) {
            return Planned(
                format = OutputFormat(
                    sampleRate = sampleRate,
                    channelCount = sourceChannels,
                    encoding = wider.encoding,
                    channelMask = wider.channelMask.takeIf { it != 0 } ?: channelMask,
                ),
                requiresWidening = true,
                bitPerfectContractAvailable = wider.bitPerfect,
            )
        }
        // Nothing usable was reported: keep the source format so a refusal names the real format.
        return Planned(source, requiresWidening = false, bitPerfectContractAvailable = false)
    }

    /**
     * Whether to ask the platform for a bit-perfect mixer contract. Only USB outputs can have one, and
     * only when the device reports the contract for this exact format; asking otherwise would be a
     * request the platform cannot honour.
     */
    fun shouldRequestBitPerfect(route: AudioOutputChannel?, planned: Planned): Boolean =
        route?.isUsb == true && planned.bitPerfectContractAvailable
}
