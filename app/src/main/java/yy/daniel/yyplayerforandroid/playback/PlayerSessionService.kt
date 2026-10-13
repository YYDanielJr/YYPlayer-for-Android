package yy.daniel.yyplayerforandroid.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import yy.daniel.yyplayerforandroid.MainActivity
import yy.daniel.yyplayerforandroid.PlayerApplication
import yy.daniel.yyplayerforandroid.audio.OutputPath

/**
 * Owns the platform-visible MediaSession, which is what makes the system media controls work: the
 * control centre, the media notification, Bluetooth transport buttons and the dynamic island all
 * address a session rather than an app.
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
        playback.onPlayerReplaced = { session?.setPlayer(it) }
        runtime.attachSession(this)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Applies the output-path setting to the running player. Called by the app when the user changes it;
     * the engine rebuilds its player and keeps playing from the same position.
     */
    fun applyOutputPath(path: OutputPath) {
        if (path == appliedPath) return
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
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
