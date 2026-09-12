package com.sole.cinevault

private const val STARTUP_STALL_TIMEOUT_MS = 12_000L
private const val STARTUP_PROGRESS_TOLERANCE_MS = 750L

fun shouldFallbackForStartupStall(
    isBuffering: Boolean,
    startupPlaybackConfirmed: Boolean,
    elapsedMs: Long,
    playbackProgressMs: Long,
    engineMode: PlaybackEngineMode,
    softwareFallbackAvailable: Boolean,
    fallbackOccurred: Boolean,
): Boolean {
    if (!isBuffering) return false
    if (startupPlaybackConfirmed) return false
    if (elapsedMs < STARTUP_STALL_TIMEOUT_MS) return false
    if (playbackProgressMs >= STARTUP_PROGRESS_TOLERANCE_MS) return false
    if (engineMode != PlaybackEngineMode.HARDWARE) return false
    if (!softwareFallbackAvailable) return false
    if (fallbackOccurred) return false

    return true
}
