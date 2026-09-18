package com.sole.cinevault.playback.rescue.video

import com.sole.cinevault.PlayerPlaybackRecoveryState
import com.sole.cinevault.VideoRescueAction
import com.sole.cinevault.decideNextVideoRescueAction

/**
 * Feeds the B3 backend capability contract into the B2 recovery-state bridge.
 *
 * Keeping this adapter in the video-rescue package prevents the large player
 * files from accumulating backend-specific capability logic.
 */
fun PlayerPlaybackRecoveryState.decideNextVideoRescueAction(
    ffmpegVideoCapability: FfmpegVideoBackendCapability,
): VideoRescueAction =
    decideNextVideoRescueAction(
        ffmpegVideoAvailable = ffmpegVideoCapability.snapshot().available,
    )
