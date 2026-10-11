package com.sole.cinevault.playback.rescue.video

/** Maps media PTS and optional audio time onto one nonnegative presentation timeline. */
internal object FfmpegVideoClockPosition {
    fun resolve(
        originPtsMs: Long,
        originClockMs: Long,
        nowMs: Long,
        audioPositionMs: Long?,
    ): Long {
        // Audio may not have caught up after a keyframe seek. Until it does,
        // advance video using the monotonic clock rather than waiting forever.
        if (audioPositionMs != null && audioPositionMs >= originPtsMs) {
            return (audioPositionMs - originPtsMs).coerceAtLeast(0L)
        }
        return (nowMs - originClockMs).coerceAtLeast(0L)
    }
}
