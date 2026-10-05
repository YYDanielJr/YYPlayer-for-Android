package yy.daniel.yyplayerforandroid

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import yy.daniel.yyplayerforandroid.audio.AudioDeviceProbe
import yy.daniel.yyplayerforandroid.audio.AudioOutputChannel
import yy.daniel.yyplayerforandroid.audio.MixerProfile
import yy.daniel.yyplayerforandroid.audio.NativeAudioCore
import yy.daniel.yyplayerforandroid.audio.ProcessingState
import yy.daniel.yyplayerforandroid.audio.SampleEncoding
import yy.daniel.yyplayerforandroid.audio.SharingRequest
import yy.daniel.yyplayerforandroid.audio.SinkKind
import yy.daniel.yyplayerforandroid.audio.SourceFormat
import yy.daniel.yyplayerforandroid.audio.TransparencyRequest

/**
 * Device evidence for the audio output path.
 *
 * Two things are asserted here. First, the portable core loads and answers on a real device with the
 * shipping ABI. Second, the device's actual output capabilities are recorded as they are: on a phone
 * with no USB DAC attached there is no bit-perfect contract, and the test requires the code to say
 * so instead of inventing one.
 */
@RunWith(AndroidJUnit4::class)
class AudioOutputDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val manager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Test
    fun nativeCoreLoadsAndReportsItsVersion() {
        assertTrue("native core must be loadable on ${Build.SUPPORTED_ABIS.first()}", NativeAudioCore.available)
        // Recorded once so the run proves the shipping ABI actually executed the bridge.
        android.util.Log.i("YYPlayerAudio", "YYPLAYER_CORE_VERSION=${NativeAudioCore.version()}")
    }

    @Test
    fun bridgeAnswersAPlainPolicyRequest() {
        // Exercises the decision path end to end on the device and records both the request that was
        // sent and the answer, so a wire-format mismatch is visible instead of only "native_error".
        val source = SourceFormat(44100, 2, SampleEncoding.S16)
        val decision = NativeAudioCore.decide(
            source = source,
            route = null,
            sharing = SharingRequest.SHARED,
            transparency = TransparencyRequest.STRICT_UNPROCESSED,
            processing = ProcessingState(softwareVolume = true),
        )
        val request = NativeAudioCore.buildRequestForTest(
            source = source,
            route = null,
            sharing = SharingRequest.SHARED,
            transparency = TransparencyRequest.STRICT_UNPROCESSED,
            processing = ProcessingState(softwareVolume = true),
            routeSelected = false,
        )
        android.util.Log.i(
            "YYPlayerAudio",
            "YYPLAYER_POLICY request=$request accepted=${decision.accepted} reason=${decision.reasonCode}",
        )
        assertFalse("software volume cannot be claimed as original output", decision.accepted)
        assertTrue(
            "expected a strict-policy refusal, got ${decision.reasonCode}",
            decision.reasonCode.startsWith("strict_unprocessed") || decision.reasonCode == "no_route_selected",
        )
    }

    /** A route that reports formats but no bit-perfect contract, as any non-USB output does. */
    private fun plainRoute(): AudioOutputChannel = AudioOutputChannel(
        id = 99,
        type = 2,
        typeLabel = "内置扬声器",
        productName = "Built-in speaker",
        address = "",
        isUsb = false,
        sampleRates = listOf(48000),
        channelCounts = listOf(2),
        encodings = listOf(1),
        sink = SinkKind.AUDIO_TRACK_SYSTEM,
        bitPerfectProfiles = emptyList(),
        supportedProfiles = listOf(
            MixerProfile(SampleEncoding.S16, 48000, 0x3, bitPerfect = false, mixesWithOthers = true),
        ),
        exclusivePlausible = false,
        canReportActualSharing = false,
    )

    @Test
    fun policyRefusesStrictUnprocessedWithoutABitPerfectContract() {
        // The core must refuse to promise original samples rather than open a converted stream.
        val decision = NativeAudioCore.decide(
            source = SourceFormat(44100, 2, SampleEncoding.S16),
            route = plainRoute(),
            sharing = SharingRequest.SHARED,
            transparency = TransparencyRequest.STRICT_UNPROCESSED,
            processing = ProcessingState(),
        )
        assertFalse("no route can promise original samples without a contract", decision.accepted)
        assertTrue(
            "expected a strict-transparency refusal, got ${decision.reasonCode}",
            decision.reasonCode.startsWith("strict_unprocessed"),
        )
    }

    @Test
    fun softwareVolumeIsRefusedForStrictUnprocessedOutput() {
        // Software volume changes the samples before they reach the sink, so a strict request has to
        // be refused rather than silently disabling the user's volume setting.
        val decision = NativeAudioCore.decide(
            source = SourceFormat(44100, 2, SampleEncoding.S16),
            route = plainRoute(),
            sharing = SharingRequest.SHARED,
            transparency = TransparencyRequest.STRICT_UNPROCESSED,
            processing = ProcessingState(softwareVolume = true),
        )
        assertFalse("software volume changes the samples", decision.accepted)
        assertEquals("strict_unprocessed_dsp_active", decision.reasonCode)
    }

    @Test
    fun strictExclusiveIsRefusedOnTheSystemAudioTrackPath() {
        // AudioTrack never exposes stream exclusivity, and a bit-perfect mixer contract is a
        // different promise, so the request must be refused instead of reported as exclusive.
        val decision = NativeAudioCore.decide(
            source = SourceFormat(48000, 2, SampleEncoding.S16),
            route = plainRoute(),
            sharing = SharingRequest.STRICT_EXCLUSIVE,
            transparency = TransparencyRequest.COMPATIBLE,
            processing = ProcessingState(),
        )
        assertFalse(decision.accepted)
        assertEquals("exclusive_not_proven_by_audiotrack", decision.reasonCode)
    }

    @Test
    fun deviceCapabilitiesAreRecordedWithoutInventingBitPerfect() {
        val channels = AudioDeviceProbe.channels(manager)
        assertTrue("the system must report at least one output device", channels.isNotEmpty())
        val report = StringBuilder()
        report.append("sdk=").append(Build.VERSION.SDK_INT)
            .append(" release=").append(Build.VERSION.RELEASE)
            .append(" model=").append(Build.MODEL)
            .append('\n')
        channels.forEach { channel ->
            report.append("- ").append(channel.productName)
                .append(" [").append(channel.typeLabel).append(']')
                .append(" id=").append(channel.id)
                .append(" usb=").append(channel.isUsb)
                .append(" rates=").append(channel.sampleRates)
                .append(" bitPerfectProfiles=").append(channel.bitPerfectProfiles.size)
                .append('\n')
            // The platform only allows preferred mixer attributes for USB devices, so a non-USB
            // output can never carry a bit-perfect contract and must not report one.
            if (!channel.isUsb) {
                assertTrue(
                    "${channel.productName} is not USB, so it cannot have a mixer-attribute contract",
                    channel.bitPerfectProfiles.isEmpty(),
                )
            }
        }
        val usbChannels = channels.filter { it.isUsb }
        report.append("usbOutputs=").append(usbChannels.size).append('\n')
        if (Build.VERSION.SDK_INT < 34) {
            report.append("mixerAttributes=unsupported (API < 34)\n")
            usbChannels.forEach { assertTrue(it.bitPerfectProfiles.isEmpty()) }
        } else {
            report.append("mixerAttributes=queried\n")
        }
        // Recorded so the run leaves evidence of what this device actually reported.
        println("YYPLAYER_AUDIO_OUTPUT_REPORT_BEGIN\n$report" + "YYPLAYER_AUDIO_OUTPUT_REPORT_END")
    }

    @Test
    fun strictUnprocessedIsRefusedForANonBitPerfectUsbRoute() {
        // Synthesised USB route that reports profiles without a bit-perfect contract: the core must
        // refuse the strict request rather than open a converted (non-original) stream.
        val channels = AudioDeviceProbe.channels(manager)
        val usb = channels.firstOrNull { it.isUsb }
        if (usb == null) {
            // No USB audio device attached: nothing to assert, and the report above already shows it.
            return
        }
        val source = SourceFormat(44100, 2, SampleEncoding.S16)
        val decision = NativeAudioCore.decide(
            source = source,
            route = usb,
            sharing = SharingRequest.SHARED,
            transparency = TransparencyRequest.STRICT_UNPROCESSED,
            processing = ProcessingState(),
        )
        if (!usb.hasBitPerfectContract) {
            assertFalse(decision.accepted)
            assertTrue(
                "unexpected reason ${decision.reasonCode}",
                decision.reasonCode.startsWith("strict_unprocessed"),
            )
        }
    }
}
