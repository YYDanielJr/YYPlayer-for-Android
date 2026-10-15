package yy.daniel.yyplayerforandroid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import yy.daniel.yyplayerforandroid.playback.ArtworkCache
import yy.daniel.yyplayerforandroid.playback.MediaDetails
import yy.daniel.yyplayerforandroid.playback.LocalMedia
import yy.daniel.yyplayerforandroid.playback.MediaKind

internal fun invalidateMediaArtwork() = ArtworkCache.clear()

@Composable
internal fun rememberMediaDetails(media: LocalMedia?): MediaDetails {
    val context = LocalContext.current.applicationContext
    // key resets the previous song immediately; cancellation prevents late cover colours from winning.
    return key(media?.uri, ArtworkCache.revision) {
        val details by produceState(ArtworkCache.peek(media) ?: MediaDetails(artist = media?.artist, album = media?.album, durationMs = media?.durationMs, title = media?.songTitle)) {
            value = if (media == null) MediaDetails() else ArtworkCache.load(context, media)
        }
        details
    }
}

@Composable
internal fun MediaArtwork(media: LocalMedia?, modifier: Modifier = Modifier, radius: Dp = 20.dp) {
    val details = rememberMediaDetails(media)
    Box(modifier.clip(RoundedCornerShape(radius)).background(MiuixTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        val bitmap = details.artwork
        if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(if (media?.kind == MediaKind.VIDEO) PlayerIcons.Video else PlayerIcons.Music, null,
            Modifier.fillMaxSize(.36f), tint = MiuixTheme.colorScheme.onSurfaceContainerVariant)
    }
}
