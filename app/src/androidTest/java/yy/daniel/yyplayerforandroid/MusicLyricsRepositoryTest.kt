package yy.daniel.yyplayerforandroid

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import yy.daniel.yyplayerforandroid.playback.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real file IO and persisted associations in an isolated preference namespace, no user mutations. */
class MusicLyricsRepositoryTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "lyrics-qa-${java.util.UUID.randomUUID()}-"
    private val context = object : ContextWrapper(app) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences(namespace + name, mode)
    }
    private fun media(file: File) = LocalMedia(Uri.fromFile(file), file.name, MediaKind.AUDIO, null)
    private fun atom(name: String, bytes: ByteArray) = ByteBuffer.allocate(8 + bytes.size).putInt(8 + bytes.size)
        .put(name.toByteArray(Charsets.ISO_8859_1)).put(bytes).array()

    @Test fun manualLyricsPersistAndFailedReplacementPreservesAssociation() = runBlocking {
        val source = File.createTempFile("source", ".wav", app.cacheDir)
        val lyric = File.createTempFile("lyrics", ".lrc", app.cacheDir)
        val bad = File.createTempFile("bad-lyrics", ".lrc", app.cacheDir)
        try {
            lyric.writeText("[offset:100]\n[00:01.50]真实文件歌词")
            bad.writeText("[00:99]invalid")
            val repository = MusicLyricsRepository(context)
            val imported = repository.import(media(source), Uri.fromFile(lyric))
            assertEquals(1600L, imported.lyrics.lines.first().milliseconds)
            assertEquals("真实文件歌词", MusicLyricsRepository(context).read(media(source)).lyrics.lines.first().text)
            assertTrue(runCatching { repository.import(media(source), Uri.fromFile(bad)) }.isFailure)
            assertEquals(imported.lyrics, repository.read(media(source)).lyrics)
            assertTrue(repository.clear(media(source)).lyrics.lines.isEmpty())
        } finally { source.delete(); lyric.delete(); bad.delete(); context.getSharedPreferences("music_lyrics_v1", 0).edit().clear().commit() }
    }

    @Test fun embeddedFlacAndMp4LyricsUseBoundedTagReaders() = runBlocking {
        val flac = File.createTempFile("tag-only", ".flac", app.cacheDir)
        val m4a = File.createTempFile("tag-only", ".m4a", app.cacheDir)
        try {
            val text = "[00:02]内嵌歌词"
            val comment = "LYRICS=$text".toByteArray(Charsets.UTF_8)
            val data = ByteBuffer.allocate(12 + comment.size).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putInt(1).putInt(comment.size).put(comment).array()
            flac.writeBytes("fLaC".toByteArray() + byteArrayOf(0x84.toByte(), 0, 0, data.size.toByte()) + data)
            m4a.writeBytes(atom("ftyp", "M4A ".toByteArray() + ByteArray(4)) +
                atom("moov", atom("udta", atom("meta", ByteArray(4) + atom("ilst", atom("©lyr", atom("data", ByteBuffer.allocate(8).putInt(1).putInt(0).array() + text.toByteArray())))))))
            assertEquals(text, EmbeddedLyrics.read(context, Uri.fromFile(flac)))
            assertEquals(text, EmbeddedLyrics.read(context, Uri.fromFile(m4a)))
            assertEquals("内嵌歌词", MusicLyricsRepository(context).read(media(m4a)).lyrics.lines.first().text)
            m4a.writeBytes(atom("ftyp", "M4A ".toByteArray() + ByteArray(4)) +
                atom("moov", atom("udta", atom("meta", ByteArray(4) + atom("ilst", atom("©lyr", atom("data",
                    ByteBuffer.allocate(8).putInt(2).putInt(0).array() + text.toByteArray(Charsets.UTF_16BE))))))))
            assertEquals(text, EmbeddedLyrics.read(context, Uri.fromFile(m4a)))
        } finally { flac.delete(); m4a.delete() }
    }

    @Test fun embeddedId3UsltAndLargeLyricsRejectWithoutOverwriting() = runBlocking {
        val mp3 = File.createTempFile("tag-only", ".mp3", app.cacheDir)
        val lyric = File.createTempFile("large-lyrics", ".lrc", app.cacheDir)
        try {
            val body = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + "[00:01]USLT歌词".toByteArray()
            val frame = "USLT".toByteArray() + ByteBuffer.allocate(4).putInt(body.size).array() + ByteArray(2) + body
            val size = frame.size
            mp3.writeBytes("ID3".toByteArray() + byteArrayOf(3, 0, 0, (size shr 21 and 127).toByte(), (size shr 14 and 127).toByte(),
                (size shr 7 and 127).toByte(), (size and 127).toByte()) + frame)
            assertEquals("[00:01]USLT歌词", EmbeddedLyrics.read(context, Uri.fromFile(mp3)))
            lyric.writeBytes(ByteArray(Lyrics.MAX_BYTES + 1) { 65 })
            assertTrue(runCatching { MusicLyricsRepository(context).import(media(mp3), Uri.fromFile(lyric)) }.isFailure)
            assertEquals("USLT歌词", MusicLyricsRepository(context).read(media(mp3)).lyrics.lines.first().text)
        } finally { mp3.delete(); lyric.delete() }
    }
}
