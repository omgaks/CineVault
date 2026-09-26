package com.sole.cinevault

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sole.cinevault.library.loadLibraryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
 * IMPORTANT — startForeground() timing:
 * VideoPlayerScreen.kt calls ContextCompat.startForegroundService(...) to
 * launch this service, which puts Android's hard 5-second clock on us:
 * Service.startForeground() MUST be called within 5 seconds or the OS
 * throws ForegroundServiceDidNotStartInTimeException and kills the
 * process. MediaSessionService's built-in MediaNotificationManager only
 * promotes to foreground once the player reports active playback — if
 * the SMB share is still connecting, ExoPlayer is still buffering, or
 * track selection hasn't resolved yet, that can take longer than 5s and
 * trips the crash. So onCreate() below calls startForeground() itself,
 * immediately, with a minimal placeholder notification. Once real
 * playback metadata is available, MediaSessionService's own notification
 * manager updates that same notification (same ID/channel) with title,
 * artwork, and transport controls — this placeholder never lingers.
 *
 * Manifest requirements for this to work:
 *   <service android:name=".CineVaultPlaybackService"
 *       android:foregroundServiceType="mediaPlayback"
 *       android:exported="false">
 *       <intent-filter>
 *           <action android:name="androidx.media3.session.MediaSessionService"/>
 *       </intent-filter>
 *   </service>
 * Plus <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>
 * and (API 33+) POST_NOTIFICATIONS requested at runtime, or this placeholder
 * notification silently won't show (though startForeground() itself still
 * satisfies the timing requirement either way).
 *
 * NOTE for Xiaomi/HyperOS devices: MIUI-based battery management is known
 * to aggressively kill background services regardless of foreground status
 * unless the app is allowed "Autostart" / exempted from battery
 * optimization. If lock-screen playback stops working after a while on the
 * Pad 7 specifically, check Settings > Apps > CineVault > Battery saver /
 * Autostart first — that's a device-level restriction, not something the
 * app can fully control from code.
 *
 * VOICE SEARCH (onAddMediaItems below):
 * Resolves "Hey Google, play [title] on CineVault" — and equivalent
 * requests from any MediaController, not Assistant specifically — against
 * the on-disk library cache (library/PlaybackMemory.kt), reusing the same
 * substring-match approach as SearchScreen.kt's own in-app search.
 * MediaItem.requestMetadata.searchQuery is the documented Media3 mechanism
 * for this (developer.android.com/media/media3/session/control-playback);
 * a plain MediaSession (not MediaLibraryService, which is what this app
 * uses) is expected to resolve search queries here rather than in
 * onSearch()/onGetSearchResult(), which are MediaLibraryService-only.
 *
 * A REAL, STRUCTURAL LIMITATION, STATED PLAINLY: this only works while a
 * session already exists — i.e. while something is ALREADY playing and the
 * service is alive to receive the request (see refreshSession() below:
 * with no current player, the session releases itself and the service
 * stops entirely). "Play a different movie while one is already playing"
 * genuinely works through this. A cold voice launch — the app not running,
 * nothing playing, saying "play X" from scratch — does NOT reach this
 * service at all, because there is no session for Assistant to connect to
 * yet. Fixing that would mean giving this service the ability to create
 * and own its own ExoPlayer independent of VideoPlayerScreen.kt, which is
 * a genuine architectural decision (this service was deliberately built
 * to never do that — see the class doc above) rather than something to
 * change unilaterally inside a voice-search addition.
 */
class CineVaultPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var wrappedPlayerFor: androidx.media3.exoplayer.ExoPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val PLAYBACK_CHANNEL_ID = "cinevault_playback_channel"
        private const val PLAYBACK_NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        // Must happen immediately — before refreshSession()/player setup,
        // which can be slow — to satisfy Android's 5-second
        // startForeground() requirement. See class doc above.
        startForeground(PLAYBACK_NOTIFICATION_ID, buildPlaceholderNotification())
    }

    private fun buildPlaceholderNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(PLAYBACK_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    PLAYBACK_CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "CineVault playback controls"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }

        return NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setContentTitle("CineVault")
            .setContentText("Starting playback…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

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
            mediaSession = MediaSession.Builder(this, CineVaultForwardingPlayer(player))
                .setCallback(VoiceSearchCallback())
                .build()
            wrappedPlayerFor = player
        }
    }

    // Resolves MediaItem.requestMetadata.searchQuery (set by Assistant/any
    // MediaController requesting playback by search text rather than a
    // known ID) against the on-disk library cache. See the class doc above
    // for what this can and can't reach.
    private inner class VoiceSearchCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val future = SettableFuture.create<MutableList<MediaItem>>()
            val query = mediaItems.firstOrNull { !it.requestMetadata.searchQuery.isNullOrBlank() }
                ?.requestMetadata?.searchQuery

            if (query == null) {
                // No search query on any item — not a voice-search request,
                // fall back to the default behavior (resolve by mediaId as
                // normal) rather than intercepting every add-items call.
                future.set(mediaItems)
                return future
            }

            serviceScope.launch {
                val cached = loadLibraryCache(this@CineVaultPlaybackService)
                // Same substring approach as SearchScreen.kt's in-app search
                // (title/filename/genre/director) — deliberately not fuzzy/
                // typo-tolerant matching, same reasoning as that screen.
                val match = cached?.videos?.firstOrNull { v ->
                    v.title.contains(query, ignoreCase = true) ||
                        v.video.name.contains(query, ignoreCase = true) ||
                        v.genres.any { it.contains(query, ignoreCase = true) } ||
                        v.director?.contains(query, ignoreCase = true) == true
                }

                val resolved = if (match != null) {
                    // setUri(String) with the raw path, not Uri.fromFile() —
                    // matches PlaybackNavigationCoordinator.kt's established
                    // MediaItem construction exactly, so this resolves through
                    // whatever data-source routing (including SMB paths) the
                    // app already has, rather than risking a differently-
                    // formatted URI that might not.
                    mutableListOf(
                        MediaItem.Builder()
                            .setUri(match.video.path)
                            .setMediaId(match.video.path)
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(match.title)
                                    .build()
                            )
                            .build()
                    )
                } else {
                    // No match — return the original (unresolvable) items
                    // rather than throwing; Media3/the calling controller
                    // handles an item with no playable LocalConfiguration
                    // as a failed request, which is the correct outcome
                    // for "couldn't find that title" rather than a crash.
                    mediaItems
                }
                future.set(resolved)
            }

            return future
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
        serviceScope.cancel()
        super.onDestroy()
    }
}
