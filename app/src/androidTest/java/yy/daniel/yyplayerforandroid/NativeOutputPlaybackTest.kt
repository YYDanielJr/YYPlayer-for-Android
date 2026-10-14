package yy.daniel.yyplayerforandroid

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import yy.daniel.yyplayerforandroid.audio.OutputPath
import yy.daniel.yyplayerforandroid.playback.OutputPathRenderersFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Exercises the native output path through a real ExoPlayer, so Media3 itself validates the
 * audio-sink contract instead of the app asserting its own interpretation of it.
 *
 * The player is created and driven on the main thread (Media3 requires it) while the test thread waits
 * for the outcome, which also mirrors how the app uses the engine.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class NativeOutputPlaybackTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test
    fun sinkRefusesCompressedBytesAndUnprovenStrictPolicies() {
        val format = androidx.media3.common.Format.Builder().setSampleMimeType("audio/raw")
            .setPcmEncoding(androidx.media3.common.C.ENCODING_PCM_24BIT)
            .setSampleRate(44100).setChannelCount(2).build()
        val sink = yy.daniel.yyplayerforandroid.audio.NativeOutputAudioSink(context, { null })
        assertEquals(androidx.media3.exoplayer.audio.AudioSink.CURRENT_POSITION_NOT_SET, sink.getCurrentPositionUs(false))
        assertFalse("FLAC metadata 的 pcmEncoding 不代表 buffer 是 PCM",
            sink.supportsFormat(format.buildUpon().setSampleMimeType("audio/flac").build()))
        assertFalse(sink.supportsFormat(format.buildUpon().setPcmEncoding(androidx.media3.common.C.ENCODING_PCM_FLOAT).build()))
        for (strictSharing in listOf(true, false)) {
            val strict = yy.daniel.yyplayerforandroid.audio.NativeOutputAudioSink(context, { null },
                sharingRequest = { if (strictSharing) yy.daniel.yyplayerforandroid.audio.SharingRequest.STRICT_EXCLUSIVE
                    else yy.daniel.yyplayerforandroid.audio.SharingRequest.SHARED },
                transparencyRequest = { if (strictSharing) yy.daniel.yyplayerforandroid.audio.TransparencyRequest.COMPATIBLE
                    else yy.daniel.yyplayerforandroid.audio.TransparencyRequest.STRICT_UNPROCESSED })
            try {
                strict.configure(format, 0, null)
                fail("本机没有独占或 BIT_PERFECT 证据，应拒绝严格请求")
            } catch (expected: androidx.media3.exoplayer.audio.AudioSink.ConfigurationException) {
                assertTrue(expected.message.orEmpty().contains(if (strictSharing) "exclusive_not_proven" else "bitperfect_contract_unavailable"))
            } finally { strict.release() }
        }
    }

    @Test
    fun nativeSinkPlaysALocalFileThroughExoPlayer() {
        runThroughExoPlayer(OutputPath.NATIVE_EXACT, "native")
    }

    /**
     * Control run on the system sink. Same file, same player wiring, only the output path differs, so a
     * difference in how much audio is delivered points at the sink rather than at the file or the source.
     */
    @Test
    fun systemSinkPlaysTheSameFile() {
        runThroughExoPlayer(OutputPath.SYSTEM, "system")
    }

    private fun runThroughExoPlayer(path: OutputPath, label: String) {
        val runtime = (context.applicationContext as PlayerApplication).runtime
        val all = runtime.library.load().filter { it.kind == yy.daniel.yyplayerforandroid.playback.MediaKind.AUDIO }
        // A short MP3 as well as the long FLAC: comparing them separates "this file" from "this sink".
        val track = all.firstOrNull { it.title.endsWith(".mp3", true) } ?: all.firstOrNull()
        android.util.Log.i("YYPlayerAudio", "YYTEST_${label}_TRACK=${track?.musicTitle} uri=${track?.uri}")
        assertNotNull("需要已导入的本地音频", track)
        track!!

        // Ground truth about the file, so "the sink stopped early" can be told apart from "the file
        // really is this short".
        val retriever = android.media.MediaMetadataRetriever()
        val fileDurationMs = runCatching {
            retriever.setDataSource(context, track.uri)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }.getOrNull()
        val fileSampleRate = runCatching {
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
        }.getOrNull()
        runCatching { retriever.release() }
        android.util.Log.i(
            "YYPlayerAudio",
            "YYTEST_${label}_FILE durationMs=$fileDurationMs sampleRate=$fileSampleRate " +
                "expectedFrames=${if (fileDurationMs != null && fileSampleRate != null) fileDurationMs * fileSampleRate / 1000 else null}",
        )

        val negotiated = AtomicReference<String>()
        val failure = AtomicReference<PlaybackException>()
        val ready = CountDownLatch(1)
        val main = InstrumentationRegistry.getInstrumentation()
        val released = CountDownLatch(1)
        // Read back from the sink the test created, so the numbers under test are visible directly.
        val negotiateProbe = AtomicReference<String>()
        val probeSink = AtomicReference<yy.daniel.yyplayerforandroid.audio.NativeOutputAudioSink>()
        val assertionFailure = AtomicReference<Throwable>()
        val fullPlayback = path == OutputPath.NATIVE_EXACT &&
            InstrumentationRegistry.getArguments().getString("fullPlayback") == "true"
        val pinnedDevice = InstrumentationRegistry.getArguments().getString("pinnedDeviceId")?.toIntOrNull()
        val sampleCount = if (fullPlayback) ((fileDurationMs!! + 7_000L) / 4_000L).toInt() else 4

        main.runOnMainSync {
            val renderers = OutputPathRenderersFactory(
                appContext = context,
                outputPath = { path },
                selectedDeviceId = { pinnedDevice },
                onNegotiated = { n ->
                    if (n != null) {
                    negotiated.set(
                        "format=${n.format.encoding.wire}/${n.format.sampleRate}Hz widened=${n.widened} " +
                            "bitPerfectRequested=${n.bitPerfectRequested} reason=${n.reasonCode} " +
                            "requested=$pinnedDevice routed=${n.route.routedDeviceIds}",
                    )
                    android.util.Log.i("YYPlayerAudio", "YYTEST_${label}_NEGOTIATED ${negotiated.get()}")
                    }
                },
                onSinkCreated = { created ->
                    negotiateProbe.set("sink=${created.javaClass.simpleName}")
                    probeSink.set(created)
                },
            )
            val player = ExoPlayer.Builder(context, renderers).build()
            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    failure.set(error)
                    val cause = error.cause
                    android.util.Log.e(
                        "YYPlayerAudio",
                        "YYTEST_${label}_ERROR ${error.errorCodeName} cause=${cause?.javaClass?.name}: ${cause?.message}",
                    )
                    cause?.stackTrace?.take(8)?.forEach {
                        android.util.Log.e("YYPlayerAudio", "YYTEST_${label}_CAUSE at $it")
                    }
                    ready.countDown()
                }

                override fun onPlaybackStateChanged(state: Int) {
                    android.util.Log.i("YYPlayerAudio", "YYTEST_${label}_STATE=$state")
                    if (state == Player.STATE_READY) ready.countDown()
                }
            })
            player.setMediaItem(MediaItem.fromUri(track.uri))
            player.prepare()
            player.play()

            // Sample the player over the window, then tear it down on this thread. The comparison that
            // matters is how many frames the sink accepted versus what the file actually contains.
            repeat(sampleCount) { step ->
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val snapshot = probeSink.get()
                    val decodedFrames = snapshot?.framesAccepted() ?: 0L
                    android.util.Log.i(
                        "YYPlayerAudio",
                        "YYTEST_${label}_PROGRESS step=$step isPlaying=${player.isPlaying} " +
                            "state=${player.playbackState} position=${player.currentPosition} " +
                            "duration=${player.duration} buffered=${player.bufferedPosition} " +
                            "error=${failure.get()?.errorCodeName} decodedFrames=$decodedFrames " +
                            "fileFrames=${fileDurationMs?.let { it * (fileSampleRate ?: 44100) / 1000 }} " +
                            "counters=${snapshot?.counters()} opened=${snapshot?.openedFormat()?.encoding?.wire}",
                    )
                    if (step == sampleCount - 1) {
                        try {
                            assertNull("播放器发生错误", failure.get())
                            if (fullPlayback) {
                                assertEquals("整曲必须正常结束", Player.STATE_ENDED, player.playbackState)
                                val expected = fileDurationMs!! * fileSampleRate!! / 1000L
                                assertTrue("所有解码帧应送达（时长元数据允许 1ms 舍入）",
                                    kotlin.math.abs(decodedFrames - expected) <= fileSampleRate / 1000L + 1)
                            } else {
                                assertEquals(Player.STATE_READY, player.playbackState)
                                assertTrue("播放应持续前进", player.isPlaying)
                                assertTrue("位置必须正常且超过旧停摆点", player.currentPosition in 12_000L..20_000L)
                            }
                            if (path == OutputPath.NATIVE_EXACT) {
                                assertNotNull("必须使用自研 sink", snapshot)
                                assertTrue("真实解码帧必须超过 12 秒", decodedFrames > (fileSampleRate ?: 44100) * 12L)
                            }
                        } catch (error: Throwable) {
                            assertionFailure.set(error)
                        } finally {
                            player.release()
                            released.countDown()
                        }
                    }
                }, 4_000L * (step + 1))
            }
        }

        val reached = ready.await(40, TimeUnit.SECONDS)
        assertTrue("播放器未释放，可能阻塞播放线程", released.await(sampleCount * 4L + 15L, TimeUnit.SECONDS))
        assertTrue("未进入 READY", reached)
        assertionFailure.get()?.let { throw it }
        android.util.Log.i(
            "YYPlayerAudio",
            "YYTEST_${label}_RESULT reached=$reached error=${failure.get()?.errorCodeName} negotiated=${negotiated.get()}",
        )
    }
}
