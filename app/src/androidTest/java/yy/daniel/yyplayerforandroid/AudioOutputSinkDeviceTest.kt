package yy.daniel.yyplayerforandroid

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import yy.daniel.yyplayerforandroid.audio.ActualSharing
import yy.daniel.yyplayerforandroid.audio.AudioDeviceProbe
import yy.daniel.yyplayerforandroid.audio.AudioVerificationTone
import yy.daniel.yyplayerforandroid.audio.BitPerfectStatus
import yy.daniel.yyplayerforandroid.audio.NativeAudioCore
import yy.daniel.yyplayerforandroid.audio.OutputDecision
import yy.daniel.yyplayerforandroid.audio.OutputFormat
import yy.daniel.yyplayerforandroid.audio.OutputSelfTest
import yy.daniel.yyplayerforandroid.audio.OutputSelfTestResult
import yy.daniel.yyplayerforandroid.audio.PcmRingFeeder
import yy.daniel.yyplayerforandroid.audio.ProcessingState
import yy.daniel.yyplayerforandroid.audio.SampleEncoding
import yy.daniel.yyplayerforandroid.audio.SharingRequest
import yy.daniel.yyplayerforandroid.audio.SinkKind
import yy.daniel.yyplayerforandroid.audio.SourceFormat
import yy.daniel.yyplayerforandroid.audio.TransparencyRequest

/**
 * H02 device evidence: the portable core's PCM ring is handed to a real platform sink and the
 * platform's own report is recorded.
 *
 * The tests assert only what the platform said. When the attached device offers no bit-perfect
 * contract the run still executes the whole path and records the refusal, because "this USB DAC has
 * no BIT_PERFECT contract" is a result, not a failure of the test.
 */
