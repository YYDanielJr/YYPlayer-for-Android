package yy.daniel.yyplayerforandroid.playback

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import yy.daniel.yyplayerforandroid.audio.NativeOutputAudioSink
import yy.daniel.yyplayerforandroid.audio.OutputPath

/**
 * Picks the audio sink for one player instance.
 *
 * Rebuilding the renderers is the only honest way to switch output paths: an `AudioSink` owns the
 * platform stream for as long as it is configured, so the two paths cannot both be attached to one
 * player. The caller therefore builds a new player when the setting changes, and treats that like any
 * other decode/route change (position and play intent are restored).
 *
 * [OutputPath.SYSTEM] retains Media3's sink and clock. Video configures a shorter PCM queue and
 * requests low-latency AudioTrack output; music keeps the stock buffering policy.
 *
 * Float output is disabled on both paths. Media3 requests float PCM when the device advertises it,
 * which means an extra conversion stage in front of the sink; the app's rule is that samples reach the
 * output in an integer container or not at all, and disabling it also keeps the system path's format
 * comparable with the native path's.
 */
@UnstableApi
class OutputPathRenderersFactory(
    private val appContext: Context,
    private val outputPath: () -> OutputPath,
    private val selectedDeviceId: () -> Int?,
    private val onNegotiated: (NativeOutputAudioSink.Negotiated?) -> Unit,
    /** Hands the created native sink to the caller for diagnostics; null when not needed. */
    private val onSinkCreated: ((NativeOutputAudioSink) -> Unit)? = null,
    private val sharingRequest: () -> yy.daniel.yyplayerforandroid.audio.SharingRequest = { yy.daniel.yyplayerforandroid.audio.SharingRequest.SHARED },
    private val transparencyRequest: () -> yy.daniel.yyplayerforandroid.audio.TransparencyRequest = { yy.daniel.yyplayerforandroid.audio.TransparencyRequest.PREFER_UNPROCESSED },
    private val videoPlayback: Boolean = false,
) : DefaultRenderersFactory(appContext) {
    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        if (audioSink !is NativeOutputAudioSink) {
            super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
                audioSink, eventHandler, eventListener, out)
            return
        }
        out.add(object : MediaCodecAudioRenderer(context, codecAdapterFactory, mediaCodecSelector,
            enableDecoderFallback, eventHandler, eventListener, audioSink) {
            override fun getMediaFormat(format: Format, codecMimeType: String, codecMaxInputSize: Int,
                codecOperatingRate: Float): MediaFormat {
                if (format.pcmEncoding == C.ENCODING_PCM_FLOAT) throw createRendererException(
                    IllegalStateException("float_pcm_requires_system_output"), format,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
                return super.getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate).apply {
                    // The stock renderer asks for float if the sink accepts it. Ask for the source
                    // integer container explicitly, then verify the decoder actually honored it.
                    setInteger(MediaFormat.KEY_PCM_ENCODING, when (format.pcmEncoding) {
                        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT -> format.pcmEncoding
                        else -> C.ENCODING_PCM_16BIT
                    })
                }
            }
            override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
                if (format.pcmEncoding == C.ENCODING_PCM_24BIT || format.pcmEncoding == C.ENCODING_PCM_32BIT) {
                    val actual = if (mediaFormat == null) format.pcmEncoding else
                        if (mediaFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else C.ENCODING_PCM_16BIT
                    val valid = actual == format.pcmEncoding ||
                        (format.pcmEncoding == C.ENCODING_PCM_24BIT && actual == C.ENCODING_PCM_32BIT)
                    if (!valid) throw createRendererException(
                        IllegalStateException("解码器未保留源整数位深：${format.pcmEncoding} → $actual"),
                        format, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
                }
                super.onOutputFormatChanged(format, mediaFormat)
            }
        })
    }

    init {
        // Float output is disabled: Media3 requests float PCM when the device advertises it, which would
        // add a conversion stage in front of the sink, and the app's rule is that samples reach the output
        // in an integer container or not at all.
        setEnableAudioFloatOutput(false)
        // Keep Media3's full 0.1–8x Sonic range: Android's platform rate can reject high speeds.
        setEnableAudioOutputPlaybackParameters(false)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = when (outputPath()) {
        // The stock factory always produces a sink; a null here would mean Media3 changed its contract.
        OutputPath.SYSTEM -> if (videoPlayback) DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(false)
            .setEnableAudioOutputPlaybackParameters(false)
            // The 500ms default PCM queue delays Sonic's speed transition. Keep a short video-only
            // queue; the stock provider still enforces AudioTrack's actual minimum buffer size.
            .setAudioOutputProvider(AudioTrackAudioOutputProvider.Builder(context)
                .setMaxPlaybackSpeed(1f) // Sonic changes PCM tempo; the platform stream stays at 1x.
                .setAudioTrackBuilderModifier { builder, _ ->
                    builder.setPerformanceMode(android.media.AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
                .setAudioTrackBufferSizeProvider(DefaultAudioTrackBufferSizeProvider.Builder()
                    .setTargetPcmBufferDurationUs(40_000).build()).build())
            .build()
        else super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
            ?: throw IllegalStateException("系统输出通道未提供 AudioSink")
        OutputPath.NATIVE_EXACT -> NativeOutputAudioSink(
            context = appContext,
            selectedDeviceId = selectedDeviceId,
            onNegotiated = onNegotiated,
            sharingRequest = sharingRequest,
            transparencyRequest = transparencyRequest,
        ).also { onSinkCreated?.invoke(it) }
    }
}
