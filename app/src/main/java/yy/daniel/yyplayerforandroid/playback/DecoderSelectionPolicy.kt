package yy.daniel.yyplayerforandroid.playback

/** Keeps codec filtering/order independent from Media3 so the user-visible policies can be verified. */
internal object DecoderSelectionPolicy {
    /** A few emulator/vendor codec registries under-report Android's known software codec flag. */
    fun isKnownPlatformSoftwareCodec(name: String): Boolean =
        name.startsWith("OMX.google.", ignoreCase = true) || name.startsWith("c2.android.", ignoreCase = true)

    fun <T> select(
        candidates: List<T>,
        mode: DecoderMode,
        isHardwareAccelerated: (T) -> Boolean,
        isSoftwareOnly: (T) -> Boolean,
    ): List<T> = when (mode) {
        DecoderMode.AUTO -> candidates
        DecoderMode.PREFER_HARDWARE -> candidates.sortedBy { if (isHardwareAccelerated(it)) 0 else 1 }
        DecoderMode.STRICT_HARDWARE -> candidates.filter(isHardwareAccelerated)
        DecoderMode.SOFTWARE -> candidates.filter(isSoftwareOnly)
    }
}
