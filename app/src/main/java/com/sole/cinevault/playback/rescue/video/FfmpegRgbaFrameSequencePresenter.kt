package com.sole.cinevault.playback.rescue.video

import android.os.SystemClock
import android.view.Surface

/**
 * Presents a bounded stream of already-decoded RGBA frames on a worker thread.
 *
 * The producer remains responsible for native decoding and timestamps.
 * This adapter deliberately does not start threads, own the Surface, or
 * advertise a working FFmpeg rescue backend.
 */
internal class FfmpegRgbaFrameSequencePresenter(
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
    private val sleepMs: (Long) -> Unit = Thread::sleep,
    private val scheduler: FfmpegVideoFrameScheduler = FfmpegVideoFrameScheduler(),
) {
    data class TimedFrame(
        val presentationTimeMs: Long,
        val image: FfmpegContainerProbeLoader.RgbaFrame,
    ) {
        init {
            require(presentationTimeMs >= 0L)
        }
    }

    data class Stats(val presented: Int, val dropped: Int)

    /**
     * Returns promptly if cancelled. All callbacks and Surface access happen
     * on the caller's thread; never call this on the Android main thread.
     */
    fun present(
        frames: Iterable<TimedFrame>,
        surface: Surface,
        isCancelled: () -> Boolean = { false },
    ): Stats {
        require(surface.isValid) { "Video surface is unavailable" }
        val originMs = clockMs()
        var presented = 0
        var dropped = 0
        var previousPts = -1L
        for (frame in frames) {
            if (isCancelled()) break
            require(frame.presentationTimeMs >= previousPts) {
                "Frames must be in presentation timestamp order"
            }
            previousPts = frame.presentationTimeMs
            while (!isCancelled()) {
                val positionMs = (clockMs() - originMs).coerceAtLeast(0L)
                when (scheduler.decide(frame.presentationTimeMs, positionMs)) {
                    FfmpegVideoFrameScheduleDecision.WAIT -> {
                        // Short sleeps make stop/release responsive and avoid
                        // blocking an Android UI thread with frame pacing.
                        sleepMs(5L)
                    }
                    FfmpegVideoFrameScheduleDecision.DROP_LATE -> {
                        dropped++
                        break
                    }
                    FfmpegVideoFrameScheduleDecision.PRESENT -> {
                        if (isCancelled()) break
                        FfmpegRgbaFrameRenderer.render(frame.image, surface)
                        presented++
                        break
                    }
                }
            }
        }
        return Stats(presented, dropped)
    }
}
