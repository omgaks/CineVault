package com.sole.cinevault.playback.rescue.video

enum class FfmpegVideoFrameScheduleDecision { PRESENT, WAIT, DROP_LATE }

class FfmpegVideoFrameScheduler(
    private val earlyToleranceMs: Long = DEFAULT_EARLY_TOLERANCE_MS,
    private val lateDropThresholdMs: Long = DEFAULT_LATE_DROP_THRESHOLD_MS,
) {
    init {
        require(earlyToleranceMs >= 0L) { "earlyToleranceMs must be >= 0" }
        require(lateDropThresholdMs >= 0L) { "lateDropThresholdMs must be >= 0" }
    }

    fun decide(framePresentationTimeMs: Long, playbackPositionMs: Long): FfmpegVideoFrameScheduleDecision {
        require(framePresentationTimeMs >= 0L) { "framePresentationTimeMs must be >= 0" }
        require(playbackPositionMs >= 0L) { "playbackPositionMs must be >= 0" }
        val deltaMs = framePresentationTimeMs - playbackPositionMs
        return when {
            deltaMs > earlyToleranceMs -> FfmpegVideoFrameScheduleDecision.WAIT
            deltaMs < -lateDropThresholdMs -> FfmpegVideoFrameScheduleDecision.DROP_LATE
            else -> FfmpegVideoFrameScheduleDecision.PRESENT
        }
    }

    companion object {
        const val DEFAULT_EARLY_TOLERANCE_MS = 15L
        const val DEFAULT_LATE_DROP_THRESHOLD_MS = 120L
    }
}
