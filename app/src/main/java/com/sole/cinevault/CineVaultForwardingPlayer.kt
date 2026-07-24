package com.sole.cinevault

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/*
 * CineVaultForwardingPlayer.kt
 *
 * Bridges hardware media-button next/previous (wired headset, Bluetooth
 * remote, some car units) into playNext()/playPrevious() in
 * VideoPlayerScreen.kt.
 *
 * Why this is needed at all: VideoPlayerScreen swaps a single MediaItem in
 * and out of the player per video (not a real ExoPlayer playlist), so the
 * underlying player always reports hasNextMediaItem()/hasPreviousMediaItem()
 * as false. Media3's MediaSession only shows/enables next-previous on the
 * lock-screen notification, and only forwards hardware key events, when the
 * player's own available commands say it can — which it never would here
 * without this wrapper. ForwardingPlayer is the standard, stable Media3/
 * ExoPlayer utility for exactly this: wrap a real player, override only the
 * handful of methods you want to intercept, delegate everything else
 * untouched. Play/pause/seek-within-video are NOT overridden here and pass
 * straight through to the real player as normal.
 *
 * Wired in by CineVaultPlaybackService.kt (wraps CineVaultPlayerHolder.
 * currentPlayer in this before handing it to MediaSession.Builder), and
 * fed by VideoPlayerScreen.kt setting CineVaultPlayerHolder.onNextRequested
 * / onPreviousRequested alongside currentPlayer.
 *
 * One accepted trade-off: hasNextMediaItem()/hasPreviousMediaItem() are
 * forced to true unconditionally, since ForwardingPlayer has no way to know
 * whether the app-level episode list actually has a next/previous entry at
 * this point. In practice this just means the lock-screen next/previous
 * buttons never visually grey out at the start/end of a list — tapping
 * them still safely falls through to the app's existing "No next video" /
 * "No previous video" handling rather than doing anything wrong.
 */
class CineVaultForwardingPlayer(player: Player) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() {
        CineVaultPlayerHolder.onNextRequested?.invoke()
    }

    override fun seekToNextMediaItem() {
        CineVaultPlayerHolder.onNextRequested?.invoke()
    }

    override fun seekToPrevious() {
        CineVaultPlayerHolder.onPreviousRequested?.invoke()
    }

    override fun seekToPreviousMediaItem() {
        CineVaultPlayerHolder.onPreviousRequested?.invoke()
    }
}
