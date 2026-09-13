package com.sole.cinevault

/**
 * Detects the "audio/progress is moving but no video frame ever appeared"
 * failure mode.
 *
 * This is intentionally different from startup buffering:
 * - startup stall = playback never really gets moving
 * - first-frame failure = playback IS moving, but video output stays absent
 */
fun shouldFallbackForMissingFirstVideoFrame(
    isPlaying: Boolean,
    hasSelectedVideoTrack: Boolean,
    firstVideoFrameRendered: Boolean,
    elapsedMs: Long,
    playbackProgressMs: Long,
    engineMode: PlaybackEngineMode,
    softwareFallbackAvailable: Boolean,
    fallbackOccurred: Boolean,
    thresholds: PlaybackHealthThresholds =
        ConservativePlaybackHealthThresholds,
): Boolean {
    if (!isPlaying) return false
    if (!hasSelectedVideoTrack) return false
    if (firstVideoFrameRendered) return false
    if (elapsedMs < thresholds.firstFrameTimeoutMs) return false
    if (playbackProgressMs < thresholds.firstFrameProgressThresholdMs) return false
    if (engineMode != PlaybackEngineMode.HARDWARE) return false
    if (!softwareFallbackAvailable) return false
    if (fallbackOccurred) return false

    return true
}
