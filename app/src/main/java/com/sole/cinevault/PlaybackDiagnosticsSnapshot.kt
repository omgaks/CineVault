package com.sole.cinevault

data class PlaybackDiagnosticsSnapshot(
    val mimeType: String?,
    val codecString: String?,
    val resolution: String,
    val frameRate: Float?,
    val dynamicRange: VideoDynamicRange,
    val decoderName: String?,
    val decoderMode: PlaybackEngineMode,
    val activeDecoderKind: ActiveVideoDecoderKind,
    val compatibilityRisk: VideoCompatibilityRisk,
    val decoderRecommendation: VideoDecoderRecommendation,
    val fallbackOccurred: Boolean,
    val fallbackReason: PlaybackFallbackReason?,
    val nativeReadiness: NativeVideoPlaybackReadiness =
        NativeVideoPlaybackReadiness.UNKNOWN,
    val softwareFallbackAvailable: Boolean = false,
    val totalDroppedVideoFrames: Int = 0,
    val unhealthyDroppedFrameWindows: Int = 0,
    val startupPlaybackConfirmed: Boolean = false,
    val firstVideoFrameRendered: Boolean = false,
)

fun buildPlaybackDiagnosticsSnapshot(
    recoveryState: PlayerPlaybackRecoveryState,
): PlaybackDiagnosticsSnapshot {
    val profile = recoveryState.videoStreamProfile
    val assessment = recoveryState.videoPlaybackCompatibilityAssessment
    val activeDecoder = recoveryState.activeVideoDecoderStatus

    return PlaybackDiagnosticsSnapshot(
        mimeType = profile?.mimeType,
        codecString = profile?.codecString,
        resolution = profile?.resolutionLabel ?: "Unknown",
        frameRate = profile?.frameRate,
        dynamicRange = profile?.dynamicRange ?: VideoDynamicRange.UNKNOWN,
        decoderName = activeDecoder.decoderName,
        decoderMode = recoveryState.engineMode,
        activeDecoderKind = activeDecoder.kind,
        compatibilityRisk = assessment.risk,
        decoderRecommendation = assessment.recommendation,
        fallbackOccurred = recoveryState.fallbackOccurred,
        fallbackReason = recoveryState.fallbackReason,
        nativeReadiness = recoveryState.nativeVideoPlaybackReadiness,
        softwareFallbackAvailable =
            recoveryState.softwareFallbackAvailable,
        totalDroppedVideoFrames =
            recoveryState.totalDroppedVideoFrames,
        unhealthyDroppedFrameWindows =
            recoveryState.droppedFrameUnhealthyStreak,
        startupPlaybackConfirmed =
            recoveryState.startupPlaybackConfirmed,
        firstVideoFrameRendered =
            recoveryState.firstVideoFrameRendered,
    )
}
