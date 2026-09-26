package com.sole.cinevault

internal data class PlaybackRescueLifecycleSnapshot(
    val softwareVideoRequested: Boolean,
    val softwareVideoActive: Boolean,
    val softwareVideoOccurred: Boolean,
    val ffmpegAudioAttempted: Boolean,
    val ffmpegAudioActive: Boolean,
)

internal object PlaybackRescueLifecyclePolicy {
    fun allowsSoftwareVideoRequest(snapshot: PlaybackRescueLifecycleSnapshot): Boolean =
        !snapshot.softwareVideoRequested &&
            !snapshot.softwareVideoActive &&
            !snapshot.softwareVideoOccurred

    fun allowsFfmpegAudioRequest(snapshot: PlaybackRescueLifecycleSnapshot): Boolean =
        !snapshot.ffmpegAudioAttempted &&
            !snapshot.ffmpegAudioActive

    fun isCleanNewMediaScope(snapshot: PlaybackRescueLifecycleSnapshot): Boolean =
        !snapshot.softwareVideoRequested &&
            !snapshot.softwareVideoActive &&
            !snapshot.softwareVideoOccurred &&
            !snapshot.ffmpegAudioAttempted &&
            !snapshot.ffmpegAudioActive
}

internal fun PlayerPlaybackRecoveryState.rescueLifecycleSnapshot() =
    PlaybackRescueLifecycleSnapshot(
        softwareVideoRequested = softwareFallbackRequested,
        softwareVideoActive = engineMode == PlaybackEngineMode.SOFTWARE,
        softwareVideoOccurred = fallbackOccurred,
        ffmpegAudioAttempted = audioFfmpegRescueAttempted,
        ffmpegAudioActive =
            AudioRuntimeRescueController.rendererPreference !=
                CineAudioRendererPreference.PLATFORM_FIRST,
    )
