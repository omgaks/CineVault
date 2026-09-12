package com.sole.cinevault

private const val FIRST_FRAME_TIMEOUT_MS = 8_000L
private const val FIRST_FRAME_PROGRESS_THRESHOLD_MS = 1_500L

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
): Boolean {
    if (!isPlaying) return false
    if (!hasSelectedVideoTrack) return false
    if (firstVideoFrameRendered) return false
    if (elapsedMs < FIRST_FRAME_TIMEOUT_MS) return false
    if (playbackProgressMs < FIRST_FRAME_PROGRESS_THRESHOLD_MS) return false
    if (engineMode != PlaybackEngineMode.HARDWARE) return false
    if (!softwareFallbackAvailable) return false
    if (fallbackOccurred) return false

    return true
}
