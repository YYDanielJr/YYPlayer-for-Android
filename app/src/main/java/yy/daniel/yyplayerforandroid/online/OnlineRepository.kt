package yy.daniel.yyplayerforandroid.online

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import androidx.core.content.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import yy.daniel.yyplayerforandroid.playback.*
import java.io.File
import java.security.MessageDigest

data class OnlineSong(val providerId: String, val source: String, val id: String, val title: String,
    val artist: String, val album: String, val lyricId: String, val durationMs: Long?,
    val picId: String = "", val artworkUrl: String = "", val availableQualities: Set<String>? = null,
    val variables: Map<String, String> = emptyMap())
data class ResolvedAudio(val url: String, val reportedQuality: String, val headers: Map<String, Map<String, String>>,
    val reportedSize: Long? = null, val reportedFormat: String? = null)

data class QualityOption(val quality: ApiQuality, val available: Boolean, val detail: String)
data class DownloadResult(val media: LocalMedia, val lyricSaved: Boolean, val warning: String?)

/** API operations are independent of Compose and the playback engine; gates also cover direct callers. */
class OnlineRepository(private val context: Context, private val musicAllowed: () -> Boolean,
    private val lyricsAllowed: () -> Boolean) {
    private val directory = File(context.noBackupFilesDir, "online-api")
    private val configFile = File(directory, "providers.json")
    private val _config = MutableStateFlow<ApiConfig?>(null)
    val config = _config.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val settings = context.getSharedPreferences("online_api_v1", 0)
    private val requestTimes = mutableMapOf<String, ArrayDeque<Long>>()
    private val resolvedCache = mutableMapOf<String, Pair<Long, ResolvedAudio>>()
    private val _downloadDirectory = MutableStateFlow(settings.getString("download_tree", null))
    val downloadDirectory = _downloadDirectory.asStateFlow()
    val downloadTree: Uri? get() = settings.getString("download_tree", null)?.let(Uri::parse)

    suspend fun load() = withContext(Dispatchers.IO) {
        try {
            synchronized(resolvedCache) { resolvedCache.clear() }
            _config.value = ApiConfig.parse(if (configFile.exists()) configFile.inputStream().use { it.readBytesLimited(ApiConfig.MAX_BYTES).toString(Charsets.UTF_8) }
                else context.assets.open("online-providers.json").bufferedReader().use { it.readText() })
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { _message.value = "API 配置无法读取，请导入有效配置" }
    }
    suspend fun importConfig(uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(ApiConfig.MAX_BYTES).toString(Charsets.UTF_8) } ?: error("无法读取配置")
        val parsed = ApiConfig.parse(text)
        directory.mkdirs()
        val temporary = File(directory, "providers.tmp")
        try {
            temporary.writeText(text); currentCoroutineContext().ensureActive()
            java.nio.file.Files.move(temporary.toPath(), configFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            synchronized(requestTimes) { requestTimes.clear() }
            synchronized(resolvedCache) { resolvedCache.clear() }
            _config.value = parsed; _message.value = "已导入 ${parsed.providers.size} 个 API"
        } finally { temporary.delete() }
    }
    fun rememberDownloadTree(uri: Uri?) {
        settings.edit { if (uri == null) remove("download_tree") else putString("download_tree", uri.toString()) }
        _downloadDirectory.value = uri?.toString()
    }
    fun provider(id: String) = _config.value?.providers?.firstOrNull { it.id == id } ?: error("API 配置已变化，请重新搜索")

    suspend fun search(providerId: String, source: String, query: String, page: Int = 1, lyrics: Boolean = false): List<OnlineSong> {
        val permitted = if (lyrics) lyricsAllowed else musicAllowed
        check(permitted()) { "联网功能已关闭" }
        require(query.isNotBlank() && query.length <= 200 && page in 1..100)
        val p = provider(providerId); require(source in p.sources)
        val e = (if (lyrics) p.lyricSearch else p.search) ?: error("此 API 不支持该搜索")
        val response = call(p, e, variables(p, query = query, source = source, page = page), permitted)
        val rows = field(response, e.response.optString("items", "")) as? JSONArray ?: error("搜索响应格式不匹配")
        require(rows.length() <= 200) { "单次返回条目过多" }
        val fields = e.response.getJSONObject("fields")
        fun text(row: Any, name: String) = fieldText(row, fields.optString(name, name)).take(512)
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = text(row, "id"); val title = text(row, "title")
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            val rawDuration = text(row, "duration").toDoubleOrNull()
            val duration = rawDuration?.takeIf { it.isFinite() && it >= 0 && it <= 86400000 }?.let {
                (it * if (e.response.optString("durationUnit", "seconds") == "milliseconds") 1 else 1000).toLong()
            }
            val available = fields.optString("qualities").takeIf { it.isNotBlank() }?.let { path ->
                when (val list = field(row, path)) {
                    is JSONArray -> (0 until list.length()).map { list.optString(it) }.toSet()
                    is String -> list.split(',').map(String::trim).toSet()
                    else -> null
                }
            }
            OnlineSong(p.id, text(row, "source").ifBlank { source }, id, title, text(row, "artist"), text(row, "album"),
                text(row, "lyricId").ifBlank { id }, duration, text(row, "picId"), text(row, "artworkUrl"), available,
                p.trackFields.mapValues { fieldText(row, it.value).take(8192) })
        }.distinctBy { "${it.source}:${it.id}" }.let { result ->
            if (lyrics) result.sortedWith(compareByDescending<OnlineSong> { it.title.trim().equals(query.trim(), true) }.thenBy { it.title }) else result
        }
    }
    suspend fun resolve(song: OnlineSong, quality: ApiQuality, download: Boolean = false): ResolvedAudio {
        check(musicAllowed()) { "联网音乐已关闭" }
        val p = provider(song.providerId); require(quality in p.qualities) { "音质配置已变化" }
        require(quality.sources.isEmpty() || song.source in quality.sources) { "此来源不支持该音质" }
        require(song.availableQualities == null || quality.id in song.availableQualities || quality.value in song.availableQualities) { "此歌曲不支持该音质" }
        val key = "${song.providerId}:${song.source}:${song.id}:${quality.id}:$download"
        synchronized(resolvedCache) { resolvedCache[key]?.takeIf { android.os.SystemClock.elapsedRealtime() - it.first < 60000 }?.second }?.let { return it }
        val e = (if (download) p.download ?: p.stream else p.stream) ?: error("未配置播放地址接口")
        val result = call(p, e, variables(p, song, quality), musicAllowed, quality.parameters)
        val url = fieldText(result, e.response.optString("url", "url")); secureUri(url)
        val report = fieldText(result, e.response.optString("quality", "br"))
        val actual = p.qualities.firstOrNull { it.value == report || it.id == report }
        require(actual == null || actual.id == quality.id) { "此音质不可用，服务实际返回 ${actual?.name}，请选择对应音质" }
        val size = fieldText(result, e.response.optString("size", "size")).toDoubleOrNull()?.let {
            val factor = if (e.response.optString("sizeUnit", "bytes") == "KB") 1024 else 1
            (it * factor).takeIf { n -> n.isFinite() && n > 0 && n <= MAX_DOWNLOAD }?.toLong()
        }
        return ResolvedAudio(url, report, p.mediaHeaders, size,
            fieldText(result, e.response.optString("format", "format")).takeIf(String::isNotBlank)).also {
            synchronized(resolvedCache) {
                if (resolvedCache.size >= 128) resolvedCache.clear()
                resolvedCache[key] = android.os.SystemClock.elapsedRealtime() to it
            }
        }
    }
    suspend fun qualityOptions(song: OnlineSong, download: Boolean): List<QualityOption> {
        val p = provider(song.providerId)
        return p.qualities.map { q ->
            try {
                val audio = resolve(song, q, download)
                val actualKnown = audio.reportedQuality == q.value || audio.reportedQuality == q.id
                QualityOption(q, true, if (actualKnown) "服务返回此档位；格式以下载文件为准" else "可获取地址；服务未确认此档位")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { QualityOption(q, false, e.message ?: "此歌曲暂不提供该音质") }
        }
    }
    suspend fun lyric(song: OnlineSong): String {
        check(lyricsAllowed()) { "联网歌词已关闭" }
        val p = provider(song.providerId); val e = p.lyric ?: error("未配置歌词接口")
        val response = call(p, e, variables(p, song), lyricsAllowed)
        val text = fieldText(response, e.response.optString("lyric", "lyric"))
        require(text.isNotBlank()) { "此候选暂无歌词" }; Lyrics.parse(text)
        val translation = e.response.optString("translation").takeIf(String::isNotBlank)?.let { fieldText(response, it) }.orEmpty()
        return if (translation.isBlank()) text else mergeTranslatedLyrics(text, translation)
    }

    /** Download only on an explicit music action, with bounded validation and a durable local cover. */
    suspend fun artwork(song: OnlineSong): Uri? = withContext(Dispatchers.IO) {
        check(musicAllowed()) { "联网音乐已关闭" }
        val p = provider(song.providerId)
        val dir = File(context.filesDir, "online-artwork").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256").digest("${song.providerId}:${song.source}:${song.id}:${song.picId}".toByteArray()).joinToString("") { "%02x".format(it) }
        val target = File(dir, "$hash.cover")
        if (target.isFile) return@withContext target.toUri()
        val url = if (song.artworkUrl.isNotBlank()) song.artworkUrl else {
            val e = p.artwork ?: return@withContext null
            if (song.picId.isBlank()) return@withContext null
            val response = call(p, e, variables(p, song), musicAllowed)
            fieldText(response, e.response.optString("url", "url"))
        }
        if (url.isBlank()) return@withContext null
        val host = secureUri(url).host
        val bytes = network(ApiRequest(url, headers = p.mediaHeaders[host].orEmpty()), p.timeoutMs, musicAllowed) {
            readBounded(it, 8 * 1024 * 1024, musicAllowed)
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth in 1..8192 && options.outHeight in 1..8192) { "专辑图格式或尺寸不合法" }
        var sample = 1
        while (options.outWidth / sample > 1024 || options.outHeight / sample > 1024) sample *= 2
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("专辑图无法解码")
        val temp = File.createTempFile(hash, ".tmp", dir)
        try {
            temp.outputStream().use { check(image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)) }
            currentCoroutineContext().ensureActive(); check(musicAllowed())
            java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            target.toUri()
        } finally { image.recycle(); temp.delete() }
    }
    suspend fun automaticLyrics(song: OnlineSong, media: LocalMedia, lyrics: MusicLyricsRepository): String? {
        if (!lyricsAllowed()) return null
        return try { if (provider(song.providerId).lyric != null) lyrics.saveOnline(media, lyric(song)); null }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { "歌曲已就绪，歌词暂未获取，可稍后手动搜索" }
    }

    suspend fun download(song: OnlineSong, quality: ApiQuality, tree: Uri?, lyrics: MusicLyricsRepository,
        onProgress: (Long, Long?) -> Unit = { _, _ -> }): DownloadResult = withContext(Dispatchers.IO) {
        val resolved = resolve(song, quality, download = true)
        val target = DownloadTarget(context, tree)
        try {
            val uri = target.create()
            val prefix = java.io.ByteArrayOutputStream()
            network(ApiRequest(resolved.url, headers = resolved.headers[secureUri(resolved.url).host].orEmpty()),
                provider(song.providerId).timeoutMs, musicAllowed) { connection ->
                val length = connection.contentLengthLong.takeIf { it >= 0 }
                require(length == null || length in 1..MAX_DOWNLOAD) { "歌曲大小不合法或超过 512MB" }
                connection.inputStream.use { input ->
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        val buffer = ByteArray(64 * 1024); var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive(); check(musicAllowed()) { "联网音乐已关闭" }
                            val count = input.read(buffer); if (count < 0) break
                            total += count; require(total <= MAX_DOWNLOAD) { "歌曲超过 512MB" }
                            if (prefix.size() < 256 * 1024) prefix.write(buffer, 0, minOf(count, 256 * 1024 - prefix.size()))
                            output.write(buffer, 0, count); onProgress(total, length)
                        }
                        require(total > 0 && (length == null || length == total)) { "下载不完整" }
                    } ?: error("下载目录不可写")
                }
            }
            currentCoroutineContext().ensureActive(); check(musicAllowed()) { "联网音乐已关闭" }
            val format = AudioFileFormat.detect(prefix.toByteArray()) ?: error("无法识别音频文件格式，下载未保存；请检查 API 返回内容")
            val destination = target.finish(downloadBaseName(song.title, song.artist), format, song)
            var cover: Uri? = null
            var coverWarning: String? = null
            try { cover = artwork(song) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { coverWarning = "专辑图暂未获取" }
            if (cover != null) runCatching { target.sibling("jpg", File(cover.path!!).readBytes()) }
            val media = LocalMedia(destination, target.name, MediaKind.AUDIO, format.mime,
                song.title, song.artist.ifBlank { null }, song.album.ifBlank { null }, song.durationMs, metadataRead = true,
                artworkUri = cover, appOwnedDownload = target.appOwned)
            val lyricWarning = automaticLyrics(song, media, lyrics)
            val text = lyrics.cachedOnline(media)
            var siblingSaved = false
            if (text != null) try { siblingSaved = target.sibling("lrc", text.toByteArray()) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { /* Durable internal lyrics remain. */ }
            val info = listOfNotNull(lyricWarning, coverWarning,
                if (text != null && !siblingSaved) "歌词已保存在应用内" else null,
                "已下载 ${format.extension.uppercase()}${format.bitDepth?.let { " · ${it}bit" }.orEmpty()}")
            currentCoroutineContext().ensureActive()
            target.commit()
            DownloadResult(media, text != null, info.joinToString("；"))
        } finally { target.cleanup() }
    }

    private suspend fun call(p: ApiProvider, e: ApiEndpoint, variables: Map<String, String>, permitted: () -> Boolean, overrides: Map<String, String> = emptyMap()): Any {
        check(permitted()) { "联网功能已关闭" }
        val endpointUrl = template(e.url, variables, url = true)
        val endpointUri = secureUri(endpointUrl)
        val origin = secureUri(p.baseUrl)
        require(endpointUri.host in p.apiHosts && (endpointUri.port == -1 || endpointUri.port == origin.port)) { "操作地址未列入 apiHosts" }
        val parameters = (e.parameters + overrides).mapValues { template(it.value, variables) }
        val encoded = parameters.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val headers = (p.headers + e.headers).mapValues { template(it.value, variables) }
        headerMap(JSONObject(headers))
        val request = when (e.encoding) {
            "query" -> ApiRequest(endpointUrl + if (encoded.isEmpty()) "" else (if ('?' in endpointUrl) "&" else "?") + encoded, e.method, headers)
            "form" -> ApiRequest(endpointUrl, e.method, headers + ("Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"), encoded.toByteArray())
            else -> ApiRequest(endpointUrl, e.method, headers + ("Content-Type" to "application/json; charset=UTF-8"), (e.body?.let { renderBody(it, variables) } ?: JSONObject(parameters)).toString().toByteArray())
        }
        synchronized(requestTimes) {
            val now = android.os.SystemClock.elapsedRealtime()
            val times = requestTimes.getOrPut(p.id) { ArrayDeque() }
            while (times.isNotEmpty() && now - times.first() >= p.windowSeconds * 1000L) times.removeFirst()
            require(times.size < p.requestsPerWindow) { "请求较频繁，请稍后再试" }; times.addLast(now)
        }
        return network(request, p.timeoutMs, permitted) { connection ->
            val text = readBounded(connection, 2 * 1024 * 1024, permitted).toString(Charsets.UTF_8).trimStart('\uFEFF')
            require(!text.trimStart().startsWith('<')) { "服务返回网页验证，当前 API 不可用" }
            if (e.response.optString("mode", "json") == "text") text else { checkJsonDepth(text); JSONTokener(text).nextValue() }
        }
    }
    private fun variables(p: ApiProvider, song: OnlineSong? = null, quality: ApiQuality? = null, query: String = "",
        source: String = song?.source.orEmpty(), page: Int = 1) = p.variables + quality?.variables.orEmpty() + song?.variables.orEmpty() + mapOf(
        "query" to query, "source" to source, "page" to page.toString(), "limit" to p.pageSize.toString(), "id" to song?.id.orEmpty(),
        "lyricId" to song?.lyricId.orEmpty(), "picId" to song?.picId.orEmpty(), "album" to song?.album.orEmpty(),
        "title" to song?.title.orEmpty(), "artist" to song?.artist.orEmpty(), "quality" to quality?.value.orEmpty())
    companion object { const val MAX_DOWNLOAD = 512L * 1024 * 1024 }
}

