package com.sole.cinevault.playback.rescue.video

import android.os.SystemClock
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Synchronous continuous native-video presentation loop.
 *
 * The caller supplies a worker thread and owns the Surface. Stop is cooperative;
 * do not close the decoder concurrently with nextFrame(). Audio, seeking and
 * Media3 rescue activation is not wired here. An optional audio clock can
 * govern video frame pacing once the host audio renderer is connected.
 */
internal class FfmpegNativeVideoPlayback(
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
    private val sleepMs: (Long) -> Unit = Thread::sleep,
    private val scheduler: FfmpegVideoFrameScheduler = FfmpegVideoFrameScheduler(),
    private val audioPositionMs: (() -> Long?)? = null,
) {
    private val stopped = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val pendingSeekMs = AtomicLong(NO_SEEK)

    data class Stats(val presented: Int, val dropped: Int)

    fun pause() { paused.set(true) }
    fun resume() { paused.set(false) }
    fun stop() { stopped.set(true) }

    /** Queues a seek; native decoder access remains confined to the playback worker. */
    fun seekTo(positionMs: Long) {
        require(positionMs >= 0L)
        pendingSeekMs.set(positionMs)
    }

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
                val seek = pendingSeekMs.getAndSet(NO_SEEK)
                if (seek != NO_SEEK) {
                    decoder.seekTo(seek)
                    originPts = seek
                    originClock = clockMs()
                    pauseStarted = null
                }
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
                if (pendingSeekMs.get() != NO_SEEK) continue
                if (originPts != null && frame.presentationTimeMs < originPts) continue
                if (originPts == null) {
                    originPts = frame.presentationTimeMs
                    originClock = clockMs()
                }
                val relativePts = (frame.presentationTimeMs - originPts).coerceAtLeast(0L)
                while (!stopped.get()) {
                    if (pendingSeekMs.get() != NO_SEEK) break
                    if (paused.get()) {
                        if (pauseStarted == null) pauseStarted = clockMs()
                        sleepMs(10L)
                        continue
                    }
                    pauseStarted?.let { started ->
                        originClock += (clockMs() - started).coerceAtLeast(0L)
                        pauseStarted = null
                    }
                    // Prefer the audio clock when the host supplies a valid position.
                    // Otherwise use the monotonic video clock.
                    val audioPosition = audioPositionMs?.invoke()
                    val position = if (audioPosition != null && audioPosition >= 0L) {
                        audioPosition
                    } else {
                        (clockMs() - originClock).coerceAtLeast(0L)
                    }
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

    private companion object {
        const val NO_SEEK = -1L
    }
}
