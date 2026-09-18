package com.sole.cinevault

/**
 * Playback Rescue Stage B2.
 *
 * Bridges the pure Stage-B video rescue decision model into the existing
 * PlayerPlaybackRecoveryState without activating a new renderer yet.
 *
 * This keeps "FFmpeg video is available" explicit. Until a real FFmpeg video
 * renderer/backend reports itself available, recovery can never advertise or
 * request that lane.
 */
fun PlayerPlaybackRecoveryState.currentVideoRescueStage(): VideoRescueStage =
    when (engineMode) {
        PlaybackEngineMode.HARDWARE -> VideoRescueStage.HARDWARE
        PlaybackEngineMode.SOFTWARE -> VideoRescueStage.PLATFORM_SOFTWARE
    }

/**
 * Returns the next video-rescue action from the player's real current state.
 *
 * softwareFallbackAvailable comes from CineVault's existing decoder capability
 * probe. ffmpegVideoAvailable must come from the future real FFmpeg-video
 * backend integration.
 */
fun PlayerPlaybackRecoveryState.decideNextVideoRescueAction(
    ffmpegVideoAvailable: Boolean,
): VideoRescueAction =
    decideNextVideoRescue(
        currentStage = currentVideoRescueStage(),
        capabilities = VideoRescueCapabilities(
            platformSoftwareAvailable = softwareFallbackAvailable,
            ffmpegVideoAvailable = ffmpegVideoAvailable,
        ),
    )
