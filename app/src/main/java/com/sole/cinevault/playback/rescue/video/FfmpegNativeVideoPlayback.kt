package com.sole.cinevault.playback.rescue.video

import android.os.SystemClock
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Synchronous continuous native-video presentation loop.
 *
 * The caller supplies a worker thread and owns the Surface. Stop is cooperative;
 * do not close the decoder concurrently with nextFrame(). An optional audio
 * clock can govern frame pacing; Media3 rescue activation is not wired here.
 */
internal class FfmpegNativeVideoPlayback(
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
    private val sleepMs: (Long) -> Unit = Thread::sleep,
    private val scheduler: FfmpegVideoFrameScheduler = FfmpegVideoFrameScheduler(),
    private val audioPositionMs: (() -> Long?)? = null,
    private val playbackSpeed: () -> Float = { 1f },
) {
    @Volatile var presentedFrames: Int = 0
        private set
    @Volatile var droppedFrames: Int = 0
        private set

    private val stopped = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val pendingSeekMs = AtomicLong(NO_SEEK)

    data class Stats(val presented: Int, val dropped: Int)

    fun pause() { paused.set(true) }
    fun resume() { paused.set(false) }
    fun stop() { stopped.set(true) }

    /** True after stop was requested, including before the worker starts. */
    fun isStopRequested(): Boolean = stopped.get()

    /** Queues a seek; native decoder access remains confined to the playback worker. */
    fun seekTo(positionMs: Long) {
        require(positionMs >= 0L)
        pendingSeekMs.set(positionMs)
    }

    /** Runs on a background thread and closes its decoder even on failure. */
    fun play(path: String, surface: Surface): Stats {
        require(surface.isValid) { "Video surface is unavailable" }
        // Never clear a stop requested between worker creation and entry.
        // A playback instance is single-use; a new rescue attempt gets a new instance.
        if (stopped.get()) return Stats(presented = 0, dropped = 0)
        var presented = 0
        var dropped = 0
        var lastPresentationAt: Long? = null
        var originPts: Long? = null
        var originClock = clockMs()
        var fallbackPosition = 0L
        var fallbackClock = originClock
        var pauseStarted: Long? = null
        FfmpegRgbaFrameRenderer().use { renderer ->
        FfmpegNativeDecoderSession.open(path).use { decoder ->
            while (!stopped.get()) {
                val seek = pendingSeekMs.getAndSet(NO_SEEK)
                if (seek != NO_SEEK) {
                    decoder.seekTo(seek)
                    originPts = seek
                    originClock = clockMs()
                    fallbackPosition = seek
                    fallbackClock = originClock
                    pauseStarted = null
                }
                if (paused.get()) {
                    if (pauseStarted == null) pauseStarted = clockMs()
                    sleepMs(10L)
                    continue
                }
                pauseStarted?.let { started ->
                    val pausedFor = (clockMs() - started).coerceAtLeast(0L)
                    originClock += pausedFor
                    fallbackClock += pausedFor
                    pauseStarted = null
                }
                val frame = decoder.nextFrame() ?: break
                if (pendingSeekMs.get() != NO_SEEK) continue
                if (originPts != null && frame.presentationTimeMs < originPts) continue
                if (originPts == null) {
                    originPts = frame.presentationTimeMs
                    originClock = clockMs()
                    fallbackPosition = frame.presentationTimeMs
                    fallbackClock = originClock
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
                        val pausedFor = (clockMs() - started).coerceAtLeast(0L)
                        originClock += pausedFor
                        fallbackClock += pausedFor
                        pauseStarted = null
                    }
                    val now = clockMs()
                    val audioTime = audioPositionMs?.invoke()
                    if (audioTime != null && audioTime >= originPts) {
                        fallbackPosition = audioTime
                        fallbackClock = now
                    }
                    val position = FfmpegVideoClockPosition.resolve(
                        originPtsMs = originPts,
                        originClockMs = fallbackClock,
                        nowMs = now,
                        audioPositionMs = audioTime,
                        playbackSpeed = playbackSpeed(),
                        fallbackPositionMs = fallbackPosition,
                    )
                    when (scheduler.decideWithPresentationProgress(relativePts, position, lastPresentationAt?.let { (now - it).coerceAtLeast(0L) } ?: Long.MAX_VALUE)) {
                        FfmpegVideoFrameScheduleDecision.WAIT -> sleepMs(5L)
                        FfmpegVideoFrameScheduleDecision.DROP_LATE -> {
                            dropped++
                            droppedFrames = dropped
                            break
                        }
                        FfmpegVideoFrameScheduleDecision.PRESENT -> {
                            if (!stopped.get()) {
                                renderer.render(frame.image, surface)
                                presented++
                                presentedFrames = presented
                                lastPresentationAt = clockMs()
                            }
                            break
                        }
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
