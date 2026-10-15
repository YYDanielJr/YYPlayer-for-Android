package yy.daniel.yyplayerforandroid.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.core.graphics.scale
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import yy.daniel.yyplayerforandroid.MainActivity
import yy.daniel.yyplayerforandroid.PlayerApplication
import yy.daniel.yyplayerforandroid.audio.OutputPath
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Owns the platform-visible MediaSession, which is what makes the system media controls work: the
 * control centre, media notification and Bluetooth transport buttons address this session. OEM
 * surfaces such as HyperOS's island consume the same standard notification and metadata.
 *
 * One player per process: the session is built on the engine the app already uses
 * ([PlayerApplication]'s runtime) instead of constructing a second one. Two ExoPlayers would mean two
 * output streams and two clocks, so the service borrows the engine and owns its *lifecycle* - it is
 * the foreground component that keeps playback alive when no Activity is, and it releases nothing the
 * app is still using.
 *
 * The output path (system sink vs the app's own exact sink) is fixed when a player is constructed, so
 * a change is applied by rebuilding the engine's player here, preserving queue, position and play
 * intent.
 */
@UnstableApi
class PlayerSessionService : MediaSessionService() {

    private var session: MediaSession? = null
    private var engine: AndroidMedia3PlaybackBackend? = null
    private var appliedPath: OutputPath = OutputPath.SYSTEM
    private val metadataScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val runtime = (application as? PlayerApplication)?.runtime ?: run {
            stopSelf()
            return
        }
        appliedPath = runtime.preferences.value.audioOutputPath
        val playback = runtime.ensureEngine()
        engine = playback
        session = MediaSession.Builder(this, playback.exoPlayer())
            // What the notification and the control centre open when the user taps the title or artwork.
            .setSessionActivity(openAppIntent())
            .build()
        // In-app commands address the engine directly, so no MediaController necessarily binds
        // and calls onGetSession. Explicit registration starts Media3's notification/FGS tracking.
        addSession(checkNotNull(session))
        playback.onPlayerReplaced = { session?.setPlayer(it) }
        runtime.attachSession(this)
        // UI artwork used to be invisible to the session. Publish bounded artwork bytes, so
        // SystemUI never has to open our private files or the user's SAF URI itself.
        metadataScope.launch {
            playback.state.combine(androidx.compose.runtime.snapshotFlow { ArtworkCache.revision }) { state, revision ->
                Triple(state.current, state.mediaGeneration, session?.player) to revision
            }.distinctUntilChanged().collectLatest { (source, _) ->
                    val (media, generation, player) = source
                    if (media?.kind != MediaKind.AUDIO || player == null) return@collectLatest
                    val item = player.currentMediaItem ?: return@collectLatest
                    val details = try { ArtworkCache.load(applicationContext, media) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { MediaDetails() } // Missing/revoked artwork must not stop playback.
                    val art = withContext(Dispatchers.Default) {
                        details.artwork?.let { bitmap ->
                            val size = minOf(1f, 384f / maxOf(bitmap.width, bitmap.height))
                            val scaled = bitmap.scale(
                                (bitmap.width * size).toInt().coerceAtLeast(1), (bitmap.height * size).toInt().coerceAtLeast(1), true)
                            try { java.io.ByteArrayOutputStream().use {
                                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)
                                it.toByteArray()
                            } } finally { if (scaled !== bitmap) scaled.recycle() }
                        }
                    }
                    ensureActive()
                    if (session?.player !== player || playback.state.value.mediaGeneration != generation ||
                        playback.state.value.current?.identity != media.identity || player.currentMediaItem !== item ||
                        !player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return@collectLatest
                    val metadata = item.mediaMetadata.buildUpon()
                        .setTitle(details.title ?: media.musicTitle)
                        .setArtist(details.artist ?: media.artist).setAlbumTitle(details.album ?: media.album)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setIsPlayable(true)
                        .setArtworkData(art, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()
                    if (metadata == item.mediaMetadata) return@collectLatest
                    // Media3 updates metadata in-place, preserving the current decoder, position,
                    // play intent and session token. No second Player or synthetic notification.
                    player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setMediaMetadata(metadata).build())
                }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Applies the output-path setting to the running player. Called by the app when the user changes it;
     * the engine rebuilds its player and keeps playing from the same position.
     */
    fun applyOutputPath(path: OutputPath) {
        appliedPath = path
        engine?.setOutputPath(path)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away must not leave silent playback behind: stop unless sound is still wanted.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        metadataScope.cancel()
        (application as? PlayerApplication)?.runtime?.detachSession(this)
        engine?.onPlayerReplaced = null
        session?.release()
        session = null
        engine = null
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("open_music", true)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
