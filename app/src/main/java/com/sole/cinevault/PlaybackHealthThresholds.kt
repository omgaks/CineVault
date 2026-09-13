package com.sole.cinevault

data class PlaybackHealthThresholds(
    val firstFrameTimeoutMs: Long,
    val firstFrameProgressThresholdMs: Long,
    val startupStallTimeoutMs: Long,
    val droppedFrameShortWindowMinimum: Int,
    val droppedFrameShortWindowRatePerSecond: Double,
    val droppedFrameLongWindowMinimum: Int,
    val droppedFrameLongWindowRatePerSecond: Double,
    val requiredUnhealthyDroppedFrameWindows: Int,
)

val ConservativePlaybackHealthThresholds = PlaybackHealthThresholds(
    firstFrameTimeoutMs = 8_000L,
    firstFrameProgressThresholdMs = 1_500L,
    startupStallTimeoutMs = 12_000L,
    droppedFrameShortWindowMinimum = 24,
    droppedFrameShortWindowRatePerSecond = 10.0,
    droppedFrameLongWindowMinimum = 40,
    droppedFrameLongWindowRatePerSecond = 8.0,
    requiredUnhealthyDroppedFrameWindows = 2,
)

private val ElevatedPlaybackHealthThresholds = PlaybackHealthThresholds(
    firstFrameTimeoutMs = 7_000L,
    firstFrameProgressThresholdMs = 1_250L,
    startupStallTimeoutMs = 10_000L,
    droppedFrameShortWindowMinimum = 20,
    droppedFrameShortWindowRatePerSecond = 8.0,
    droppedFrameLongWindowMinimum = 32,
    droppedFrameLongWindowRatePerSecond = 6.5,
    requiredUnhealthyDroppedFrameWindows = 2,
)

/**
 * Adaptive health monitoring never forces software on its own.
 * It only decides how quickly a native decoder is considered unhealthy.
 */
fun playbackHealthThresholdsFor(
    assessment: VideoPlaybackCompatibilityAssessment,
): PlaybackHealthThresholds {
    return when (assessment.risk) {
        VideoCompatibilityRisk.ELEVATED,
        VideoCompatibilityRisk.HIGH -> ElevatedPlaybackHealthThresholds

        VideoCompatibilityRisk.LOW,
        VideoCompatibilityRisk.UNKNOWN -> ConservativePlaybackHealthThresholds
    }
}
