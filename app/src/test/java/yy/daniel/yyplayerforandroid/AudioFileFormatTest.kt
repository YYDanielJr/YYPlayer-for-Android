package yy.daniel.yyplayerforandroid

import org.junit.Assert.*
import org.junit.Test
import yy.daniel.yyplayerforandroid.online.AudioFileFormat
import yy.daniel.yyplayerforandroid.online.downloadBaseName
import yy.daniel.yyplayerforandroid.online.mergeTranslatedLyrics

class AudioFileFormatTest {
    @Test fun flacWinsOverAnySuggestedMp3ExtensionAndReadsStreamInfoBits() {
        for (depth in listOf(16, 24)) {
            val header = ByteArray(42)
            "fLaC".toByteArray().copyInto(header)
            header[7] = 34
            header[20] = ((depth - 1) shr 4).toByte()
            header[21] = (((depth - 1) and 15) shl 4).toByte()
            assertEquals("flac", AudioFileFormat.detect(header)!!.extension)
            assertEquals(depth, AudioFileFormat.detect(header)!!.bitDepth)
            val id3 = byteArrayOf(73, 68, 51, 4, 0, 0, 0, 0, 0, 0)
            assertEquals(depth, AudioFileFormat.detect(id3 + header)!!.bitDepth)
        }
    }
    @Test fun identifiesCommonContainersAndRejectsHtmlOrMalformedData() {
        fun ext(value: ByteArray) = AudioFileFormat.detect(value)?.extension
        assertEquals("wav", ext("RIFFxxxxWAVE".toByteArray()))
        assertEquals("m4a", ext("xxxxftypM4A ".toByteArray()))
        assertEquals("opus", ext("OggSxxxxxxxxOpusHead".toByteArray()))
        assertEquals("ogg", ext("OggSxxxxxxxxvorbis".toByteArray()))
        assertEquals("mp3", ext(byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0)))
        assertEquals("aac", ext(byteArrayOf(0xff.toByte(), 0xf1.toByte(), 0x50, 0)))
        assertEquals("aiff", ext("FORMxxxxAIFF".toByteArray()))
        assertNull(ext("<html>not audio</html>".toByteArray()))
        assertNull(ext(byteArrayOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0)))
        assertNull(ext(byteArrayOf(73, 68, 51, 4, 0, 0, 127, 127, 127, 127)))
    }
    @Test fun humanReadableNamesHaveNoRandomSuffixAndSanitizeOnlyUnsafeCharacters() {
        assertEquals("铁花飞 - Mili _ 塞壬唱片-MSR", downloadBaseName("铁花飞", "Mili / 塞壬唱片-MSR"))
        assertEquals("歌名 - 未知歌手", downloadBaseName("歌名", ""))
    }
    @Test fun translationSharesOriginalTimeWithoutHidingOriginalLine() {
        val merged = mergeTranslatedLyrics("[00:01.00]hello\n[00:02.00]world", "[00:01.00]你好\n[00:02.00]世界")
        assertTrue(merged.contains("hello / 你好"))
        assertTrue(merged.contains("world / 世界"))
    }
}
