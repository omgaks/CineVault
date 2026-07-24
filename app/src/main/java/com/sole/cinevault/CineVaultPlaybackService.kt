package com.sole.cinevault

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/*
 * CineVaultPlaybackService.kt
 *
 * A foreground MediaSessionService that wraps whichever ExoPlayer instance
 * is currently playing (tracked in CineVaultPlayerHolder, MainActivity.kt)
 * in a MediaSession. This is what makes playback survive the screen
 * locking or the app backgrounding, and gives real lock-screen media
 * controls (play/pause/skip) plus a notification — previously
 * MainActivity.onStop() unconditionally paused playback the moment the
 * screen turned off, with no way to keep it going.
 *
 * Deliberately does NOT create its own ExoPlayer — VideoPlayerScreen.kt
 * still owns creation/configuration of the player exactly as before (same
 * CineRenderersFactory, tuned DefaultLoadControl, SMB media source, track
 * selector — none of that changed). This service just reads whatever
 * player is currently in CineVaultPlayerHolder, wraps it in a session, and
 * keeps the process alive/foreground while it's playing. VideoPlayerScreen
 * starts this service right after it sets CineVaultPlayerHolder.currentPlayer,
 * and stops it on an actual exit from the player screen (Back / navigated
 * away) — not just because the screen locked, which is the whole point.
 *
 * NOTE for Xiaomi/HyperOS devices: MIUI-based battery management is known
 * to aggressively kill background services regardless of foreground status
 * unless the app is allowed "Autostart" / exempted from battery
 * optimization. If lock-screen playback stops working after a while on the
 * Pad 7 specifically, check Settings > Apps > CineVault > Battery saver /
 * Autostart first — that's a device-level restriction, not something the
 * app can fully control from code.
 */
class CineVaultPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var wrappedPlayerFor: androidx.media3.exoplayer.ExoPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshSession()
        return super.onStartCommand(intent, flags, startId)
    }

    // Rebuilds the session if the current player has changed (e.g. a fresh
    // ExoPlayer instance was created for a new visit to the player screen),
    // or tears itself down if there's no player to attach to anymore.
    private fun refreshSession() {
        val player = CineVaultPlayerHolder.currentPlayer
        if (player == null) {
            mediaSession?.release()
            mediaSession = null
            wrappedPlayerFor = null
            stopSelf()
            return
        }
        // Compares against the raw ExoPlayer we last wrapped, not
        // mediaSession.player (which is always the CineVaultForwardingPlayer
        // wrapper, never equal to the raw player) — comparing against that
        // directly would rebuild the session on every single call.
        if (wrappedPlayerFor !== player) {
            mediaSession?.release()
            // Wrapped so hardware media-button next/previous route to the
            // app's own episode-switching logic — see
            // CineVaultForwardingPlayer.kt for why this is necessary.
            mediaSession = MediaSession.Builder(this, CineVaultForwardingPlayer(player)).build()
            wrappedPlayerFor = player
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        refreshSession()
        return mediaSession
    }

    // Standard MediaSessionService pattern: if the task is swiped away from
    // Recents while nothing is actively playing, stop the service instead
    // of leaving an orphaned foreground notification behind.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = CineVaultPlayerHolder.currentPlayer
        if (player == null || !player.playWhenReady) {
            mediaSession?.release()
            mediaSession = null
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
