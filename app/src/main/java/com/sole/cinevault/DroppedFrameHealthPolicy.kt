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
    thresholds: PlaybackHealthThresholds =
        ConservativePlaybackHealthThresholds,
): DroppedFrameHealth {
    if (droppedFrames <= 0 || elapsedMs < 1_500L) {
        return DroppedFrameHealth.HEALTHY
    }

    val dropsPerSecond = droppedFrames * 1_000.0 / elapsedMs

    val severeShortWindow =
        elapsedMs <= 2_500L &&
            droppedFrames >= thresholds.droppedFrameShortWindowMinimum &&
            dropsPerSecond >=
                thresholds.droppedFrameShortWindowRatePerSecond

    val severeLongWindow =
        elapsedMs > 2_500L &&
            droppedFrames >= thresholds.droppedFrameLongWindowMinimum &&
            dropsPerSecond >=
                thresholds.droppedFrameLongWindowRatePerSecond

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
    requiredUnhealthyWindows: Int = 2,
): Boolean {
    return unhealthyStreak >= requiredUnhealthyWindows &&
        engineMode == PlaybackEngineMode.HARDWARE &&
        softwareFallbackAvailable
}
