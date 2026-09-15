package com.sole.cinevault

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Restores the audio track selected before the FFmpeg-first rebuild.
 *
 * The old TrackSelectionOverride cannot be reused because it belongs to the
 * destroyed player's Tracks.Group. We therefore wait for the new player's
 * tracks, find a matching audio format by stable identity, and create a fresh
 * override against the new group/index.
 */
internal class AudioRescueTrackRestoreListener(
    private val player: ExoPlayer,
    private val trackSelector: DefaultTrackSelector,
    private val identity: AudioRescueTrackIdentity,
) : Player.Listener {

    private var finished = false

    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
        if (finished) return

        tracks.groups
            .asSequence()
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .forEach { group ->
                for (trackIndex in 0 until group.length) {
                    if (identity.matches(group.getTrackFormat(trackIndex))) {
                        trackSelector.parameters =
                            trackSelector.buildUponParameters()
                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                .addOverride(
                                    TrackSelectionOverride(
                                        group.mediaTrackGroup,
                                        trackIndex,
                                    )
                                )
                                .build()

                        finished = true
                        player.removeListener(this)
                        return
                    }
                }
            }
    }
}

internal fun restoreAudioTrackAfterRescue(
    player: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    identity: AudioRescueTrackIdentity?,
) {
    if (identity == null) return

    val listener = AudioRescueTrackRestoreListener(
        player = player,
        trackSelector = trackSelector,
        identity = identity,
    )
    player.addListener(listener)

    // Tracks may already be available by the time the handover is applied.
    listener.onTracksChanged(player.currentTracks)
}
