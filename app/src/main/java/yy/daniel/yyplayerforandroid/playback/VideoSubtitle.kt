package yy.daniel.yyplayerforandroid.playback

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class VideoSubtitle(val uri: Uri, val name: String, val mimeType: String)

/** Only an explicitly chosen, bounded subtitle is copied. Original URI remains the import identity. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal suspend fun importVideoSubtitle(context: Context, uri: Uri, useLibass: Boolean = false): VideoSubtitle = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: error("无法读取字幕文件名")
    val extension = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    val mime = when (extension) {
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        else -> error("请选择 SRT、VTT、ASS 或 SSA 字幕")
    }
    val directory = File(context.cacheDir, "video-subtitles").apply { mkdirs() }
    val file = File(directory, "${UUID.randomUUID()}.$extension")
    try {
        resolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(8192); var size = 0
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    require(size <= 4 * 1024 * 1024) { "字幕不能超过 4 MB" }
                    output.write(buffer, 0, count)
                }
                require(size > 0) { "字幕文件为空" }
            }
        } ?: error("无法打开字幕")
        // libass accepts valid drawing-only ASS scripts that Media3's text cue parser rejects.
        // Those are validated by the native sub-add ACK; retain the same bounded input copy.
        if (!useLibass || mime != "text/x-ssa") validateVideoSubtitle(file.readBytes(), mime)
        VideoSubtitle(file.toUri(), name, mime)
    } catch (failure: Throwable) { file.delete(); throw failure }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun validateVideoSubtitle(bytes: ByteArray, mime: String) {
    val parser = androidx.media3.extractor.text.DefaultSubtitleParserFactory()
        .create(androidx.media3.common.Format.Builder().setSampleMimeType(mime).build())
    var count = 0
    parser.parse(bytes, androidx.media3.extractor.text.SubtitleParser.OutputOptions.allCues()) { result ->
        count += result.cues.size
        require(count <= 20_000) { "字幕条目过多（最多 20000 条）" }
    }
    require(count > 0) { "未找到可播放的字幕，请检查格式及文本编码" }
}
