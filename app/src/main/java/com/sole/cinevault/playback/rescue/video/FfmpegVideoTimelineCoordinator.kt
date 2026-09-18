package com.sole.cinevault.playback.rescue.video

/**
 * Presentation timestamp update emitted by the future FFmpeg/native decoder.
 */
data class FfmpegVideoPresentationTimestamp(
    val presentationTimeMs: Long,
) {
    init {
        require(presentationTimeMs >= 0L) {
            "presentationTimeMs must be >= 0"
        }
    }
}

/**
 * Applies decoder presentation timestamps to the rescue playback clock.
 *
 * Small backwards timestamp jitter is ignored so UI/progress state does not
 * visibly jump backwards. Large backwards movement is accepted because it can
 * represent a real seek or decoder discontinuity.
 */
class FfmpegVideoTimelineCoordinator(
    private val clock: FfmpegVideoPlaybackClock,
    private val backwardJitterToleranceMs: Long = 250L,
) {
    init {
        require(backwardJitterToleranceMs >= 0L) {
            "backwardJitterToleranceMs must be >= 0"
        }
    }

    fun onPresentationTimestamp(
        timestamp: FfmpegVideoPresentationTimestamp,
    ) {
        val current = clock.positionMs
        val incoming = timestamp.presentationTimeMs

        if (
            incoming >= current ||
            current - incoming > backwardJitterToleranceMs
        ) {
            clock.seekTo(incoming)
        }
    }

    fun onExplicitSeek(positionMs: Long) {
        clock.seekTo(positionMs.coerceAtLeast(0L))
    }
}
