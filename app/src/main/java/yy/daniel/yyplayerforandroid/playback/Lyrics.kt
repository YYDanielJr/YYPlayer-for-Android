package yy.daniel.yyplayerforandroid.playback

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlin.math.roundToLong

data class LyricLine(val milliseconds: Long, val text: String)

/** Desktop's spaced separator preserves bare slashes in URLs, dates and artist names. */
fun bilingualParts(text: String): Pair<String, String> {
    val separator = text.indexOf(" / ")
    if (separator >= 0) {
        val primary = text.substring(0, separator).trim()
        val secondary = text.substring(separator + 3).trim()
        if (primary.isNotEmpty() && secondary.isNotEmpty()) return primary to secondary
    }
    return text to ""
}

/** Equivalent time semantics to player-core/lyrics.rs (GPL-3.0-only desktop reference). */
data class Lyrics(val lines: List<LyricLine> = emptyList(), val plain: String = "") {
    fun active(positionMs: Long, userOffsetMs: Long = 0): Int {
        val position = if (userOffsetMs > 0 && positionMs > Long.MAX_VALUE - userOffsetMs) Long.MAX_VALUE
            else if (userOffsetMs < 0 && positionMs < Long.MIN_VALUE - userOffsetMs) Long.MIN_VALUE else positionMs + userOffsetMs
        var low = 0; var high = lines.size
        while (low < high) { val mid = (low + high) ushr 1; if (lines[mid].milliseconds <= position) low = mid + 1 else high = mid }
        return low - 1
    }

    companion object {
        const val MAX_BYTES = 1_000_000
        fun decode(bytes: ByteArray): String {
            require(bytes.size <= MAX_BYTES) { "歌词文件超过 1MB" }
            if (bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            if (bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            return runCatching { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
                .getOrElse { String(bytes, charset("GB18030")) }.trimStart('\uFEFF')
        }

        fun parse(text: String): Lyrics {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "歌词文件超过 1MB" }
            val lines = mutableListOf<LyricLine>(); val plain = StringBuilder(); var offset = 0L
            text.trimStart('\uFEFF').lineSequence().forEach { raw ->
                var line = raw.trim(); val times = mutableListOf<Long>()
                while (line.startsWith('[')) {
                    val end = line.indexOf(']'); if (end < 0) break
                    val tag = line.substring(1, end); line = line.substring(end + 1)
                    if (tag.startsWith("offset:")) {
                        offset = tag.substringAfter(':').trim().toLongOrNull() ?: error("非法 LRC offset")
                        require(offset in -600_000..600_000) { "LRC offset 超出范围" }
                    } else if (':' in tag) {
                        val m = tag.substringBefore(':').toLongOrNull(); val s = tag.substringAfter(':').toDoubleOrNull()
                        if (m != null && s != null) {
                            require(m in 0..10_000 && s.isFinite() && s >= 0 && s < 60) { "非法 LRC 时间戳" }
                            times += m * 60_000 + (s * 1000).roundToLong()
                        }
                    }
                }
                if (times.isNotEmpty()) times.forEach { lines += LyricLine(it, line.trim()) }
                else if (line.isNotBlank()) plain.append(line).append('\n')
                require(lines.size <= 10_000) { "歌词行数超过 10000" }
            }
            return Lyrics(lines.map { it.copy(milliseconds = (it.milliseconds + offset).coerceAtLeast(0)) }.sortedBy { it.milliseconds }, plain.toString())
        }
    }
}
