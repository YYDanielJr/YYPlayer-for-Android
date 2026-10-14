package yy.daniel.yyplayerforandroid

import android.media.AudioFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import yy.daniel.yyplayerforandroid.audio.*

@RunWith(AndroidJUnit4::class)
class OutputAuditDeviceTest {
    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    @Test fun formatChangeDrainsTheOldPausedTailBeforeOpeningTheNextTrack() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val sink = NativeOutputAudioSink(context, { null })
        val raw = androidx.media3.common.Format.Builder().setSampleMimeType("audio/raw")
            .setChannelCount(2).setPcmEncoding(androidx.media3.common.C.ENCODING_PCM_16BIT)
            .setSampleRate(48000).build()
        try {
            sink.configure(raw, 0, null)
            sink.play()
            val startup = ByteBuffer.allocateDirect(4096)
            val startupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!sink.handleBuffer(startup, 0, 1) && System.nanoTime() < startupDeadline) Thread.sleep(10)
            assertEquals(startup.limit(), startup.position())
            sink.pause()
            sink.flush()
            assertTrue(sink.handleBuffer(ByteBuffer.allocateDirect(4096), 0, 1))
            assertTrue(sink.hasPendingData())
            sink.configure(raw.buildUpon().setSampleRate(44100).build(), 0, null)
            assertEquals(48000, sink.openedFormat()?.sampleRate)
            val next = ByteBuffer.allocateDirect(4096)
            assertFalse(sink.handleBuffer(next, 1_000_000, 1))
            assertEquals(0, next.position())
            sink.play()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!sink.handleBuffer(next, 1_000_000, 1) && System.nanoTime() < deadline) Thread.sleep(10)
            assertEquals(next.limit(), next.position())
            assertEquals(44100, sink.openedFormat()?.sampleRate)
            sink.pause()
            sink.configure(raw, 0, null)
            sink.flush()
            sink.play()
            val afterFlush = ByteBuffer.allocateDirect(4096)
            val flushDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!sink.handleBuffer(afterFlush, 2_000_000, 1) && System.nanoTime() < flushDeadline) Thread.sleep(10)
            assertEquals(afterFlush.limit(), afterFlush.position())
            assertEquals("flush 不能丢弃待生效的解码格式", 48000, sink.openedFormat()?.sampleRate)
        } finally { sink.release() }
    }

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    @Test fun bothPathsSwitchActualDevicesAndRetainPosition() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val channels = AudioDeviceProbe.channels(manager)
        val usb = channels.firstOrNull { it.isUsb }
        assertNotNull("需要 USB DAC", usb)
        val other = channels.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?: channels.first { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val tracks = yy.daniel.yyplayerforandroid.playback.MediaLibraryRepository(context).load()
            .filter { it.kind == yy.daniel.yyplayerforandroid.playback.MediaKind.AUDIO }
        assertTrue("需要真实导入的本地歌曲", tracks.isNotEmpty())
        var selected: Int? = usb!!.id
        var backend: yy.daniel.yyplayerforandroid.playback.AndroidMedia3PlaybackBackend? = null
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
        fun await(label: String, predicate: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < deadline) {
                var ready = false
                main { ready = predicate() }
                if (ready) return
                Thread.sleep(100)
            }
            fail("超时：$label")
        }
        try {
            main {
                backend = yy.daniel.yyplayerforandroid.playback.AndroidMedia3PlaybackBackend(context,
                    selectedDeviceId = { selected }, onNegotiated = { report ->
                        if (report != null) android.util.Log.i("YYPlayerAudio", "YYTEST_ROUTE requested=$selected " +
                            "actual=${report.route.routedDeviceIds} pin=${report.devicePinAccepted}")
                    })
                backend!!.exoPlayer().volume = 0f // Route/clock test must not emit sound on the speaker.
                backend!!.setDecoderMode(yy.daniel.yyplayerforandroid.playback.DecoderMode.STRICT_HARDWARE)
                backend!!.play(listOf(tracks.first()))
            }
            for (path in OutputPath.entries) {
                main { backend!!.setOutputPath(path) }
                for (device in listOf(usb, other)) {
                    var before = 0L
                    main {
                        before = backend!!.exoPlayer().currentPosition
                        selected = device.id
                        backend!!.reconfigureOutput()
                    }
                    await("$path → ${device.productName}") {
                        assertNull("切换不能产生播放错误", backend!!.state.value.error)
                        val active = backend!!.exoPlayer() as androidx.media3.exoplayer.ExoPlayer
                        val routed = if (path == OutputPath.NATIVE_EXACT)
                            backend!!.state.value.negotiatedOutput?.route?.routedDeviceIds.orEmpty()
                        else {
                            // Debug verification only: inspect Media3 1.11.1's opened AudioTrack.
                            val renderer = (0 until active.rendererCount).map(active::getRenderer)
                                .first { it is androidx.media3.exoplayer.audio.MediaCodecAudioRenderer }
                            val sinkField = androidx.media3.exoplayer.audio.MediaCodecAudioRenderer::class.java
                                .getDeclaredField("audioSink").also { it.isAccessible = true }
                            val sink = sinkField.get(renderer)
                            val outputField = androidx.media3.exoplayer.audio.DefaultAudioSink::class.java
                                .getDeclaredField("audioOutput").also { it.isAccessible = true }
                            val output = outputField.get(sink) as? androidx.media3.exoplayer.audio.AudioTrackAudioOutput
                            listOfNotNull(output?.audioTrack?.routedDevice?.id)
                        }
                        active.isPlaying && active.currentPosition > before + 500 && device.id in routed
                    }
                    main { android.util.Log.i("YYPlayerAudio", "YYTEST_SWITCH path=$path requested=${device.id} " +
                        "device=${device.productName} position=${backend!!.exoPlayer().currentPosition} actualConfirmed=true") }
                }
            }
            main { backend!!.setOutputPath(OutputPath.SYSTEM); backend!!.setSpeed(2f) }
            await("系统 2×") { backend!!.exoPlayer().isPlaying && backend!!.exoPlayer().playbackParameters.speed == 2f }
            main { backend!!.setOutputPath(OutputPath.NATIVE_EXACT) }
            await("自研实际 1×") { backend!!.exoPlayer().isPlaying && backend!!.exoPlayer().playbackParameters.speed == 1f }
            main { backend!!.setOutputPath(OutputPath.SYSTEM) }
            await("切回系统保留 2× 意图") { backend!!.exoPlayer().isPlaying && backend!!.exoPlayer().playbackParameters.speed == 2f }
        } finally { main { backend?.close() } }
    }

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    @Test fun selfTestReleasesMusicAndRestoresItsPausedPositionAndMutedVolume() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val runtime = (context.applicationContext as PlayerApplication).runtime
        val tracks = runtime.library.load().filter { it.kind == yy.daniel.yyplayerforandroid.playback.MediaKind.AUDIO }
        assertTrue(tracks.isNotEmpty())
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
        fun await(label: String, predicate: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (System.nanoTime() < deadline) {
                var ready = false
                main { ready = predicate() }
                if (ready) return
                Thread.sleep(100)
            }
            fail("超时：$label")
        }
        val originalPath = runtime.preferences.value.audioOutputPath
        val originalDevice = runtime.audioOutput.state.value.selectedDeviceId
        var before = 0L
        try {
            main {
                runtime.acquire(); runtime.start()
                runtime.selectAudioOutputDevice(runtime.audioOutput.state.value.channels.first { it.isUsb }.id)
                runtime.setAudioOutputPath(OutputPath.NATIVE_EXACT)
                runtime.playback.exoPlayer().volume = 0f
                runtime.playback.play(listOf(tracks.first()))
            }
            await("真实音乐开始") { runtime.playback.exoPlayer().isPlaying && runtime.playback.exoPlayer().currentPosition > 500 }
            main {
                before = runtime.playback.exoPlayer().currentPosition
                runtime.runAudioOutputSelfTest()
                assertTrue(runtime.audioOutput.state.value.selfTestRunning)
                val activePlayer = runtime.playback.exoPlayer()
                val originalSharing = runtime.preferences.value.audioSharing
                val originalTransparency = runtime.preferences.value.audioTransparency
                runtime.setAudioSharing(if (originalSharing == SharingRequest.SHARED)
                    SharingRequest.PREFER_EXCLUSIVE else SharingRequest.SHARED)
                runtime.setAudioTransparency(if (originalTransparency == TransparencyRequest.COMPATIBLE)
                    TransparencyRequest.PREFER_UNPROCESSED else TransparencyRequest.COMPATIBLE)
                runtime.playback.reconfigureOutput()
                assertSame("自检期间政策切换不能重建歌曲输出", activePlayer, runtime.playback.exoPlayer())
                assertEquals(originalSharing, runtime.preferences.value.audioSharing)
                assertEquals(originalTransparency, runtime.preferences.value.audioTransparency)
                runtime.playback.togglePlayPause()
                assertFalse(runtime.playback.exoPlayer().playWhenReady)
                assertEquals(0f, runtime.playback.exoPlayer().volume, 0f)
            }
            await("自检完整结束") { !runtime.audioOutput.state.value.selfTestRunning }
            main {
                val result = runtime.audioOutput.state.value.selfTest
                assertNotNull(result)
                assertTrue(result!!.reasonText, result.deliveryComplete)
                assertTrue(result.routeMatchesRequest)
                assertFalse(result.systemReportedBitPerfect)
                assertFalse(runtime.playback.exoPlayer().playWhenReady)
                assertTrue(kotlin.math.abs(runtime.playback.exoPlayer().currentPosition - before) < 500)
                assertEquals(0f, runtime.playback.exoPlayer().volume, 0f)
            }
        } finally {
            main {
                runtime.playback.exoPlayer().stop()
                runtime.playback.exoPlayer().volume = 1f
                runtime.selectAudioOutputDevice(originalDevice)
                runtime.setAudioOutputPath(originalPath)
                runtime.stop(); runtime.release(false)
            }
            context.stopService(android.content.Intent(context, yy.daniel.yyplayerforandroid.playback.PlayerSessionService::class.java))
        }
    }

    @Test fun directBuffersHonorPositionLimitAndReadOnlyDestination() {
        val wideRing = NativeAudioCore.createRing(48000, 32, SampleEncoding.S16, 0, 64)
        assertTrue("核心 JNI 保留 32 声道容量契约", wideRing > 0)
        assertTrue(NativeAudioCore.releaseRing(wideRing))
        val ring = NativeAudioCore.createRing(48000, 2, SampleEncoding.S16, 3, 64)
        assertTrue(ring > 0)
        try {
            val src = ByteBuffer.allocateDirect(32)
            for (i in 0 until 32) src.put(i, i.toByte())
            src.position(8); src.limit(16)
            assertEquals(2, NativeAudioCore.ringWrite(ring, src, 99))
            assertEquals(8, src.position())
            val dst = ByteBuffer.allocateDirect(32)
            dst.position(12); dst.limit(20)
            assertEquals(0, NativeAudioCore.ringRead(ring, dst.asReadOnlyBuffer(), 2))
            assertEquals(2, NativeAudioCore.ringRead(ring, dst, 99))
            for (i in 0 until 8) assertEquals((i + 8).toByte(), dst.get(i + 12))
            assertEquals(0.toByte(), dst.get(11))
            assertEquals(12, dst.position())
        } finally { assertTrue(NativeAudioCore.releaseRing(ring)) }
        assertNull(NativeAudioCore.ringStats(ring))
    }

    @Test fun concurrentReleaseClearAndIoDoNotUseFreedMemory() {
        val executor = Executors.newFixedThreadPool(3)
        try {
            repeat(100) {
                val ring = NativeAudioCore.createRing(48000, 2, SampleEncoding.S16, 3, 64)
                assertTrue(ring > 0)
                val jobs = (0..2).map { worker -> executor.submit {
                    val buffer = ByteBuffer.allocateDirect(256)
                    repeat(100) {
                        when (worker) {
                            0 -> NativeAudioCore.ringWrite(ring, buffer, 64)
                            1 -> { NativeAudioCore.ringRead(ring, buffer, 64); NativeAudioCore.ringStats(ring) }
                            else -> NativeAudioCore.ringClear(ring)
                        }
                    }
                } }
                assertTrue(NativeAudioCore.releaseRing(ring))
                jobs.forEach { it.get(5, TimeUnit.SECONDS) }
                assertNull(NativeAudioCore.ringStats(ring))
                assertFalse(NativeAudioCore.releaseRing(ring))
            }
        } finally { executor.shutdownNow() }
    }

    @Test fun policyReadsProfilesBeyond32AndNormalizesAndroidChannelMasks() {
        val filler = (1..40).map { MixerProfile(SampleEncoding.S16, 8000 + it * 100,
            AudioFormat.CHANNEL_OUT_STEREO, false, true) }
        val route = AudioOutputChannel(99, 11, "测试", "测试", "", true, listOf(96000), listOf(2),
            emptyList(), SinkKind.AUDIO_TRACK_SYSTEM, emptyList(),
            filler + MixerProfile(SampleEncoding.S32, 96000, AudioFormat.CHANNEL_OUT_STEREO, false, true),
            false, false)
        val result = NativeAudioCore.decide(SourceFormat(96000, 2, SampleEncoding.S32), route,
            SharingRequest.SHARED, TransparencyRequest.PREFER_UNPROCESSED, ProcessingState())
        assertTrue(result.reasonCode, result.accepted)
        assertEquals(96000, result.format?.sampleRate)
        assertEquals(SampleEncoding.S32, result.format?.encoding)
    }

    @Test fun requestAcceptanceAloneNeverProvesBitPerfect() {
        val format = OutputFormat(48000, 2, SampleEncoding.S16, AudioFormat.CHANNEL_OUT_STEREO)
        val result = OutputSelfTestResult(true, decidedFormat = format, requestedDeviceId = 42,
            bitPerfectRequested = true, mixerContractAccepted = true,
            route = SinkRouteReport(routedDeviceIds = listOf(43), routedIsUsb = true, platformFormat = format))
        assertFalse(result.systemReportedBitPerfect)
        assertFalse(result.deliveryComplete)
        val complete = result.copy(mixerContractReadBack = MixerProfile(SampleEncoding.S16, 48000,
            AudioFormat.CHANNEL_OUT_STEREO, true, false), framesFed = 64, framesConsumed = 64, framesPlayed = 64,
            route = result.route.copy(routedDeviceIds = listOf(42), underrunCount = 0))
        assertTrue(complete.systemReportedBitPerfect)
        assertFalse(complete.copy(partialWriteFrames = 1).systemReportedBitPerfect)
        assertFalse(complete.copy(route = complete.route.copy(underrunCount = 1)).systemReportedBitPerfect)
    }
}
