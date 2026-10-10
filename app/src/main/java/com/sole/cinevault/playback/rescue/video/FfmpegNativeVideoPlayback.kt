package com.sole.cinevault.playback.rescue.video

import android.os.SystemClock
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Synchronous continuous native-video presentation loop.
 *
 * The caller supplies a worker thread and owns the Surface. Stop is cooperative;
 * do not close the decoder concurrently with nextFrame(). Audio, seeking and
 * Media3 rescue activation are intentionally reserved for Slice 2.
 */
internal class FfmpegNativeVideoPlayback(
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
    private val sleepMs: (Long) -> Unit = Thread::sleep,
    private val scheduler: FfmpegVideoFrameScheduler = FfmpegVideoFrameScheduler(),
) {
    private val stopped = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    data class Stats(val presented: Int, val dropped: Int)

    fun pause() { paused.set(true) }
    fun resume() { paused.set(false) }
    fun stop() { stopped.set(true) }

    /** Runs on a background thread and closes its decoder even on failure. */
    fun play(path: String, surface: Surface): Stats {
        require(surface.isValid) { "Video surface is unavailable" }
        stopped.set(false)
        var presented = 0
        var dropped = 0
        var originPts: Long? = null
        var originClock = clockMs()
        var pauseStarted: Long? = null
        FfmpegNativeDecoderSession.open(path).use { decoder ->
            while (!stopped.get()) {
                if (paused.get()) {
                    if (pauseStarted == null) pauseStarted = clockMs()
                    sleepMs(10L)
                    continue
                }
                pauseStarted?.let { started ->
                    originClock += (clockMs() - started).coerceAtLeast(0L)
                    pauseStarted = null
                }
                val frame = decoder.nextFrame() ?: break
                if (originPts == null) {
                    originPts = frame.presentationTimeMs
                    originClock = clockMs()
                }
                val relativePts = (frame.presentationTimeMs - originPts).coerceAtLeast(0L)
                while (!stopped.get()) {
                    if (paused.get()) {
                        if (pauseStarted == null) pauseStarted = clockMs()
                        sleepMs(10L)
                        continue
                    }
                    pauseStarted?.let { started ->
                        originClock += (clockMs() - started).coerceAtLeast(0L)
                        pauseStarted = null
                    }
                    val position = (clockMs() - originClock).coerceAtLeast(0L)
                    when (scheduler.decide(relativePts, position)) {
                        FfmpegVideoFrameScheduleDecision.WAIT -> sleepMs(5L)
                        FfmpegVideoFrameScheduleDecision.DROP_LATE -> {
                            dropped++
                            break
                        }
                        FfmpegVideoFrameScheduleDecision.PRESENT -> {
                            if (!stopped.get()) {
                                FfmpegRgbaFrameRenderer.render(frame.image, surface)
                                presented++
                            }
                            break
                        }
                    }
                }
            }
        }
        return Stats(presented, dropped)
    }
}
