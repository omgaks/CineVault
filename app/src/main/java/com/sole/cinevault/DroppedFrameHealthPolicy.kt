package com.sole.cinevault

enum class DroppedFrameHealth {
    HEALTHY,
    UNHEALTHY,
}

/**
 * Conservative playback-health check.
 *
 * Media3 reports dropped frames in windows. We only call a window unhealthy
 * when the renderer dropped a meaningful number of frames over a meaningful
 * amount of time. A single burst does NOT immediately trigger fallback;
 * PlayerPlaybackRecoveryState requires consecutive unhealthy windows.
 */
fun assessDroppedFrameHealth(
    droppedFrames: Int,
    elapsedMs: Long,
): DroppedFrameHealth {
    if (droppedFrames <= 0 || elapsedMs < 1_500L) {
        return DroppedFrameHealth.HEALTHY
    }

    val dropsPerSecond = droppedFrames * 1_000.0 / elapsedMs

    val severeShortWindow =
        elapsedMs <= 2_500L &&
            droppedFrames >= 24 &&
            dropsPerSecond >= 10.0

    val severeLongWindow =
        elapsedMs > 2_500L &&
            droppedFrames >= 40 &&
            dropsPerSecond >= 8.0

    return if (severeShortWindow || severeLongWindow) {
        DroppedFrameHealth.UNHEALTHY
    } else {
        DroppedFrameHealth.HEALTHY
    }
}

/**
 * Two consecutive unhealthy reports are required before CineVault considers
 * the native decoder persistently unhealthy.
 */
fun nextDroppedFrameUnhealthyStreak(
    currentStreak: Int,
    health: DroppedFrameHealth,
): Int {
    return when (health) {
        DroppedFrameHealth.HEALTHY -> 0
        DroppedFrameHealth.UNHEALTHY -> (currentStreak + 1).coerceAtMost(2)
    }
}

fun shouldFallbackForDroppedFrames(
    unhealthyStreak: Int,
    engineMode: PlaybackEngineMode,
    softwareFallbackAvailable: Boolean,
): Boolean {
    return unhealthyStreak >= 2 &&
        engineMode == PlaybackEngineMode.HARDWARE &&
        softwareFallbackAvailable
}
