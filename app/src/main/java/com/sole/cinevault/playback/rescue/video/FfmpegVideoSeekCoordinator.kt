package com.sole.cinevault.playback.rescue.video

/**
 * Coordinates seek/discontinuity handling across the FFmpeg rescue video path.
 *
 * A seek must invalidate queued pre-seek frames before the playback clock is
 * moved. Native decoder seek wiring is intentionally left for a later slice.
 */
class FfmpegVideoSeekCoordinator(
    private val framePump: FfmpegVideoScheduledFramePump,
    private val timeline: FfmpegVideoTimelineCoordinator,
) {
    fun seekTo(positionMs: Long): Long {
        val targetMs = positionMs.coerceAtLeast(0L)

        framePump.flush()
        timeline.onExplicitSeek(targetMs)

        return targetMs
    }
}
