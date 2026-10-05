package yy.daniel.yyplayerforandroid.playback

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class MusicLyrics(val lyrics: Lyrics = Lyrics(), val source: String? = null, val warning: String? = null)

/** Persisted per-song overrides; every caller keys projection by the media URI and request revision. */
class MusicLyricsRepository(private val context: Context) {
    private val storage = context.getSharedPreferences("music_lyrics_v1", 0)
    internal val documents = MusicDocuments(context)
    val centered: Boolean get() = storage.getBoolean("centered", false)
    val offsetMs: Long get() = storage.getLong("offset_ms", 0).coerceIn(-600_000, 600_000)
    fun setCentered(value: Boolean) { storage.edit { putBoolean("centered", value) } }
    fun setOffset(value: Long) { storage.edit { putLong("offset_ms", value.coerceIn(-600_000, 600_000)) } }

    suspend fun read(media: LocalMedia): MusicLyrics = withContext(Dispatchers.IO) {
        val embedded = try { EmbeddedLyrics.read(context, media.uri)?.let { MusicLyrics(Lyrics.parse(it), "内嵌歌词") } ?: MusicLyrics() }
            catch (e: CancellationException) { throw e } catch (_: Exception) { MusicLyrics() }
        val override = storage.getString("lyric:${media.uri}", null)?.let(Uri::parse)
        val automatic = if (override == null) documents.sibling(media, listOf(media.title.substringBeforeLast('.', media.title) + ".lrc")) else null
        val uri = override ?: automatic ?: return@withContext embedded
        try { MusicLyrics(readFile(uri), if (override != null) "手动添加" else "同目录歌词") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { embedded.copy(warning = e.message ?: "无法读取歌词，可重新添加") }
    }

    suspend fun import(media: LocalMedia, uri: Uri): MusicLyrics = withContext(Dispatchers.IO) {
        val lyrics = readFile(uri)
        currentCoroutineContext().ensureActive()
        storage.edit { putString("lyric:${media.uri}", uri.toString()) }
        MusicLyrics(lyrics, "手动添加")
    }
    suspend fun clear(media: LocalMedia): MusicLyrics = withContext(Dispatchers.IO) {
        storage.edit { remove("lyric:${media.uri}") }; read(media)
    }
    private suspend fun readFile(uri: Uri): Lyrics {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer); if (count < 0) break
                require(output.size() + count <= Lyrics.MAX_BYTES) { "歌词文件超过 1MB" }; output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("无法读取歌词，请重新选择文件")
        return Lyrics.parse(Lyrics.decode(bytes))
    }
}
