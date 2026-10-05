package yy.daniel.yyplayerforandroid.playback

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class MediaLibraryRepository(context: Context) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences("local_media_v1", Context.MODE_PRIVATE)

    fun load(): List<LocalMedia> {
        val saved = runCatching { JSONArray(preferences.getString("items", "[]")) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until saved.length()) {
                val row = saved.optJSONObject(i) ?: continue
                val uri = runCatching { Uri.parse(row.optString("uri")) }.getOrNull() ?: continue
                if (resolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission ||
                        it.isReadPermission && it.isPersistedTreeCovering(uri) }) continue
                val kind = runCatching { MediaKind.valueOf(row.optString("kind")) }.getOrNull() ?: continue
                add(LocalMedia(uri, row.optString("title", "未命名媒体"), kind, row.optString("mime").takeIf(String::isNotBlank),
                    row.optString("songTitle").takeIf(String::isNotBlank), row.optString("artist").takeIf(String::isNotBlank),
                    row.optString("album").takeIf(String::isNotBlank), row.optLong("durationMs", -1).takeIf { it >= 0 }, row.optBoolean("metadataRead")))
            }
        }
    }

    /** Keeps only URIs for which Android granted a persistable read permission. */
    suspend fun add(uris: List<Uri>, kind: MediaKind): List<LocalMedia> {
        val old = load().associateBy { it.uri.toString() }.toMutableMap()
        uris.forEach { uri ->
            currentCoroutineContext().ensureActive()
            val mime = runCatching { resolver.getType(uri) }.getOrNull()
            val title = queryTitle(resolver, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "未命名媒体"
            old[uri.toString()] = readMusicMetadata(context = appContext, LocalMedia(uri, title, kind, mime))
        }
        val result = old.values.toList()
        currentCoroutineContext().ensureActive()
        save(result)
        return result
    }

    private val appContext = context.applicationContext

    /** Backfill old filename-only rows on IO; cancellable between files, never on the UI looper. */
    suspend fun refreshMetadata(items: List<LocalMedia>): List<LocalMedia> {
        val result = items.map { currentCoroutineContext().ensureActive(); if (it.metadataRead) it else readMusicMetadata(appContext, it) }
        currentCoroutineContext().ensureActive()
        save(result)
        return result
    }

    private fun save(result: List<LocalMedia>) {
        val encoded = JSONArray().apply {
            result.forEach { item -> put(JSONObject().apply {
                put("uri", item.uri.toString())
                put("title", item.title)
                put("kind", item.kind.name)
                put("mime", item.mimeType.orEmpty())
                put("songTitle", item.songTitle.orEmpty()); put("artist", item.artist.orEmpty()); put("album", item.album.orEmpty())
                put("durationMs", item.durationMs ?: -1); put("metadataRead", item.metadataRead)
            }) }
        }
        preferences.edit { putString("items", encoded.toString()) }
    }

    fun remove(media: LocalMedia): List<LocalMedia> {
        val result = load().filterNot { it.uri == media.uri }
        save(result)
        return result
    }

    private fun queryTitle(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf(String::isNotBlank)
}

internal fun readMusicMetadata(context: Context, media: LocalMedia): LocalMedia {
    if (media.kind != MediaKind.AUDIO) return media.copy(metadataRead = true)
    val reader = MediaMetadataRetriever()
    return try {
        reader.setDataSource(context, media.uri)
        fun text(key: Int) = reader.extractMetadata(key)?.trim()?.takeIf(String::isNotBlank)
        media.copy(songTitle = text(MediaMetadataRetriever.METADATA_KEY_TITLE), artist = text(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            album = text(MediaMetadataRetriever.METADATA_KEY_ALBUM),
            durationMs = text(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it >= 0 }, metadataRead = true)
    } catch (_: Exception) { media.copy(metadataRead = true) } finally { runCatching { reader.release() } }
}
