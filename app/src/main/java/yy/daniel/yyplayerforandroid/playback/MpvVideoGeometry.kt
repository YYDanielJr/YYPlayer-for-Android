package yy.daniel.yyplayerforandroid.playback

/** dw/dh already include pixel aspect correction; mpv's VO also applies the metadata rotation.
 * Publish the resulting display bounds, with no unapplied rotation for TextureView to repeat. */
internal fun mpvDisplaySize(displayWidth: Int, displayHeight: Int, rotation: Int): Pair<Int, Int> {
    if (displayWidth <= 0 || displayHeight <= 0) return 0 to 0
    val normalized = ((rotation % 360) + 360) % 360
    return if (normalized == 90 || normalized == 270) displayHeight to displayWidth
        else displayWidth to displayHeight
}
