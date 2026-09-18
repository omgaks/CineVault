package com.sole.cinevault.playback.rescue.video

enum class FfmpegVideoDiscontinuityReason {
    SEEK,
    SOURCE_CHANGE,
    DECODER_RESET,
}

/**
 * Central invalidation point for timeline discontinuities.
 *
 * Every discontinuity flushes stale decoded frames. Seek additionally updates
 * the playback timeline immediately; source/decoder resets leave the current
 * position untouched until the replacement decoder publishes a new timestamp.
 */
class FfmpegVideoDiscontinuityCoordinator(
    private val framePump: FfmpegVideoScheduledFramePump,
    private val timeline: FfmpegVideoTimelineCoordinator,
) {
    fun onDiscontinuity(
        reason: FfmpegVideoDiscontinuityReason,
        positionMs: Long? = null,
    ) {
        framePump.flush()

        when (reason) {
            FfmpegVideoDiscontinuityReason.SEEK -> {
                requireNotNull(positionMs) {
                    "positionMs is required for SEEK discontinuity"
                }
                timeline.onExplicitSeek(positionMs.coerceAtLeast(0L))
            }

            FfmpegVideoDiscontinuityReason.SOURCE_CHANGE,
            FfmpegVideoDiscontinuityReason.DECODER_RESET -> Unit
        }
    }
}
