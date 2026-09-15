package com.sole.cinevault

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

private const val MAX_AUDIO_RESTORE_TRACK_EVENTS = 3

internal enum class AudioRescueTrackRestoreDecision {
    KEEP_WAITING,
    RESTORED,
    GIVE_UP,
}

internal fun audioRescueTrackRestoreDecision(
    matchingTrackFound: Boolean,
    audioGroupsPresent: Boolean,
    trackEventsSeen: Int,
): AudioRescueTrackRestoreDecision =
    when {
        matchingTrackFound -> AudioRescueTrackRestoreDecision.RESTORED
        !audioGroupsPresent -> AudioRescueTrackRestoreDecision.KEEP_WAITING
        trackEventsSeen >= MAX_AUDIO_RESTORE_TRACK_EVENTS ->
            AudioRescueTrackRestoreDecision.GIVE_UP
        else -> AudioRescueTrackRestoreDecision.KEEP_WAITING
    }

/**
 * Restores the audio track selected before the FFmpeg-first rebuild.
 *
 * The old TrackSelectionOverride cannot be reused because it belongs to the
 * destroyed player's Tracks.Group. This listener creates a fresh override
 * against the rebuilt player's group/index.
 *
 * If the exact saved identity never appears, the listener removes itself after
 * a small bounded number of real audio-track updates. It deliberately does not
 * force another track; Media3's normal/default audio selection remains active.
 */
internal class AudioRescueTrackRestoreListener(
    private val player: ExoPlayer,
    private val trackSelector: DefaultTrackSelector,
    private val identity: AudioRescueTrackIdentity,
) : Player.Listener {

    private var finished = false
    private var audioTrackEventsSeen = 0

    override fun onTracksChanged(tracks: Tracks) {
        if (finished) return

        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audioGroups.isEmpty()) return

        audioTrackEventsSeen += 1

        audioGroups.forEach { group ->
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

                    finish()
                    return
                }
            }
        }

        if (
            audioRescueTrackRestoreDecision(
                matchingTrackFound = false,
                audioGroupsPresent = true,
                trackEventsSeen = audioTrackEventsSeen,
            ) == AudioRescueTrackRestoreDecision.GIVE_UP
        ) {
            finish()
        }
    }

    private fun finish() {
        if (finished) return
        finished = true
        player.removeListener(this)
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
