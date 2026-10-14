package yy.daniel.yyplayerforandroid.playback

import android.content.Context
import android.view.Surface

/** Private transport, called only by MpvVideoBackend's IO controller. No UI-facing raw commands. */
@androidx.annotation.Keep
internal object MpvNative {
    init { System.loadLibrary("yy_mpv") }
    external fun create(context: Context, surface: Surface?): Long
    private external fun request(handle: Long, arguments: Array<ByteArray>): ByteArray
    fun call(handle: Long, arguments: Array<String>): String =
        request(handle, arguments.map { it.toByteArray(Charsets.UTF_8) }.toTypedArray()).toString(Charsets.UTF_8)
    external fun close(handle: Long)
}
