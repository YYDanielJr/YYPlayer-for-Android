package yy.daniel.yyplayerforandroid.online

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.InputStream
import java.net.HttpURLConnection

/** Progressive HTTPS audio with Range and host-scoped headers, on Media3's loading thread. */
@UnstableApi
internal class OnlineAudioDataSource(private val permitted: () -> Boolean,
    private val headers: (String) -> Map<String, String>) : BaseDataSource(true) {
    private var connection: HttpURLConnection? = null
    private var stream: InputStream? = null
    private var uri: Uri? = null
    private var remaining = C.LENGTH_UNSET.toLong()
    private var opened = false
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        check(permitted()) { "联网音乐已关闭" }
        uri = dataSpec.uri
        secureUri(dataSpec.uri.toString())
        val range = if (dataSpec.position > 0 || dataSpec.length != C.LENGTH_UNSET.toLong()) {
            val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) "" else Math.addExact(dataSpec.position, dataSpec.length - 1).toString()
            mapOf("Range" to "bytes=${dataSpec.position}-$end")
        } else emptyMap()
        try {
            val conn = connect(ApiRequest(dataSpec.uri.toString(), headers = headers(dataSpec.uri.toString()) + range), 15000, permitted, { connection = it })
            stream = conn.inputStream
            if (conn.responseCode == 200 && dataSpec.position > 0) {
                var skip = dataSpec.position; val buffer = ByteArray(8192)
                while (skip > 0) {
                    check(permitted()) { "联网音乐已关闭" }
                    val count = stream!!.read(buffer, 0, minOf(skip, buffer.size.toLong()).toInt())
                    require(count >= 0) { "播放位置超出响应" }; skip -= count
                }
            }
            remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else conn.contentLengthLong.let {
                if (it < 0) C.LENGTH_UNSET.toLong() else (it - if (conn.responseCode == 200) dataSpec.position else 0).coerceAtLeast(0)
            }
            opened = true; transferStarted(dataSpec)
            return remaining
        } catch (e: Throwable) { close(); throw e }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        check(permitted()) { "联网音乐已关闭" }
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = stream!!.read(buffer, offset, if (remaining < 0) length else minOf(length.toLong(), remaining).toInt())
        if (count < 0) return C.RESULT_END_OF_INPUT
        if (remaining >= 0) remaining -= count
        bytesTransferred(count); return count
    }
    override fun getUri(): Uri? = uri
    override fun close() {
        try { stream?.close() } finally {
            stream = null; connection?.disconnect(); connection = null; uri = null
            if (opened) { opened = false; transferEnded() }
        }
    }
    class Factory(private val permitted: () -> Boolean, private val headers: (String) -> Map<String, String>) : DataSource.Factory {
        override fun createDataSource(): DataSource = OnlineAudioDataSource(permitted, headers)
    }
}
