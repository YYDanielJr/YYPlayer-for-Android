package yy.daniel.yyplayerforandroid.playback

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded tag-only reader. No audio decode or whole-song buffering; unsupported tags stay absent. */
internal object EmbeddedLyrics {
    private const val TAG_LIMIT = 16 * 1024 * 1024
    suspend fun read(context: Context, uri: Uri): String? = context.contentResolver.openInputStream(uri)?.use { input ->
        currentCoroutineContext().ensureActive()
        val header = input.readN(4)
        when {
            header.size < 4 -> null
            String(header, Charsets.ISO_8859_1).startsWith("ID3") -> id3(input, header)
            String(header, Charsets.ISO_8859_1) == "fLaC" -> flac(input)
            String(header, Charsets.ISO_8859_1) == "OggS" -> ogg(input, header)
            else -> if (String(header, Charsets.ISO_8859_1) == "RIFF") null else mp4(context, uri)
        }
    }

    /** Seek over mdat; a non-seekable provider returns no embedded tag instead of copying audio. */
    private suspend fun mp4(context: Context, uri: Uri): String? = context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
        FileInputStream(descriptor.fileDescriptor).use { input ->
            val channel = input.channel
            val fileSize = channel.size()
            suspend fun readAt(position: Long, size: Int): ByteArray {
                require(size in 0..Lyrics.MAX_BYTES + 16)
                channel.position(position)
                val bytes = ByteBuffer.allocate(size)
                while (bytes.hasRemaining()) { currentCoroutineContext().ensureActive(); if (channel.read(bytes) < 0) error("标签已截断") }
                return bytes.array()
            }
            if (fileSize < 12 || String(readAt(0, 8), 4, 4, Charsets.ISO_8859_1) != "ftyp") return@use null
            var inspected = 0; var allocated = 0
            suspend fun scan(start: Long, end: Long, depth: Int, lyric: Boolean = false): String? {
                if (depth > 8) return null
                var position = start
                while (position + 8 <= end && ++inspected <= 10_000) {
                    currentCoroutineContext().ensureActive()
                    val header = ByteBuffer.wrap(readAt(position, 8)).order(ByteOrder.BIG_ENDIAN)
                    var size = header.int.toLong() and 0xffffffffL
                    val type = String(header.array(), 4, 4, Charsets.ISO_8859_1)
                    var headerSize = 8
                    if (size == 1L) { size = ByteBuffer.wrap(readAt(position + 8, 8)).long; headerSize = 16 }
                    if (size == 0L) size = end - position
                    require(size >= headerSize && size <= end - position) { "非法 MP4 标签长度" }
                    val dataStart = position + headerSize
                    if (lyric && type == "data" && size > headerSize + 8 && size <= Lyrics.MAX_BYTES + headerSize + 8) {
                        val data = readAt(dataStart, (size - headerSize).toInt()); allocated += data.size; require(allocated <= TAG_LIMIT)
                        val encoding = when (ByteBuffer.wrap(data).int and 0xffffff) { 1 -> Charsets.UTF_8; 2 -> Charsets.UTF_16BE; else -> return null }
                        return String(data, 8, data.size - 8, encoding).trim('\u0000', '\uFEFF').takeIf(String::isNotBlank)
                    }
                    if (type in setOf("moov", "udta", "meta", "ilst", "©lyr")) {
                        val childStart = dataStart + if (type == "meta") 4 else 0
                        if (childStart <= position + size) scan(childStart, position + size, depth + 1, type == "©lyr")?.let { return it }
                    }
                    position += size
                }
                return null
            }
            scan(0, fileSize, 0)
        }
    }

    private suspend fun InputStream.readN(size: Int): ByteArray {
        require(size in 0..TAG_LIMIT)
        val result = ByteArray(size); var read = 0
        while (read < size) { currentCoroutineContext().ensureActive(); val count = read(result, read, size - read); if (count < 0) break; read += count }
        return if (read == size) result else result.copyOf(read)
    }
    private fun be(bytes: ByteArray, start: Int, count: Int): Int {
        var value = 0L
        for (i in start until start + count) value = (value shl 8) or (bytes[i].toLong() and 255)
        require(value <= TAG_LIMIT) { "标签超过大小限制" }; return value.toInt()
    }
    private fun sync(bytes: ByteArray, start: Int): Int {
        var value = 0
        for (i in start until start + 4) { require(bytes[i].toInt() and 128 == 0); value = (value shl 7) or (bytes[i].toInt() and 127) }
        require(value <= TAG_LIMIT); return value
    }
    private suspend fun id3(input: InputStream, header: ByteArray): String? {
        val version = header[3].toInt(); if (version !in 3..4) return null
        val rest = input.readN(6); if (rest.size != 6) return null
        // Unsynchronised/compressed/encrypted variants need a separate parser; do not misread them.
        if (rest[1].toInt() and 0x80 != 0) return null
        val bytes = input.readN(sync(rest, 2)); var p = 0
        if (rest[1].toInt() and 0x40 != 0 && bytes.size >= 4) p = if (version == 4) sync(bytes, 0) else be(bytes, 0, 4) + 4
        while (p + 10 <= bytes.size) {
            currentCoroutineContext().ensureActive()
            val id = String(bytes, p, 4, Charsets.ISO_8859_1)
            val size = if (version == 4) sync(bytes, p + 4) else be(bytes, p + 4, 4)
            val flags = bytes[p + 9].toInt() and 255; p += 10
            if (size <= 0 || p + size > bytes.size) break
            if (flags == 0 && size <= Lyrics.MAX_BYTES && (id == "USLT" || id == "TXXX")) {
                val data = bytes.copyOfRange(p, p + size)
                val encoding = data[0].toInt(); val start = if (id == "USLT") 4 else 1
                if (start < data.size) {
                    val terminator = if (encoding == 1 || encoding == 2) 2 else 1; var end = start
                    while (end + terminator <= data.size) {
                        if (data[end] == 0.toByte() && (terminator == 1 || data[end + 1] == 0.toByte())) break
                        end += terminator
                    }
                    val charset = when (encoding) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }
                    val description = String(data, start, (end - start).coerceAtLeast(0), charset)
                    if (end + terminator <= data.size && (id == "USLT" || description.equals("LYRICS", true) || description.equals("UNSYNCEDLYRICS", true))) {
                        return String(data, end + terminator, data.size - end - terminator, charset).trim('\u0000', '\uFEFF').takeIf(String::isNotBlank)
                    }
                }
            }
            p += size
        }
        return null
    }
    private suspend fun flac(input: InputStream): String? {
        var total = 0
        repeat(256) {
            val h = input.readN(4); if (h.size < 4) return null
            val size = be(h, 1, 3); total += size; require(total <= TAG_LIMIT)
            val data = input.readN(size); if (data.size != size) return null
            if (h[0].toInt() and 127 == 4) vorbis(data)?.let { return it }
            if (h[0].toInt() and 128 != 0) return null
        }
        return null
    }
    private suspend fun ogg(input: InputStream, initial: ByteArray): String? {
        var header = initial; var total = 0; val packet = java.io.ByteArrayOutputStream()
        repeat(256) {
            if (String(header, Charsets.ISO_8859_1) != "OggS") return null
            val page = input.readN(23); if (page.size != 23) return null
            val lacing = input.readN(page[22].toInt() and 255)
            for (lace in lacing) {
                val count = lace.toInt() and 255; total += count; require(total <= TAG_LIMIT)
                val data = input.readN(count); if (data.size != count) return null; packet.write(data)
                if (count < 255) {
                    val bytes = packet.toByteArray(); packet.reset()
                    val offset = when {
                        bytes.size >= 8 && String(bytes, 0, 8, Charsets.ISO_8859_1) == "OpusTags" -> 8
                        bytes.size >= 7 && bytes[0] == 3.toByte() && String(bytes, 1, 6, Charsets.ISO_8859_1) == "vorbis" -> 7
                        else -> -1
                    }
                    if (offset >= 0) return vorbis(bytes.copyOfRange(offset, bytes.size))
                }
            }
            header = input.readN(4)
        }
        return null
    }
    internal fun vorbis(data: ByteArray): String? {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        fun bytes(): ByteArray { val size = buffer.int; require(size in 0..buffer.remaining()); return ByteArray(size).also(buffer::get) }
        bytes(); val count = buffer.int; require(count in 0..100_000)
        repeat(count) {
            val entry = bytes(); if (entry.size <= Lyrics.MAX_BYTES) {
                val text = String(entry, Charsets.UTF_8); val key = text.substringBefore('=')
                if (key.equals("LYRICS", true) || key.equals("UNSYNCEDLYRICS", true)) return text.substringAfter('=', "").takeIf(String::isNotBlank)
            }
        }
        return null
    }
}
