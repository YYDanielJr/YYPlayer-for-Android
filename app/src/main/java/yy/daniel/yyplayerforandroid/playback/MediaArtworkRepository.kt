package yy.daniel.yyplayerforandroid.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.scale
import androidx.core.graphics.get
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal data class MediaDetails(val artwork: Bitmap? = null, val artist: String? = null,
    val album: String? = null, val durationMs: Long? = null, val width: Int? = null, val height: Int? = null,
    val title: String? = null, val coverColor: Color = Color(0xFF7089A2), val loaded: Boolean = false)

internal object ArtworkCache {
    var revision by mutableIntStateOf(0)
    private val workers = Semaphore(2)
    private val entries = object : LruCache<String, MediaDetails>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: MediaDetails) = (value.artwork?.allocationByteCount ?: 0) + 512
    }
    suspend fun load(context: Context, media: LocalMedia): MediaDetails = withContext(Dispatchers.IO) {
        entries.get(media.uri.toString()) ?: workers.withPermit {
            entries.get(media.uri.toString()) ?: readWithSiblingCover(context, media).also { entries.put(media.uri.toString(), it) }
        }
    }
    fun clear() { entries.evictAll(); revision++ }
    fun peek(media: LocalMedia?) = media?.let { entries.get(it.uri.toString()) }
    private suspend fun readWithSiblingCover(context: Context, media: LocalMedia): MediaDetails {
        val base = if (media.isOnline) MediaDetails(artist = media.artist, album = media.album, title = media.songTitle, durationMs = media.durationMs, loaded = true) else read(context, media)
        val details = base.let { it.copy(artist = it.artist ?: media.artist, album = it.album ?: media.album,
            title = it.title ?: media.songTitle, durationMs = it.durationMs ?: media.durationMs) }
        if (media.kind != MediaKind.AUDIO || details.artwork != null) return details
        val stem = media.title.substringBeforeLast('.', media.title)
        val ownCover = media.artworkUri?.takeIf { it.scheme == "file" && it.path?.startsWith(java.io.File(context.filesDir, "online-artwork").absolutePath + "/") == true }
        val uri = ownCover ?: if (media.isOnline) null else MusicDocuments(context).sibling(media, listOf("$stem.jpg", "$stem.png", "cover.jpg", "cover.png", "folder.jpg"))
        if (uri == null) return details
        val art = runCatching { context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytesBounded(16 * 1024 * 1024)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1; while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } }.getOrNull() ?: return details
        return details.copy(artwork = art, coverColor = coverColor(art))
    }
    private fun read(context: Context, media: LocalMedia): MediaDetails {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, media.uri)
            val art = if (media.kind == MediaKind.VIDEO) {
                // API 26 has no bounded frame API. Keep the icon fallback there rather than decode a 4K bitmap.
                if (Build.VERSION.SDK_INT >= 27) runCatching { retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512) }.getOrNull() else null
            } else {
                retriever.embeddedPicture?.takeIf { it.size <= 8 * 1024 * 1024 }?.let { bytes ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val options = BitmapFactory.Options().apply {
                        var sample = 1
                        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                        inSampleSize = sample
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                }
            }
            MediaDetails(art,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() },
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank),
                art?.let(::coverColor) ?: Color(0xFF7089A2), loaded = true)
        } catch (_: Exception) { MediaDetails(loaded = true) } finally { runCatching { retriever.release() } }
    }
}

private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val result = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val count = read(buffer); if (count < 0) break; require(result.size() + count <= limit); result.write(buffer, 0, count) }
    return result.toByteArray()
}

/** Quantised population with a mild chroma weight, clamped for comfortable ambient surfaces. IO only. */
private fun coverColor(bitmap: Bitmap): Color {
    val sample = bitmap.scale(48, 48)
    val buckets = Array(18) { FloatArray(4) }; val hsv = FloatArray(3)
    for (y in 0 until 48) for (x in 0 until 48) {
        val pixel = sample[x, y]
        android.graphics.Color.colorToHSV(pixel, hsv)
        if (android.graphics.Color.alpha(pixel) < 128 || hsv[2] < .22f || hsv[1] < .16f) continue
        // Aggregate hue so a smooth colour gradient beats a large, nearly neutral shadow block.
        val bucket = buckets[(hsv[0] / 20).toInt().coerceIn(0, 17)]
        val weight = hsv[1] * hsv[1] * hsv[2]
        bucket[0] += weight; bucket[1] += android.graphics.Color.red(pixel) * weight
        bucket[2] += android.graphics.Color.green(pixel) * weight; bucket[3] += android.graphics.Color.blue(pixel) * weight
    }
    if (sample !== bitmap) sample.recycle()
    val selected = buckets.maxBy { it[0] }
    if (selected[0] <= 0) return Color(0xFF7089A2)
    android.graphics.Color.colorToHSV(android.graphics.Color.rgb((selected[1] / selected[0]).toInt(),
        (selected[2] / selected[0]).toInt(), (selected[3] / selected[0]).toInt()), hsv)
    hsv[1] = hsv[1].coerceIn(.18f, .55f); hsv[2] = hsv[2].coerceIn(.42f, .78f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
