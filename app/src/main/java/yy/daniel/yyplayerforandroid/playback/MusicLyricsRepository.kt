package yy.daniel.yyplayerforandroid.playback

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest

data class MusicLyrics(val lyrics: Lyrics = Lyrics(), val source: String? = null, val warning: String? = null)

/** Persisted per-song overrides; every caller keys projection by the media URI and request revision. */
class MusicLyricsRepository(private val context: Context) {
    private val storage = context.getSharedPreferences("music_lyrics_v1", 0)
    internal val documents = MusicDocuments(context)
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    val centered: Boolean get() = storage.getBoolean("centered", false)
    val offsetMs: Long get() = storage.getLong("offset_ms", 0).coerceIn(-600_000, 600_000)
    fun setCentered(value: Boolean) { storage.edit { putBoolean("centered", value) } }
    fun setOffset(value: Long) { storage.edit { putLong("offset_ms", value.coerceIn(-600_000, 600_000)) } }

    suspend fun read(media: LocalMedia): MusicLyrics = withContext(Dispatchers.IO) {
        val embedded = try { if (media.isOnline) MusicLyrics() else EmbeddedLyrics.read(context, media.uri)?.let { MusicLyrics(Lyrics.parse(it), "内嵌歌词") } ?: MusicLyrics() }
            catch (e: CancellationException) { throw e } catch (_: Exception) { MusicLyrics() }
        val override = storage.getString("lyric:${media.identity}", null)?.let(Uri::parse)
        val automatic = if (override == null && !media.isOnline) documents.sibling(media, listOf(media.title.substringBeforeLast('.', media.title) + ".lrc")) else null
        val cached = onlineFile(media).takeIf { it.exists() }?.let(Uri::fromFile)
        val uri = override ?: automatic ?: cached ?: return@withContext embedded
        try { MusicLyrics(readFile(uri), if (override != null) "手动添加" else if (automatic != null) "同目录歌词" else "联网歌词（已保存）") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { embedded.copy(warning = e.message ?: "无法读取歌词，可重新添加") }
    }

    suspend fun import(media: LocalMedia, uri: Uri): MusicLyrics = withContext(Dispatchers.IO) {
        val lyrics = readFile(uri)
        currentCoroutineContext().ensureActive()
        storage.edit { putString("lyric:${media.identity}", uri.toString()) }
        MusicLyrics(lyrics, "手动添加")
    }
    suspend fun clear(media: LocalMedia): MusicLyrics = withContext(Dispatchers.IO) {
        storage.edit { remove("lyric:${media.identity}") }; onlineFile(media).delete(); _revision.value++; read(media)
    }
    private fun onlineFile(media: LocalMedia): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(media.identity.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "online-lyrics"), "$hash.lrc")
    }
    suspend fun saveOnline(media: LocalMedia, text: String, selected: Boolean = false): MusicLyrics = withContext(Dispatchers.IO) {
        val parsed = Lyrics.parse(text); require(parsed.lines.isNotEmpty() || parsed.plain.isNotBlank()) { "候选歌词为空" }
        val destination = onlineFile(media); destination.parentFile!!.mkdirs()
        val temp = File.createTempFile("lyrics-", ".tmp", destination.parentFile)
        try {
            temp.writeText(text); currentCoroutineContext().ensureActive()
            java.nio.file.Files.move(temp.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            if (selected) storage.edit { putString("lyric:${media.identity}", Uri.fromFile(destination).toString()) }
            _revision.value++
        } finally { temp.delete() }
        MusicLyrics(parsed, "联网歌词（已保存）")
    }
    suspend fun cachedOnline(media: LocalMedia): String? = withContext(Dispatchers.IO) { onlineFile(media).takeIf { it.exists() }?.readText() }
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
