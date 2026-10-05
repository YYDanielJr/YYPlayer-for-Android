package yy.daniel.yyplayerforandroid.online

/** Container signatures, never a guess based on the URL, Content-Type or file size. */
data class AudioFileFormat(val extension: String, val mime: String, val bitDepth: Int? = null) {
    companion object {
        fun detect(bytes: ByteArray): AudioFileFormat? {
            fun at(offset: Int, text: String) = offset >= 0 && offset + text.length <= bytes.size &&
                text.indices.all { bytes[offset + it].toInt() and 255 == text[it].code }
            fun byte(i: Int) = bytes.getOrNull(i)?.toInt()?.and(255) ?: 0
            var offset = 0
            if (at(0, "ID3") && bytes.size >= 10) {
                if ((6..9).any { byte(it) >= 128 }) return null
                offset = 10 + (byte(6) shl 21) + (byte(7) shl 14) + (byte(8) shl 7) + byte(9)
                if (byte(5) and 16 != 0) offset += 10
            }
            if (at(offset, "fLaC")) {
                // STREAMINFO: 20-bit sample rate, 3-bit channels, 5-bit (bits per sample - 1).
                val bits = if (bytes.size >= offset + 42 && byte(offset + 4) and 127 == 0 &&
                    byte(offset + 5) == 0 && byte(offset + 6) == 0 && byte(offset + 7) == 34)
                    (((byte(offset + 20) and 1) shl 4) or (byte(offset + 21) shr 4)) + 1 else null
                return AudioFileFormat("flac", "audio/flac", bits)
            }
            if ((at(0, "RIFF") || at(0, "RF64")) && at(8, "WAVE")) return AudioFileFormat("wav", "audio/wav")
            if (at(4, "ftyp")) return AudioFileFormat("m4a", "audio/mp4")
            if (at(0, "OggS")) return if (bytes.toString(Charsets.ISO_8859_1).contains("OpusHead"))
                AudioFileFormat("opus", "audio/ogg") else AudioFileFormat("ogg", "audio/ogg")
            if (at(0, "FORM") && (at(8, "AIFF") || at(8, "AIFC"))) return AudioFileFormat("aiff", "audio/aiff")
            if (at(offset, "MAC ")) return AudioFileFormat("ape", "audio/x-ape")
            if (bytes.size > offset + 3 && byte(offset) == 255) {
                if (byte(offset + 1) and 246 == 240) return AudioFileFormat("aac", "audio/aac")
                if (byte(offset + 1) and 224 == 224 && byte(offset + 1) and 6 != 0 &&
                    byte(offset + 1) and 24 != 8 && byte(offset + 2) and 240 !in listOf(0, 240) &&
                    byte(offset + 2) and 12 != 12) return AudioFileFormat("mp3", "audio/mpeg")
            }
            return null
        }
    }
}

internal fun downloadBaseName(title: String, artist: String): String =
    "${title.trim()} - ${artist.trim().ifBlank { "未知歌手" }}"
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.').take(150)