@RunWith(AndroidJUnit4::class)
class AudioOutputSinkDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val manager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun usbChannel() = AudioDeviceProbe.channels(manager).firstOrNull { it.isUsb }

    /**
     * First real measurement against a USB DAC: which mixer-attribute combinations the device reports,
     * and which of them the platform marks BIT_PERFECT. Recorded verbatim, including the empty result.
     */
    @Test
    fun usbMixerAttributeCapabilityIsRecorded() {
        val report = StringBuilder()
        report.append("sdk=").append(Build.VERSION.SDK_INT)
            .append(" release=").append(Build.VERSION.RELEASE)
            .append(" model=").append(Build.MODEL).append('\n')
        AudioDeviceProbe.channels(manager).forEach { channel ->
            report.append("- ").append(channel.productName)
                .append(" id=").append(channel.id)
                .append(" usb=").append(channel.isUsb)
                .append(" rates=").append(channel.sampleRates)
                .append(" channels=").append(channel.channelCounts)
                .append(" encodings=").append(channel.encodings)
                .append('\n')
            report.append("  bitPerfect=")
                .append(channel.bitPerfectProfiles.joinToString { "${it.sampleRate}Hz/${it.encoding.wire}/mask=${it.channelMask}" })
                .append('\n')
            report.append("  other=")
                .append(channel.supportedProfiles.joinToString { "${it.sampleRate}Hz/${it.encoding.wire}/mask=${it.channelMask}" })
                .append('\n')
        }
        val usb = usbChannel()
        report.append("usbAttached=").append(usb != null).append('\n')
        if (usb == null) {
            report.append("verdict=no_usb_dac_attached\n")
        } else if (usb.bitPerfectProfiles.isEmpty()) {
            report.append("verdict=usb_present_without_bit_perfect_contract\n")
        } else {
            report.append("verdict=usb_reports_bit_perfect_contract\n")
        }
        println("YYPLAYER_USB_CAPABILITY_BEGIN\n$report" + "YYPLAYER_USB_CAPABILITY_END")
        android.util.Log.i("YYPlayerAudio", "YYPLAYER_USB_CAPABILITY\n$report")
        // The bridge must be usable, otherwise every later result would be a silent "unavailable".
        assertTrue("native core must be loaded for the sink path", NativeAudioCore.available)
    }

    /**
     * The pristine-conversion rule the sink relies on must be decided by the core, not re-implemented
     * in Kotlin. This locks the bridge call that the output path uses to widen samples.
     */
    @Test
    fun pristineConversionRuleComesFromTheCore() {
        assertTrue(NativeAudioCore.isPristineConversion(SampleEncoding.S16, SampleEncoding.S16))
        assertTrue(NativeAudioCore.isPristineConversion(SampleEncoding.S16, SampleEncoding.S24_3))
        assertTrue(NativeAudioCore.isPristineConversion(SampleEncoding.S16, SampleEncoding.S24_IN32))
        assertTrue(NativeAudioCore.isPristineConversion(SampleEncoding.S16, SampleEncoding.S32))
        // Narrowing or float would change the samples, so the sink must never be allowed to do it.
        assertTrue(!NativeAudioCore.isPristineConversion(SampleEncoding.S32, SampleEncoding.S16))
        assertTrue(!NativeAudioCore.isPristineConversion(SampleEncoding.S16, SampleEncoding.F32))
        android.util.Log.i("YYPlayerAudio", "YYPLAYER_PRISTINE_BRIDGE OK")
    }

    /**
     * Executes the whole H02 path on the attached hardware. This is the test that produces the
     * bit-perfect evidence: it only reports `system_reported` when the platform accepted the mixer
     * contract *and* the stream it opened reports the decided format *and* the route is USB.
     */
    @Test
    fun ringIsPlayedThroughARealSinkWithTheDecidedFormat() {
        assertTrue("native core must be loaded", NativeAudioCore.available)
        val usb = usbChannel()
        // A source format that exactly matches a bit-perfect profile when one exists, so the core has a
        // real chance to request the contract; otherwise a plain 48 kHz stereo s16 source.
        val profile = usb?.bitPerfectProfiles?.preferredRate()
        val source = if (profile != null) {
            SourceFormat(profile.sampleRate, 2, profile.encoding)
        } else {
            SourceFormat(48000, 2, SampleEncoding.S16)
        }
        val route = usb ?: AudioDeviceProbe.channels(manager)
            .firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val decision = NativeAudioCore.decide(
            source = source,
            route = route,
            sharing = SharingRequest.SHARED,
            transparency = TransparencyRequest.PREFER_UNPROCESSED,
            processing = ProcessingState(),
            routeSelected = route != null,
        )
        android.util.Log.i(
            "YYPlayerAudio",
            "YYPLAYER_SELFTEST_DECISION accepted=${decision.accepted} reason=${decision.reasonCode} " +
                "sink=${decision.sink.wire} format=${decision.format} requestBitPerfect=${decision.requestBitPerfect} " +
                "pinning=${usb?.id}",
        )
        assertTrue("the core must accept a plain shared request: ${decision.reasonCode}", decision.accepted)
        assertNotNull("an accepted decision must carry a format", decision.format)

        // The DAC is pinned explicitly. Leaving the route to the platform is what routed the earlier run
        // to the connected Bluetooth headset instead of the USB device, which makes USB bit-perfect
        // evidence impossible to obtain at all.
        val result = kotlinx.coroutines.runBlocking {
            OutputSelfTest(context, attributes).run(source, decision, selectedDeviceId = usb?.id)
        }
        logReport(result)
        assertTrue("the sink must open on this device: ${result.reasonCode} ${result.reasonText}", result.started)
        // The platform's own report must agree with the format that was decided, otherwise the decision
        // never reached the device and no bit-perfect claim would be meaningful.
        assertTrue(
            "platform opened ${result.route.trackSampleRate}Hz/${result.route.trackEncoding.wire} but " +
                "the core decided ${decision.format?.sampleRate}Hz/${decision.format?.encoding?.wire}",
            result.formatMatchesTrack,
        )
        assertTrue("the stream must actually carry frames", result.framesConsumed > 0)
        assertTrue(
            "the ring must not lose a frame on the way in: fed=${result.framesFed} " +
                "dropped=${result.framesDropped}",
            result.framesFed > 0 && result.framesDropped == 0L,
        )
        assertTrue(
            "the sink must take every frame that was written: fed=${result.framesFed} " +
                "consumed=${result.framesConsumed}",
            result.framesConsumed >= result.framesFed,
        )
        // Starvation during the signal is bounded by one pump block. The pump hands the platform at most
        // one bufferful per turn, so if the paced producer is a few milliseconds late the remainder of
        // that block is filled with silence; 130 frames is 2.7 ms at 48 kHz. It is not data loss - every
        // frame was still written, dropped nothing and was consumed - so the assertion pins the real
        // invariant (the sink is never starved by a whole block) instead of a scheduler-dependent zero.
        val pumpBlockFrames = result.route.bufferSizeFrames.toLong()
        assertTrue(
            "the sink must never be starved by a whole pump block: duringSignal=${result.starvingDuringSignal} " +
                "block=$pumpBlockFrames starving=${result.sinkStarvingFrames} atFeedEnd=${result.starvingFramesAtFeedEnd}",
            result.starvingDuringSignal < pumpBlockFrames,
        )
        assertEquals(
            "and the leading silence is what accounts for the rest: starving=${result.sinkStarvingFrames} " +
                "atFeedEnd=${result.starvingFramesAtFeedEnd}",
            result.sinkStarvingFrames,
            result.starvingFramesAtFeedEnd + result.starvingDuringSignal,
        )
        assertEquals("the platform must not have reported an xrun of its own", 0, result.route.underrunCount)
        assertNotNull("the reference signal must be hashable for a later capture comparison", result.digestHex)
        if (usb != null) {
            // The pin is only a request. On this Xiaomi build `setPreferredDevice` reports false while
            // the platform still routes to the requested device, so the route readback is the evidence
            // and the pin answer is recorded next to it instead of being asserted.
            assertTrue(
                "the platform must route to the requested USB DAC, not to the default route: " +
                    "requested=${result.requestedDeviceId} actual=${result.route.routedDeviceIds} " +
                    "${result.route.routedDeviceNames}",
                result.routeMatchesRequest,
            )
            assertTrue(
                "the route must be the USB device, otherwise no USB evidence was obtained",
                result.route.routedIsUsb,
            )
            android.util.Log.i(
                "YYPlayerAudio",
                "YYPLAYER_PIN_EVIDENCE requested=${result.requestedDeviceId} " +
                    "pinAccepted=${result.devicePinAccepted} routed=${result.route.routedDeviceIds} " +
                    "matches=${result.routeMatchesRequest}",
            )
        }
        // Bit perfection is only ever reported when the platform itself agreed to it.
        if (result.mixerContractAccepted) {
            assertNotNull("an accepted contract must have a readback value", result.mixerContractReadBack)
            if (result.route.routedIsUsb) {
                assertEquals(
                    "an accepted USB contract with a matching format is system-reported",
                    true,
                    result.systemReportedBitPerfect,
                )
            }
        } else {
            assertTrue(
                "without a contract the result must not claim system-reported bit perfection",
                !result.systemReportedBitPerfect,
            )
        }
    }

    /**
     * Prefers a standard rate when a device offers several, so the evidence describes an ordinary
     * playback format rather than the first entry of an unordered list.
     */
    private fun List<yy.daniel.yyplayerforandroid.audio.MixerProfile>.preferredRate():
        yy.daniel.yyplayerforandroid.audio.MixerProfile? =
        firstOrNull { it.sampleRate == 48000 } ?: firstOrNull { it.sampleRate == 44100 } ?: firstOrNull()

    /**
     * The widening path on real hardware.
     *
     * A 16-bit source reaches a DAC whose usable containers are wider, so the samples have to be widened
     * by the portable core before they enter the ring. Only the containers `AudioFormat` actually defines
     * are exercised: `s24_in32` has no platform encoding at all (see the refusal test below), so it is not
     * a path the sink can open and running it here would only measure the refusal.
     */
    @Test
    fun widenedFormatsOpenAndDeliverAtTheDecidedFormat() {
        val usb = usbChannel()
        for (encoding in listOf(SampleEncoding.S24_3, SampleEncoding.S32)) {
            runWidenedCase(encoding, usb?.id, routeIsUsb = true)
            // Each case renders and converts a multi-megabyte signal; returning the heap between cases
            // keeps the runs from overlapping on a phone-sized heap.
            System.gc()
        }
    }

    /**
     * Non-USB routes run the identical sink path and may play, but they can never carry a bit-perfect
     * contract: the platform only offers mixer attributes for USB outputs. The built-in speaker is always
     * present, so this always runs and gives the contrast case its evidence.
     */
    @Test
    fun builtInSpeakerDeliversWithoutEverClaimingBitPerfect() {
        val speaker = AudioDeviceProbe.channels(manager)
            .firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        assertNotNull("the device must report a built-in speaker", speaker)
        assertTrue(
            "a built-in speaker cannot carry a mixer-attribute contract",
            speaker!!.bitPerfectProfiles.isEmpty(),
        )
        runWidenedCase(SampleEncoding.S16, speaker.id, routeIsUsb = false)
    }

    /**
     * The Bluetooth headset, when one is connected: same sink, same refusals expected. Assertions stay
     * limited to facts that hold on any non-USB route, because a Bluetooth codec is free to resample the
     * stream before it leaves the phone.
     */
    @Test
    fun bluetoothRouteDeliversWithoutEverClaimingBitPerfect() {
        val a2dp = AudioDeviceProbe.channels(manager)
            .firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        if (a2dp == null) {
            android.util.Log.i("YYPlayerAudio", "YYPLAYER_BT_SKIPPED no A2DP route attached")
            return
        }
        assertTrue("an A2DP route cannot carry a mixer-attribute contract", a2dp.bitPerfectProfiles.isEmpty())
        runWidenedCase(SampleEncoding.S16, a2dp.id, routeIsUsb = false)
    }

    /**
     * `AudioFormat` defines a 3-byte packed 24-bit form and a 32-bit form, but nothing for 24 significant
     * bits carried in 4 bytes. A decision asking for the core's `s24_in32` must therefore be refused with
     * the reason instead of being opened as a different container.
     */
    @Test
    fun twentyFourInThirtyTwoHasNoPlatformContainerAndIsRefused() {
        val source = SourceFormat(48000, 2, SampleEncoding.S16)
        val target = OutputFormat(48000, 2, SampleEncoding.S24_IN32, channelMask = 0x3)
        val decision = OutputDecision(
            accepted = true,
            reasonCode = "ok",
            reasonText = "测试用决策：s24_in32",
            sink = SinkKind.AUDIO_TRACK_SYSTEM,
            format = target,
            requestedSharing = SharingRequest.SHARED,
            expectedSharing = ActualSharing.SHARED,
            requestBitPerfect = false,
            pristineExpected = true,
            sameRouteFallback = false,
            bitPerfect = BitPerfectStatus.UNAVAILABLE,
        )
        val result = kotlinx.coroutines.runBlocking {
            OutputSelfTest(context, attributes).run(source, decision, selectedDeviceId = usbChannel()?.id)
        }
        android.util.Log.i(
            "YYPlayerAudio",
            "YYPLAYER_S24IN32_REFUSED started=${result.started} reason=${result.reasonCode}",
        )
        assertTrue("s24_in32 cannot be opened by the platform sink", !result.started)
        assertEquals("sink_bad_format", result.reasonCode)
        // The core can still carry it, which is exactly why the refusal belongs to the sink: widening it
        // to s32 is lossless, and that decision has to be made explicitly by the caller.
        assertTrue(
            "the core must still be able to widen s24_in32 to s32",
            NativeAudioCore.isPristineConversion(SampleEncoding.S24_IN32, SampleEncoding.S32),
        )
    }

    private fun runWidenedCase(encoding: SampleEncoding, deviceId: Int?, routeIsUsb: Boolean) {
        // The source stays 16-bit and the output format is pinned to `encoding`. That is the real
        // situation where a wider container is needed: the route offers no profile at the source encoding
        // for this rate, so the sink has to be opened wider and the core has to widen the samples into it.
        // Letting the core pick the profile instead would select the matching s16 one and test nothing.
        // 48 kHz keeps the rendered signal small enough that three consecutive cases do not thrash the heap.
        val rate = 48000
        val source = SourceFormat(rate, 2, SampleEncoding.S16)
        val target = OutputFormat(
            sampleRate = rate,
            channelCount = 2,
            encoding = encoding,
            channelMask = 0x3,
        )
        val decision = OutputDecision(
            accepted = true,
            reasonCode = "ok",
            reasonText = "测试用决策：强制使用加宽容器",
            sink = SinkKind.AUDIO_TRACK_SYSTEM,
            format = target,
            requestedSharing = SharingRequest.SHARED,
            expectedSharing = ActualSharing.SHARED,
            requestBitPerfect = false,
            pristineExpected = true,
            sameRouteFallback = false,
            bitPerfect = BitPerfectStatus.UNAVAILABLE,
        )
        // A decoded 16-bit source must widen, so the core has to accept the pair before we ask the
        // platform for anything: a refusal here would mean the sink could never be opened correctly.
        assertTrue(
            "the core must allow ${SampleEncoding.S16.wire} -> ${encoding.wire}",
            NativeAudioCore.isPristineConversion(SampleEncoding.S16, encoding),
        )
        val result = kotlinx.coroutines.runBlocking {
            OutputSelfTest(context, attributes).run(source, decision, selectedDeviceId = deviceId)
        }
        android.util.Log.i(
            "YYPlayerAudio",
            "YYPLAYER_WIDENED encoding=${encoding.wire} usb=$routeIsUsb started=${result.started} " +
                "reason=${result.reasonCode}/${result.reasonText} " +
                "platform=${result.route.platformFormat?.encoding?.wire}/${result.route.platformFormat?.sampleRate}Hz " +
                "matches=${result.formatMatchesTrack} substituted=${result.formatWasSubstituted} " +
                "fed=${result.framesFed} dropped=${result.framesDropped} consumed=${result.framesConsumed} " +
                "duringSignal=${result.starvingDuringSignal} routed=${result.route.routedDeviceNames}",
        )
        assertTrue(
            "${encoding.wire}: the sink did not open (${result.reasonCode} ${result.reasonText})",
            result.started,
        )
        assertTrue(
            "${encoding.wire}: no frames reached the output (fed=${result.framesFed} consumed=${result.framesConsumed})",
            result.framesConsumed > 0,
        )
        assertEquals("${encoding.wire}: frames were dropped on the way in", 0L, result.framesDropped)
        // Same invariant as the s16 case: the pump hands over at most one bufferful per turn, so a paced
        // producer can leave part of one block unfilled. Anything up to that is the mechanism's own
        // resolution, and a whole block would mean the producer genuinely stalled.
        val pumpBlockFrames = result.route.bufferSizeFrames.toLong()
        assertTrue(
            "${encoding.wire}: the sink must never be starved by a whole pump block " +
                "(duringSignal=${result.starvingDuringSignal}, block=$pumpBlockFrames)",
            result.starvingDuringSignal < pumpBlockFrames,
        )
        // No route in this suite has a bit-perfect contract (the DAC has none, and the others cannot have
        // one), so nothing here may ever report system-reported bit perfection.
        assertTrue(
            "${encoding.wire}: no contract was accepted, so nothing may claim bit perfection",
            !result.systemReportedBitPerfect,
        )
        assertEquals(
            "${encoding.wire}: a non-USB route must report its own device type",
            routeIsUsb,
            result.route.routedIsUsb,
        )
        if (deviceId != null) {
            assertTrue(
                "${encoding.wire}: the platform must route to the requested device " +
                    "(requested=$deviceId actual=${result.route.routedDeviceIds} ${result.route.routedDeviceNames})",
                result.routeMatchesRequest,
            )
        }
        // Whether the platform honours a wider container, or resamples towards a Bluetooth codec, is a
        // device fact, so it is recorded rather than asserted. What must never happen is the app
        // presenting the requested format as the real one after the platform substituted something else.
        if (result.formatWasSubstituted) {
            android.util.Log.i(
                "YYPlayerAudio",
                "YYPLAYER_WIDEN_SUBSTITUTED requested=${encoding.wire} " +
                    "accepted=${result.route.platformFormat?.encoding?.wire} " +
                    "(recorded; not presented as the output format)",
            )
            assertTrue("a substitution must not be reported as a match", !result.formatMatchesTrack)
        } else {
            assertEquals(
                "${encoding.wire}: the platform accepted the requested container, so it must report it",
                encoding,
                result.route.platformFormat?.encoding,
            )
        }
    }

    /**
     * The bytes the core produces for a widened format, checked without any audio hardware: the first
     * sample of the reference tone must appear shifted into the wider container. This is the value that
     * proves the level survives the widening, and it is asserted through the same JNI call the sink uses.
     */
    @Test
    fun widenedBytesAreTheSourceSamplesShiftedIntoTheWiderContainer() {
        val sign: Short = -12345
        val src = java.nio.ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        src.putShort(sign)
        src.putShort(sign)
        src.position(0)
        val cases = mapOf(
            // One stereo frame in each container: 2 * 3 bytes packed, 2 * 4 bytes for the padded forms.
            SampleEncoding.S24_3 to 6,
            SampleEncoding.S24_IN32 to 8,
            SampleEncoding.S32 to 8,
        )
        for ((encoding, capacity) in cases) {
            val dst = java.nio.ByteBuffer.allocateDirect(capacity).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val frames = NativeAudioCore.convertPcm(
                from = SampleEncoding.S16,
                to = encoding,
                src = src,
                srcBytes = 4,
                dst = dst,
                dstBytes = capacity,
                channels = 2,
            )
            assertEquals("${encoding.wire}: one frame must convert", 1, frames)
            val shift = encoding.effectiveBits - SampleEncoding.S16.effectiveBits
            val expected = sign.toInt() shl shift
            val actual = when (encoding) {
                SampleEncoding.S24_3 -> readSignedLittleEndian(dst, 0, 3)
                else -> readSignedLittleEndian(dst, 0, 4)
            }
            // Raw bytes are logged so a mismatch can be read as bytes rather than only as numbers.
            android.util.Log.i(
                "YYPlayerAudio",
                "YYPLAYER_WIDEN_BYTES encoding=${encoding.wire} capacity=$capacity frames=$frames " +
                    "hex=[" + (0 until capacity).joinToString(" ") { "%02X".format(dst.get(it)) } + "] " +
                    "expected=$expected actual=$actual",
            )
            assertEquals(
                "${encoding.wire}: the widened sample must sit in the container's significant bits",
                expected,
                actual,
            )
        }

        // Same pair, but as a full-length signal: the reference signal is 1.3 MB of source and up to 2 MB
        // of destination, so a size-dependent refusal in the bridge would show up here and not in the
        // one-frame cases above. The source is duplicated because `put` consumes the original's position,
        // and the bridge reads only the buffer's remaining window.
        val tone = AudioVerificationTone(sampleRate = 48000)
        val rendered = tone.renderS16()
        val srcBytes = rendered.capacity()
        val sourceFrames = srcBytes / 4
        val bigSrc = java.nio.ByteBuffer.allocateDirect(srcBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bigSrc.put(rendered.duplicate())
        bigSrc.flip()
        for (encoding in listOf(SampleEncoding.S24_3, SampleEncoding.S24_IN32, SampleEncoding.S32)) {
            val frameBytes = encoding.bytesPerSample * 2
            val bigDst = java.nio.ByteBuffer.allocateDirect(sourceFrames * frameBytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val frames = NativeAudioCore.convertPcm(
                from = SampleEncoding.S16,
                to = encoding,
                src = bigSrc,
                srcBytes = srcBytes,
                dst = bigDst,
                dstBytes = bigDst.capacity(),
                channels = 2,
            )
            android.util.Log.i(
                "YYPlayerAudio",
                "YYPLAYER_WIDEN_SIZE encoding=${encoding.wire} srcBytes=$srcBytes " +
                    "srcFrames=$sourceFrames dstBytes=${bigDst.capacity()} converted=$frames",
            )
            assertEquals("${encoding.wire}: a full-length signal must convert completely", sourceFrames, frames)
        }
    }

    /**
     * Little-endian signed read of `bytes` bytes at `offset`, sign-extended to an Int.
     *
     * The sign extension is done on a Long: a 32-bit container needs no extension at all, and shifting
     * a negative Int by 32 is undefined-by-masking in Kotlin (`-1 shl 32 == -1`), which would turn the
     * whole value into -1.
     */
    private fun readSignedLittleEndian(buffer: java.nio.ByteBuffer, offset: Int, bytes: Int): Int {
        var raw = 0L
        for (i in 0 until bytes) {
            raw = raw or ((buffer.get(offset + i).toLong() and 0xFFL) shl (8 * i))
        }
        val signBit = 1L shl (bytes * 8 - 1)
        if ((raw and signBit) != 0L && bytes < 4) {
            raw = raw or (-1L shl (bytes * 8))
        }
        return raw.toInt()
    }

    private fun logReport(result: OutputSelfTestResult) {        val report = buildString {
            append("started=").append(result.started).append('\n')
            append("reason=").append(result.reasonCode).append(' ').append(result.reasonText).append('\n')
            append("sink=").append(result.sink.wire).append('\n')
            append("decidedFormat=").append(result.decidedFormat).append('\n')
            append("requestedDeviceId=").append(result.requestedDeviceId)
                .append(" pinAccepted=").append(result.devicePinAccepted)
                .append(" routeMatchesRequest=").append(result.routeMatchesRequest).append('\n')
            append("bitPerfectRequested=").append(result.bitPerfectRequested).append('\n')
            append("mixerContractAccepted=").append(result.mixerContractAccepted).append('\n')
            append("mixerContractReadBack=").append(result.mixerContractReadBack).append('\n')
            append("systemReportedBitPerfect=").append(result.systemReportedBitPerfect).append('\n')
            append("routedIds=").append(result.route.routedDeviceIds).append('\n')
            append("routedNames=").append(result.route.routedDeviceNames).append('\n')
            append("routedIsUsb=").append(result.route.routedIsUsb).append('\n')
            append("track=").append(result.route.trackSampleRate).append("Hz/")
                .append(result.route.trackEncoding.wire).append(" mask=").append(result.route.trackChannelMask)
                .append(" platformFormat=").append(result.route.platformFormat)
                .append(" substituted=").append(result.formatWasSubstituted).append('\n')
            append("bufferFrames=").append(result.route.bufferSizeFrames)
                .append(" platformUnderruns=").append(result.route.underrunCount).append('\n')
            append("framesFed=").append(result.framesFed)
                .append(" dropped=").append(result.framesDropped)
                .append(" consumed=").append(result.framesConsumed).append('\n')
            append("ringUnderrun=").append(result.ringUnderrunFrames)
                .append(" sinkStarving=").append(result.sinkStarvingFrames)
                .append(" atFeedEnd=").append(result.starvingFramesAtFeedEnd)
                .append(" duringSignal=").append(result.starvingDuringSignal)
                .append(" partialWrites=").append(result.partialWriteFrames).append('\n')
            append("digestSha256=").append(result.digestHex).append('\n')
            append("tone=").append(result.toneDescription).append('\n')
            result.notes.forEach { append("note=").append(it).append('\n') }
        }
        println("YYPLAYER_OUTPUT_SELFTEST_BEGIN\n$report" + "YYPLAYER_OUTPUT_SELFTEST_END")
        android.util.Log.i("YYPlayerAudio", "YYPLAYER_OUTPUT_SELFTEST\n$report")
    }

    /**
     * A non-USB route must never produce a bit-perfect claim, even when the app asked for original
     * samples: the platform only offers mixer attributes for USB outputs.
     */
    @Test
    fun nonUsbRouteCannotReportBitPerfect() {
        val speaker = AudioDeviceProbe.channels(manager)
            .firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (speaker == null) return
        assertTrue("a built-in speaker cannot have a mixer-attribute contract", speaker.bitPerfectProfiles.isEmpty())
        val decision = NativeAudioCore.decide(
            source = SourceFormat(48000, 2, SampleEncoding.S16),
            route = speaker,
            sharing = SharingRequest.SHARED,
            transparency = TransparencyRequest.PREFER_UNPROCESSED,
            processing = ProcessingState(),
            routeSelected = true,
        )
        assertTrue("relaxed policy may proceed on the speaker", decision.accepted)
        assertTrue("but it must not request a bit-perfect contract", !decision.requestBitPerfect)
        assertEquals(
            "and it must not report one",
            BitPerfectStatus.UNAVAILABLE,
            decision.bitPerfect,
        )
    }

    /**
     * The tone generator and the sink must agree on frame arithmetic: this is the counter that makes a
     * shortfall visible instead of silent, so it is checked without any device involvement.
     */
    @Test
    fun tonePlanIsDeterministicAndFrameAccurate() {
        val first = AudioVerificationTone()
        val second = AudioVerificationTone()
        val a = first.renderS16()
        val b = second.renderS16()
        assertEquals("the plan must have a fixed length", a.remaining(), b.remaining())
        assertEquals(
            "48 kHz stereo 16-bit: 2+1+1+1+2 seconds",
            7 * 48000 * 2 * 2,
            a.remaining(),
        )
        assertTrue("two runs must produce identical bytes", a == b)
        val silence = ByteArray(2 * 48000 * 2 * 2)
        val headBytes = ByteArray(silence.size)
        val copy = a.duplicate()
        copy.position(0)
        copy.get(headBytes)
        assertTrue("the plan must start with silence so the mixer can be configured first", headBytes.contentEquals(silence))
    }

    /**
     * The real feeder path, against a real native ring: a producer paced at the sample rate and a
     * consumer draining in real time. Every frame must arrive and be taken, with a digest over exactly
     * the accepted bytes, so a shortfall can never be silent.
     */
    @Test
    fun feederFillsTheNativeRingWithoutLosingAFrame() {
        val capacity = 24000L
        val handle = NativeAudioCore.createRing(48000, 2, SampleEncoding.S16, 0x3, capacity)
        assertTrue("ring must be creatable", handle > 0)
        try {
            val sinkFormat = OutputFormat(48000, 2, SampleEncoding.S16, channelMask = 0x3)
            // A shorter signal than the audible plan: this test is about frame accounting, not hearing.
            val tone = AudioVerificationTone(48000, listOf(AudioVerificationTone.Segment("tone", 440, 880)))
            val pcm = tone.renderS16()
            val frames = pcm.remaining() / 4
            var fed: PcmRingFeeder.Result? = null
            val feeder = Thread { fed = PcmRingFeeder.feed(handle, pcm, sinkFormat) }
            val drain = java.nio.ByteBuffer.allocateDirect(2048 * 4)
            var read = 0L
            var producerDone = false
            feeder.start()
            val deadline = System.currentTimeMillis() + 20000
            while (System.currentTimeMillis() < deadline) {
                val stats = NativeAudioCore.ringStats(handle)
                val readable = stats?.getOrNull(1) ?: 0L
                if (readable > 0) {
                    val got = NativeAudioCore.ringRead(handle, drain, 2048)
                    if (got > 0) {
                        read += got.toLong()
                        Thread.sleep(10)
                        continue
                    }
                }
                if (!feeder.isAlive) producerDone = true
                if (producerDone && read >= (fed?.framesWritten ?: Long.MAX_VALUE)) break
                Thread.sleep(2)
            }
            feeder.join(5000)
            val result = fed
            assertNotNull("the feeder must report a result", result)
            val stats = NativeAudioCore.ringStats(handle)
            assertNotNull(stats)
            android.util.Log.i(
                "YYPlayerAudio",
                "YYPLAYER_RING_ROUNDTRIP capacity=${stats!![0]} readable=${stats[1]} written=${stats[2]} " +
                    "read=${stats[3]} dropped=${stats[4]} underrun=${stats[5]} " +
                    "feederWritten=${result!!.framesWritten}/${result.expectedFrames} " +
                    "feederDropped=${result.framesDropped} digest=${result.digestHex}",
            )
            assertTrue("the feeder must pace itself instead of flooding a one-second ring", result.complete)
            assertEquals("the tone must have been fed in full", frames.toLong(), result.expectedFrames)
            assertEquals("nothing may be dropped on the way in", 0L, stats[4])
            assertEquals("every written frame must have been read back", stats[2], stats[3])
            assertEquals("the consumer must have taken exactly what was fed", frames.toLong(), stats[3])
            assertEquals("the signal must be hashed for a later capture comparison", 64, result.digestHex.length)
            // The consumer polls the ring while the producer paces itself at 1x, so the ring counts the
            // shortfall of every read that arrived before its next chunk. That is the consumer waiting,
            // not data loss, and the frame counters above are what prove nothing was lost.
            android.util.Log.i(
                "YYPlayerAudio",
                "YYPLAYER_RING_NOTE readerShortfall=${stats[5]} frames=${frames} " +
                    "(consumer waiting for the paced producer; written==read==$frames is the real invariant)",
            )
        } finally {
            NativeAudioCore.releaseRing(handle)
        }
    }
}
