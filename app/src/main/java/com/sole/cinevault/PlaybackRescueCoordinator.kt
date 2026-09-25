package com.sole.cinevault

internal data class PlaybackRescueRuntimeSnapshot(
    val softwareVideoRequested: Boolean,
    val softwareVideoAvailable: Boolean,
    val engineMode: PlaybackEngineMode,
    val ffmpegAudioRequested: Boolean,
    val audioRendererPreference: CineAudioRendererPreference,
)

internal object PlaybackRescueCoordinator {
    fun decide(snapshot: PlaybackRescueRuntimeSnapshot): PlaybackRescueLane =
        PlaybackRescueAdmissionPolicy.decide(
            PlaybackRescueAdmissionInput(
                softwareVideoRequested = snapshot.softwareVideoRequested,
                softwareVideoAvailable = snapshot.softwareVideoAvailable,
                softwareVideoAlreadyActive =
                    snapshot.engineMode == PlaybackEngineMode.SOFTWARE,
                ffmpegAudioRequested = snapshot.ffmpegAudioRequested,
                ffmpegAudioAlreadyActive =
                    snapshot.audioRendererPreference !=
                        CineAudioRendererPreference.PLATFORM_FIRST,
            )
        )

    fun decide(
        recoveryState: PlayerPlaybackRecoveryState,
        ffmpegAudioRequested: Boolean,
    ): PlaybackRescueLane =
        decide(
            PlaybackRescueRuntimeSnapshot(
                softwareVideoRequested = recoveryState.softwareFallbackRequested,
                softwareVideoAvailable = recoveryState.softwareFallbackAvailable,
                engineMode = recoveryState.engineMode,
                ffmpegAudioRequested = ffmpegAudioRequested,
                audioRendererPreference = AudioRuntimeRescueController.rendererPreference,
            )
        )
}
