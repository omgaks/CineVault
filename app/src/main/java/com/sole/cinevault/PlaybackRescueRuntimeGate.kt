package com.sole.cinevault

internal object PlaybackRescueRuntimeGate {
    fun plan(
        recoveryState: PlayerPlaybackRecoveryState,
        ffmpegAudioRequested: Boolean,
    ): PlaybackRescueExecutionPlan =
        PlaybackRescueExecutionPolicy.forLane(
            PlaybackRescueCoordinator.decide(
                recoveryState = recoveryState,
                ffmpegAudioRequested = ffmpegAudioRequested,
            )
        )

    fun shouldExecuteSoftwareVideo(
        recoveryState: PlayerPlaybackRecoveryState,
    ): Boolean =
        plan(
            recoveryState = recoveryState,
            ffmpegAudioRequested = false,
        ).executeSoftwareVideo

    fun shouldExecuteFfmpegAudio(
        recoveryState: PlayerPlaybackRecoveryState,
    ): Boolean =
        plan(
            recoveryState = recoveryState,
            ffmpegAudioRequested = true,
        ).executeFfmpegAudio
}
