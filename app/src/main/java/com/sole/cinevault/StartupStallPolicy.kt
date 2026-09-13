package com.sole.cinevault

fun shouldFallbackForStartupStall(
    isBuffering: Boolean,
    startupPlaybackConfirmed: Boolean,
    elapsedMs: Long,
    playbackProgressMs: Long,
    engineMode: PlaybackEngineMode,
    softwareFallbackAvailable: Boolean,
    fallbackOccurred: Boolean,
    thresholds: PlaybackHealthThresholds =
        ConservativePlaybackHealthThresholds,
): Boolean {
    if (!isBuffering) return false
    if (startupPlaybackConfirmed) return false
    if (elapsedMs < thresholds.startupStallTimeoutMs) return false
    if (playbackProgressMs >= 750L) return false
    if (engineMode != PlaybackEngineMode.HARDWARE) return false
    if (!softwareFallbackAvailable) return false
    if (fallbackOccurred) return false

    return true
}
