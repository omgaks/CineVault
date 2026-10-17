package com.sole.cinevault.playback.rescue.video

/** Maps media PTS and optional audio time onto one nonnegative presentation timeline. */
internal object FfmpegVideoClockPosition {
    fun resolve(
        originPtsMs: Long,
        originClockMs: Long,
        nowMs: Long,
        audioPositionMs: Long?,
        playbackSpeed: Float = 1f,
        fallbackPositionMs: Long = originPtsMs,
    ): Long {
        // Audio may not have caught up after a keyframe seek. Until it does,
        // advance video using the monotonic clock rather than waiting forever.
        if (audioPositionMs != null && audioPositionMs >= originPtsMs) {
            return (audioPositionMs - originPtsMs).coerceAtLeast(0L)
        }
        val speed = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
        val elapsed = ((nowMs - originClockMs).coerceAtLeast(0L) * speed).toLong()
        return (fallbackPositionMs - originPtsMs).coerceAtLeast(0L) + elapsed
    }
}
