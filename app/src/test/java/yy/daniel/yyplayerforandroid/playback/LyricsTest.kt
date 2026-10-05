package yy.daniel.yyplayerforandroid.playback

import org.junit.Assert.*
import org.junit.Test

class LyricsTest {
    @Test fun desktopDuplicateOffsetAndSeekSemantics() {
        val lyrics = Lyrics.parse("[offset:-100]\n[00:02.50][00:01.20]Second\n[00:01.20]Translation")
        assertEquals(1100L, lyrics.lines[0].milliseconds)
        assertEquals(1, lyrics.active(1100))
        assertEquals(-1, lyrics.active(100))
        assertEquals(2, lyrics.active(2300, 100))
        assertEquals(listOf("Second", "Translation", "Second"), lyrics.lines.map { it.text })
    }
    @Test fun malformedAndBoundedInput() {
        listOf("[00:99]bad", "[offset:99999999999]", "[10001:00]bad", "[00:NaN]bad", "x".repeat(1_000_001),
            "[00:01]a\n".repeat(10001)).forEach { text -> assertTrue(runCatching { Lyrics.parse(text) }.isFailure) }
    }
    @Test fun bomPlainTextAndEncodingFallback() {
        val text = "[00:01.250]让音乐慢慢流动"
        assertEquals(text, Lyrics.decode(byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE)))
        assertEquals(text, Lyrics.decode(byteArrayOf(0xfe.toByte(), 0xff.toByte()) + text.toByteArray(Charsets.UTF_16BE)))
        assertEquals(text, Lyrics.decode(text.toByteArray(charset("GB18030"))))
        assertEquals("第一行\n第二行\n", Lyrics.parse("\uFEFF第一行\n第二行").plain)
        assertEquals(1250L, Lyrics.parse("\uFEFF$text").lines.first().milliseconds)
    }
    @Test fun offsetClampAndSaturatingPosition() {
        val lyrics = Lyrics.parse("[offset:-500]\n[00:00.100]Start\n[00:01.000]Next")
        assertEquals(0L, lyrics.lines.first().milliseconds)
        assertEquals(1, lyrics.active(Long.MAX_VALUE, 600000))
        assertEquals(-1, lyrics.active(Long.MIN_VALUE, -600000))
    }
    @Test fun vorbisEmbeddedLyricsAndTruncatedBlocks() {
        val text = "LYRICS=[00:01]First"
        val encoded = text.toByteArray(Charsets.UTF_8)
        val buffer = java.nio.ByteBuffer.allocate(12 + encoded.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(0).putInt(1).putInt(encoded.size).put(encoded).array()
        assertEquals("[00:01]First", EmbeddedLyrics.vorbis(buffer))
        assertTrue(runCatching { EmbeddedLyrics.vorbis(buffer.copyOf(10)) }.isFailure)
    }
}
