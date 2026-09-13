package com.sole.cinevault

/**
 * Stable stream identity used by CineVault's compatibility matrix.
 *
 * Runtime/device-specific values such as decoder name, dropped frames and
 * fallback state are deliberately excluded so the same media stream can be
 * compared across devices and decoder paths.
 */
data class PlaybackCompatibilityKey(
    val mimeType: String?,
    val codecLabel: String?,
    val profileLabel: String?,
    val levelLabel: String?,
    val bitDepth: Int?,
    val resolution: String,
    val frameRate: Float?,
    val dynamicRange: VideoDynamicRange,
)

enum class PlaybackCompatibilityOutcome {
    STARTING,
    NATIVE_HEALTHY,
    NATIVE_UNSTABLE,
    SOFTWARE_RESCUED,
}

data class PlaybackCompatibilityObservation(
    val key: PlaybackCompatibilityKey,
    val outcome: PlaybackCompatibilityOutcome,
    val decoderName: String?,
    val decoderKind: ActiveVideoDecoderKind,
    val compatibilityRisk: VideoCompatibilityRisk,
    val recommendation: VideoDecoderRecommendation,
    val nativeReadiness: NativeVideoPlaybackReadiness,
    val softwareFallbackAvailable: Boolean,
    val fallbackOccurred: Boolean,
    val fallbackReason: PlaybackFallbackReason?,
    val totalDroppedVideoFrames: Int,
    val unhealthyDroppedFrameWindows: Int,
)

/**
 * Converts the live diagnostics snapshot into one deterministic,
 * machine-readable compatibility observation.
 *
 * This does not persist anything yet. It is the reusable data contract for
 * later torture-file runs, device comparisons and compatibility-matrix export.
 */
fun buildPlaybackCompatibilityObservation(
    snapshot: PlaybackDiagnosticsSnapshot,
): PlaybackCompatibilityObservation {
    val codecDetails = parseVideoCodecDetails(
        mimeType = snapshot.mimeType,
        codecString = snapshot.codecString,
    )

    val outcome = when {
        snapshot.fallbackOccurred ||
            snapshot.decoderMode == PlaybackEngineMode.SOFTWARE ||
            snapshot.activeDecoderKind == ActiveVideoDecoderKind.SOFTWARE ->
            PlaybackCompatibilityOutcome.SOFTWARE_RESCUED

        snapshot.unhealthyDroppedFrameWindows > 0 ||
            snapshot.nativeReadiness == NativeVideoPlaybackReadiness.MARGINAL ||
            snapshot.nativeReadiness ==
                NativeVideoPlaybackReadiness.SOFTWARE_FALLBACK_NEEDED ->
            PlaybackCompatibilityOutcome.NATIVE_UNSTABLE

        snapshot.firstVideoFrameRendered ||
            snapshot.startupPlaybackConfirmed ->
            PlaybackCompatibilityOutcome.NATIVE_HEALTHY

        else ->
            PlaybackCompatibilityOutcome.STARTING
    }

    return PlaybackCompatibilityObservation(
        key = PlaybackCompatibilityKey(
            mimeType = snapshot.mimeType,
            codecLabel = codecDetails.codecLabel,
            profileLabel = codecDetails.profileLabel,
            levelLabel = codecDetails.levelLabel,
            bitDepth = codecDetails.inferredBitDepth,
            resolution = snapshot.resolution,
            frameRate = snapshot.frameRate,
            dynamicRange = snapshot.dynamicRange,
        ),
        outcome = outcome,
        decoderName = snapshot.decoderName,
        decoderKind = snapshot.activeDecoderKind,
        compatibilityRisk = snapshot.compatibilityRisk,
        recommendation = snapshot.decoderRecommendation,
        nativeReadiness = snapshot.nativeReadiness,
        softwareFallbackAvailable = snapshot.softwareFallbackAvailable,
        fallbackOccurred = snapshot.fallbackOccurred,
        fallbackReason = snapshot.fallbackReason,
        totalDroppedVideoFrames = snapshot.totalDroppedVideoFrames,
        unhealthyDroppedFrameWindows =
            snapshot.unhealthyDroppedFrameWindows,
    )
}