internal fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val count = read(buffer); if (count < 0) break; require(output.size() + count <= limit) { "文件过大" }; output.write(buffer, 0, count) }
    return output.toByteArray()
}

/** Substitute string leaves only; numbers, booleans, arrays and JSON escaping keep their types. */
internal fun renderBody(node: Any, variables: Map<String, String>): Any = when (node) {
    is JSONObject -> JSONObject().apply { node.keys().forEach { key -> put(key, renderBody(node.get(key), variables)) } }
    is JSONArray -> JSONArray().apply { repeat(node.length()) { put(renderBody(node.get(it), variables)) } }
    is String -> template(node, variables)
    else -> node
}

internal fun mergeTranslatedLyrics(original: String, translation: String): String {
    val first = Lyrics.parse(original); val second = Lyrics.parse(translation)
    if (first.lines.isEmpty()) return "$original\n\n$translation".also { Lyrics.parse(it) }
    val translated = second.lines.groupBy { it.milliseconds }
    return first.lines.groupBy { it.milliseconds }.toSortedMap().entries.joinToString("\n") { (time, lines) ->
        val label = java.lang.String.format(java.util.Locale.ROOT, "[%02d:%02d.%03d]", time / 60000, time / 1000 % 60, time % 1000)
        label + (lines + translated[time].orEmpty()).map { it.text }.filter(String::isNotBlank).distinct().joinToString(" / ")
    }.also { Lyrics.parse(it) }
}
