package com.sole.cinevault

import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Applies a consumed FFmpeg rescue handover to a newly created player runtime.
 *
 * Keeping the restore sequence in one function makes the rebuild boundary
 * explicit and prevents individual continuity fields from being forgotten when
 * PlayerRuntimeFactory evolves.
 */
internal fun applyAudioRuntimeRescueHandover(
    player: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    handover: AudioRuntimeRescueHandover,
) {
    player.setMediaItem(
        handover.mediaItemWithRestoredSubtitle(),
        handover.resumePositionMs,
    )
    player.playbackParameters = PlaybackParameters(handover.playbackSpeed)
    player.volume = handover.volume

    restoreAudioTrackAfterRescue(
        player = player,
        trackSelector = trackSelector,
        identity = handover.audioTrackIdentity,
    )

    player.prepare()
    player.playWhenReady = handover.playWhenReady
}
