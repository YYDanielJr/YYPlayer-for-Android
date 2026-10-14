package yy.daniel.yyplayerforandroid.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Deterministic PCM test signal used to make an output path auditable.
 *
 * This is a verification instrument, not a playback feature: it exists so that "the decided format
 * reached the device" can be checked by ear (which segment is on which side, where the beeps are) and
 * by hash (the bytes that entered the ring are reproducible). It never runs inside normal playback.
 *
 * The signal is built from exact integer samples, so two runs on any device produce the same bytes.
 * The head of the plan is silent on both channels; that silence is what makes it safe to open the
 * output and ask the platform to configure a USB exclusive/bit-perfect mixer *before* any sound is
 * produced, and it gives the DAC time to retune its clock.
 */
internal class AudioVerificationTone(
    val sampleRate: Int = DEFAULT_RATE,
    val segments: List<Segment> = DEFAULT_SEGMENTS,
) {

    /** One part of the signal. [left] is null when the segment is silent on that channel. */
    data class Segment(val label: String, val left: Int?, val right: Int?)

    /** Total frames of the whole plan, silence included. */
    val totalFrames: Int = segments.sumOf { if (it.left == null && it.right == null) SILENT_SECONDS else TONE_SECONDS } * sampleRate

    /**
     * Renders the plan into interleaved 16-bit little-endian frames. The caller widens or otherwise
     * converts it with the portable core, so this function has exactly one output format and never
     * needs a conversion table of its own.
     *
     * The buffer is direct on purpose: the core reads sample memory through `GetDirectBufferAddress`,
     * which returns null for a heap-backed buffer, making every conversion fail with a bad-argument
     * result that looks like a rejected format pair.
     */
    fun renderS16(): ByteBuffer {
        val frames = totalFrames
        val buffer = ByteBuffer.allocateDirect(frames * CHANNELS * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (segment in segments) {
            val silence = segment.left == null && segment.right == null
            val seconds = if (silence) SILENT_SECONDS else TONE_SECONDS
            val count = seconds * sampleRate
            if (silence) {
                repeat(count * CHANNELS) { buffer.putShort(0) }
                continue
            }
            for (frame in 0 until count) {
                buffer.putShort(channelSample(segment.left, frame))
                buffer.putShort(channelSample(segment.right, frame))
            }
        }
        buffer.flip()
        return buffer
    }

    /** The sample a segment puts on one channel at [frame], or exact silence when the channel is unused. */
    private fun channelSample(frequency: Int?, frame: Int): Short {
        if (frequency == null) return 0
        // Fixed-point sine: a 0.16 phase accumulator and a quarter-wave table, so the sample for a
        // given frame is identical on every device and no platform math library is involved.
        val increment = ((frequency.toLong() shl 16) / sampleRate).toInt()
        val phase = (frame.toLong() * increment) and 0xFFFF
        return (sine(phase.toInt()) * TONE_AMPLITUDE / SINE_ONE).toInt().toShort()
    }

    /** Sine of a 0.16 phase, in [-SINE_ONE, SINE_ONE]. */
    private fun sine(phase: Int): Long {
        val index = (phase shr 8) and 0xFF
        val quadrant = (phase shr 14) and 0x3
        return when (quadrant) {
            0 -> SINE_TABLE[index].toLong()
            1 -> SINE_TABLE[256 - index].toLong()
            2 -> -SINE_TABLE[index].toLong()
            else -> -SINE_TABLE[256 - index].toLong()
        }
    }

    /** What the signal is, for the operator's ear and for the evidence file. */
    fun description(): String = segments.joinToString(" → ") { segment ->
        val sides = when {
            segment.left != null && segment.right != null -> "双声道"
            segment.left != null -> "仅左"
            segment.right != null -> "仅右"
            else -> "静音"
        }
        val tone = segment.left ?: segment.right
        if (tone == null) "$sides ${SILENT_SECONDS}s（${segment.label}）" else "$sides ${tone}Hz ${TONE_SECONDS}s"
    }

    internal companion object {
        const val DEFAULT_RATE = 48000
        const val CHANNELS = 2
        const val TONE_SECONDS = 1
        const val SILENT_SECONDS = 2

        /** 0.5 full scale: loud enough to hear, and it cannot clip after any integer widening. */
        const val TONE_AMPLITUDE = 16384
        const val SINE_ONE = 65536

        /**
         * The audible plan. The first segment is silent on purpose (see the class comment); after that
         * 440 Hz left, 880 Hz right and then both identify a swapped or collapsed channel immediately,
         * which a checksum cannot.
         */
        val DEFAULT_SEGMENTS = listOf(
            Segment("开流与混音器配置", null, null),
            Segment("左 440Hz", 440, null),
            Segment("右 880Hz", null, 880),
            Segment("双声道 440/880Hz", 440, 880),
            Segment("收尾静音", null, null),
        )

        private val SINE_TABLE = IntArray(257) { index ->
            val radians = index * (Math.PI / 512.0)
            (Math.sin(radians) * SINE_ONE).toInt()
        }
    }
}

/**
 * Feeds a rendered signal into a native PCM ring in bounded chunks and hashes exactly the bytes it
 * wrote.
 *
 * The chunking matters: the ring is deliberately small, so a whole signal is never pushed at once.
 * The digest is taken over the bytes handed to `ringWrite`, which is the last place the application
 * still has the samples under its own control - so it is the value an external digital capture can be
 * compared against, and the value that proves the application did not alter them afterwards.
 */
internal object PcmRingFeeder {

    data class Result(
        val framesWritten: Long,
        val framesDropped: Long,
        val expectedFrames: Long,
        val digestHex: String,
    ) {
        /** True only when the ring accepted every frame of the signal, so the digest is complete. */
        val complete: Boolean get() = framesWritten == expectedFrames && framesDropped == 0L
    }

    /**
     * Writes [pcm] (interleaved frames of [format]) into [ringHandle], never more than
     * [chunkFrames] at a time.
     *
     * Two rules make the run auditable:
     *  - pacing. A ring only a second long is filled instantly by a fast producer, and everything the
     *    sink has not taken yet is dropped, so the signal never actually plays. Frames are therefore
     *    offered at the real sample rate, with a small tolerance for the ring's own buffer.
     *  - the digest covers exactly the frames the ring accepted. When the producer ever gets ahead of
     *    the ring, the dropped frames are counted and the digest is no longer a complete description of
     *    the signal, which `complete` then reports instead of hiding.
     *
     * The bridge reads sample memory through `GetDirectBufferAddress`, so the chunk must be a direct
     * buffer: a heap-backed one makes every write silently report zero frames accepted, which looks
     * exactly like a ring that never drains.
     */
    fun feed(
        ringHandle: Long,
        pcm: ByteBuffer,
        format: OutputFormat,
        chunkFrames: Int = 2048,
        gapMillis: Long = 2,
        budgetMillis: Long = 30000,
        checkActive: () -> Unit = {},
    ): Result {
        val frameBytes = format.encoding.bytesPerSample * format.channelCount
        val source = pcm.slice().order(ByteOrder.LITTLE_ENDIAN)
        require(frameBytes > 0 && format.sampleRate > 0 && source.remaining() % frameBytes == 0)
        val total = source.remaining() / frameBytes
        val digest = MessageDigest.getInstance("SHA-256")
        val chunk = ByteBuffer.allocateDirect(chunkFrames * frameBytes)
        val staging = ByteArray(chunkFrames * frameBytes)
        val frameNanos = 1_000_000_000L / format.sampleRate
        val startNanos = System.nanoTime()
        val deadline = System.currentTimeMillis() + budgetMillis
        var offered = 0L
        var written = 0L
        while (written < total && System.currentTimeMillis() < deadline) {
            checkActive()
            // Never get more than one chunk ahead of real time; the ring's own buffer covers jitter.
            val targetFrames = ((System.nanoTime() - startNanos) / frameNanos) + chunkFrames
            if (offered >= targetFrames) {
                Thread.sleep(gapMillis)
                continue
            }
            val want = minOf(chunkFrames.toLong(), total - written, targetFrames - offered).toInt()
            if (want <= 0) {
                Thread.sleep(gapMillis)
                continue
            }
            val bytes = want * frameBytes
            source.position((written * frameBytes).toInt())
            source.get(staging, 0, bytes)
            chunk.clear()
            chunk.put(staging, 0, bytes)
            chunk.flip()
            val accepted = NativeAudioCore.ringWrite(ringHandle, chunk, want)
            offered += want.toLong()
            if (accepted <= 0) {
                Thread.sleep(gapMillis)
                continue
            }
            digest.update(staging, 0, accepted * frameBytes)
            written += accepted.toLong()
        }
        val stats = NativeAudioCore.ringStats(ringHandle)
        return Result(
            framesWritten = written,
            // The ring's own drop counter is authoritative; `offered` only exists to pace the producer.
            framesDropped = stats?.getOrNull(4) ?: -1,
            expectedFrames = total.toLong(),
            digestHex = digest.digest().joinToString("") { "%02x".format(it) },
        )
    }
}
