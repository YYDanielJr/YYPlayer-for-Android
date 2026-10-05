package yy.daniel.yyplayerforandroid.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class DecoderSelectionPolicyTest {
    private data class Codec(val name: String, val hardware: Boolean, val software: Boolean)

    private val candidates = listOf(
        Codec("software-first", hardware = false, software = true),
        Codec("hardware", hardware = true, software = false),
        Codec("software-second", hardware = false, software = true),
    )

    private fun select(mode: DecoderMode) = DecoderSelectionPolicy.select(
        candidates,
        mode,
        isHardwareAccelerated = Codec::hardware,
        isSoftwareOnly = Codec::software,
    ).map(Codec::name)

    @Test
    fun automaticModePreservesPlatformCodecOrder() {
        assertEquals(listOf("software-first", "hardware", "software-second"), select(DecoderMode.AUTO))
    }

    @Test
    fun hardwarePreferenceMovesHardwareFirstWithoutDroppingFallbacks() {
        assertEquals(listOf("hardware", "software-first", "software-second"), select(DecoderMode.PREFER_HARDWARE))
    }

    @Test
    fun strictHardwareNeverFallsBackToSoftware() {
        assertEquals(listOf("hardware"), select(DecoderMode.STRICT_HARDWARE))
    }

    @Test
    fun softwareModeOnlyOffersSoftwareCodecs() {
        assertEquals(listOf("software-first", "software-second"), select(DecoderMode.SOFTWARE))
    }

    @Test
    fun recognizesKnownPlatformSoftwareCodecsWhenDeviceMetadataIsIncomplete() {
        val deviceCodec = Codec("OMX.google.h264.decoder", hardware = false, software = false)
        val selected = DecoderSelectionPolicy.select(
            listOf(deviceCodec),
            DecoderMode.SOFTWARE,
            isHardwareAccelerated = Codec::hardware,
            isSoftwareOnly = { it.software || DecoderSelectionPolicy.isKnownPlatformSoftwareCodec(it.name) },
        )
        assertEquals(listOf(deviceCodec), selected)
        assertEquals(true, DecoderSelectionPolicy.isKnownPlatformSoftwareCodec("c2.android.avc.decoder"))
        assertEquals(false, DecoderSelectionPolicy.isKnownPlatformSoftwareCodec("OMX.qcom.video.decoder.avc"))
    }
}
