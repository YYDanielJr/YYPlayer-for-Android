package yy.daniel.yyplayerforandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import yy.daniel.yyplayerforandroid.audio.ActualSharing
import yy.daniel.yyplayerforandroid.audio.AudioOutputChannel
import yy.daniel.yyplayerforandroid.audio.BitPerfectStatus
import yy.daniel.yyplayerforandroid.audio.MixerProfile
import yy.daniel.yyplayerforandroid.audio.NativeAudioCore
import yy.daniel.yyplayerforandroid.audio.ProcessingState
import yy.daniel.yyplayerforandroid.audio.SampleEncoding
import yy.daniel.yyplayerforandroid.audio.SharingRequest
import yy.daniel.yyplayerforandroid.audio.SinkKind
import yy.daniel.yyplayerforandroid.audio.SourceFormat
import yy.daniel.yyplayerforandroid.audio.TransparencyRequest
import yy.daniel.yyplayerforandroid.audio.json.JsonValue

/**
 * Locks the strings that the Kotlin side sends across the JNI boundary.
 *
 * The native core reads these exact member names and enum spellings; a rename on one side without
 * the other silently turns every decision into a rejection, so the wire format is asserted here
 * where it runs on the host in milliseconds instead of only on a device.
 */
class AudioPolicyWireTest {

    private fun usbRoute(bitPerfect: Boolean = true): AudioOutputChannel {
        val profile = MixerProfile(
            encoding = SampleEncoding.S24_IN32,
            sampleRate = 48000,
            channelMask = 0x3,
            bitPerfect = bitPerfect,
            mixesWithOthers = !bitPerfect,
        )
        return AudioOutputChannel(
            id = 42,
            type = 1000,
            typeLabel = "USB 音频设备",
            productName = "Test DAC",
            address = "port1",
            isUsb = true,
            sampleRates = listOf(48000, 96000),
            channelCounts = listOf(2),
            encodings = listOf(4),
            sink = SinkKind.AUDIO_TRACK_SYSTEM,
            bitPerfectProfiles = if (bitPerfect) listOf(profile) else emptyList(),
            supportedProfiles = if (bitPerfect) emptyList() else listOf(profile),
            exclusivePlausible = false,
            canReportActualSharing = false,
        )
    }

    @Test
    fun requestDocumentUsesTheMemberNamesTheCoreReads() {
        val request = NativeAudioCore.buildRequestForTest(
            source = SourceFormat(48000, 2, SampleEncoding.S24_IN32),
            route = usbRoute(),
            sharing = SharingRequest.PREFER_EXCLUSIVE,
            transparency = TransparencyRequest.PREFER_UNPROCESSED,
            processing = ProcessingState(softwareVolume = true),
            routeSelected = true,
        )
        // buildRequestForTest already returns the complete document.
        val document = JsonValue.parse(request)
        assertTrue("request must be valid JSON: ${JsonValue.lastParseError}", document != null)
        val root = document!!
        assertEquals(48000L, document.int("format.sampleRate"))
        assertEquals(2L, document.int("format.channels"))
        assertEquals("s24_in32", document.string("format.encoding"))
        assertEquals(3L, document.int("format.channelMask"))
        assertEquals("prefer_exclusive", document.string("sharing"))
        assertEquals("prefer_unprocessed", document.string("transparency"))
        assertEquals(true, document.bool("routeSelected"))
        assertEquals(true, document.bool("processing.softwareVolume"))
        assertEquals(42L, document.int("route.deviceId"))
        assertEquals("audiotrack_system", document.string("route.sink"))
        assertEquals(true, document.bool("route.supportsBitPerfect"))
        assertEquals(false, document.bool("route.supportsExclusive"))
        assertEquals(false, document.bool("route.canReportActualSharing"))
        val profiles = document.array("route.profiles")
        assertEquals(1, profiles?.size)
        val profile = profiles!![0]
        assertEquals("s24_in32", profile.string("encoding"))
        assertEquals(48000L, profile.int("sampleRate"))
        assertEquals(3L, profile.int("channelMask"))
        assertEquals(true, profile.bool("bitPerfect"))
        assertEquals(false, profile.bool("mixesWithOthers"))
    }

    @Test
    fun responseDocumentParsesIntoADecision() {
        // The exact shape yy_jni.cpp returns, so a change on either side fails loudly.
        val response = "{\"accepted\":true,\"reasonCode\":\"ok\",\"reasonText\":\"可以按请求输出\"," +
            "\"sink\":\"audiotrack_system\",\"sampleRate\":48000,\"channels\":2,\"encoding\":\"s24_in32\"," +
            "\"channelMask\":3,\"effectiveBits\":24,\"requestedSharing\":\"shared\"," +
            "\"expectedSharing\":\"shared\",\"requestBitPerfect\":true,\"pristineExpected\":true," +
            "\"sameRouteFallback\":false,\"bitPerfect\":\"requested\"}"
        val decision = NativeAudioCore.parseDecisionForTest(response)
        assertTrue(decision.accepted)
        assertEquals("ok", decision.reasonCode)
        assertEquals("可以按请求输出", decision.reasonText)
        assertEquals(SinkKind.AUDIO_TRACK_SYSTEM, decision.sink)
        assertEquals(48000, decision.format?.sampleRate)
        assertEquals(2, decision.format?.channelCount)
        assertEquals(SampleEncoding.S24_IN32, decision.format?.encoding)
        assertEquals(SharingRequest.SHARED, decision.requestedSharing)
        assertEquals(ActualSharing.SHARED, decision.expectedSharing)
        assertTrue(decision.requestBitPerfect)
        assertTrue(decision.pristineExpected)
        assertFalse(decision.sameRouteFallback)
        assertEquals(BitPerfectStatus.REQUESTED, decision.bitPerfect)
    }

    @Test
    fun aRejectionCarriesItsReasonThrough() {
        val response = "{\"accepted\":false,\"reasonCode\":\"strict_unprocessed_dsp_active\"," +
            "\"reasonText\":\"当前启用了软件音量\",\"sink\":\"unknown\",\"sampleRate\":0,\"channels\":0," +
            "\"encoding\":\"unknown\",\"channelMask\":0,\"effectiveBits\":0," +
            "\"requestedSharing\":\"shared\",\"expectedSharing\":\"unknown\",\"requestBitPerfect\":false," +
            "\"pristineExpected\":false,\"sameRouteFallback\":false,\"bitPerfect\":\"unknown\"}"
        val decision = NativeAudioCore.parseDecisionForTest(response)
        assertFalse(decision.accepted)
        assertEquals("strict_unprocessed_dsp_active", decision.reasonCode)
        assertEquals(null, decision.format)
    }
}
