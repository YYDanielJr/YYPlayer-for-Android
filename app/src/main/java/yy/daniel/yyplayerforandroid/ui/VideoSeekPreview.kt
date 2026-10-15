package yy.daniel.yyplayerforandroid.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

internal data class VideoPreviewFrame(val requestMs: Long, val bitmap: Bitmap?)

/** Sparse scrub thumbnails only. One independent FD, one decoder worker, bounded memory and queue. */
internal class VideoSeekPreview(context: Context, uri: Uri) : AutoCloseable {
    private val requests = Channel<Long>(Channel.CONFLATED)
    private val latest = AtomicLong(-1)
    private val scope = CoroutineScope(SupervisorJob() + decoderDispatcher)
    private val result = MutableStateFlow<VideoPreviewFrame?>(null)
    val frame = result.asStateFlow()
    init {
        scope.launch {
            var retriever: MediaMetadataRetriever? = null
            var fd: android.content.res.AssetFileDescriptor? = null
            var unavailable = false
            val cache = object : LinkedHashMap<Long, Bitmap?>(12, .75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap?>?) = size > 12
            }
            try {
                for (first in requests) {
                    delay(65) // Coalesce a fast finger; never queue per-move frame extraction.
                    var target = first
                    while (true) { target = requests.tryReceive().getOrNull() ?: break }
                    val bucket = target / 500
                    var bitmap: Bitmap? = null
                    try {
                        if (!unavailable) {
                            if (retriever == null) {
                                fd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                                val source = fd ?: error("Preview source unavailable")
                                retriever = MediaMetadataRetriever().also {
                                    if (source.declaredLength >= 0) it.setDataSource(source.fileDescriptor, source.startOffset, source.declaredLength)
                                    else it.setDataSource(source.fileDescriptor)
                                }
                            }
                            bitmap = if (cache.containsKey(bucket)) cache[bucket] else {
                                val r = retriever
                                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                                val atUs = bucket * 500_000L
                                val decoded = if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(atUs,
                                    MediaMetadataRetriever.OPTION_CLOSEST, 320, 180)
                                else if (w.toLong() * h in 1..2_073_600L) r.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                else null
                                decoded?.let {
                                    val scale = minOf(320f / it.width, 180f / it.height, 1f)
                                    if (scale < 1f) it.scale((it.width * scale).toInt().coerceAtLeast(1),
                                        (it.height * scale).toInt().coerceAtLeast(1), true).also { small -> if (small !== it) it.recycle() }
                                    else it
                                }.also { cache[bucket] = it }
                            }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { unavailable = true }
                    ensureActive()
                    if (latest.get() == target) result.value = VideoPreviewFrame(target, bitmap)
                }
            } finally {
                runCatching { retriever?.release() }; runCatching { fd?.close() }; cache.clear()
            }
        }
    }
    fun request(positionMs: Long?) {
        latest.set(positionMs ?: -1)
        if (positionMs == null) result.value = null else requests.trySend(positionMs)
    }
    override fun close() { latest.set(-1); requests.close(); scope.cancel(); result.value = null }
    companion object { private val decoderDispatcher = Dispatchers.IO.limitedParallelism(1) }
}
