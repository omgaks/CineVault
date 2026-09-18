package com.sole.cinevault.playback.rescue.video

/**
 * Monotonic playback clock contract for FFmpeg video rescue.
 *
 * The real native decoder will eventually publish decoded presentation
 * timestamps through this boundary. Keeping clock ownership outside the UI
 * prevents VideoPlayerScreen from becoming responsible for decoder timing.
 */
interface FfmpegVideoPlaybackClock {
    val positionMs: Long
    val isRunning: Boolean

    fun start(positionMs: Long)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun stop()
}

/**
 * Deterministic clock state used by the rescue architecture until the native
 * FFmpeg clock adapter is connected.
 *
 * It intentionally does not advance with wall-clock time. Native decoded PTS
 * updates will become the authoritative source later.
 */
class FfmpegVideoPlaybackClockState : FfmpegVideoPlaybackClock {

    override var positionMs: Long = 0L
        private set

    override var isRunning: Boolean = false
        private set

    override fun start(positionMs: Long) {
        this.positionMs = positionMs.coerceAtLeast(0L)
        isRunning = true
    }

    override fun pause() {
        isRunning = false
    }

    override fun resume() {
        isRunning = true
    }

    override fun seekTo(positionMs: Long) {
        this.positionMs = positionMs.coerceAtLeast(0L)
    }

    override fun stop() {
        isRunning = false
        positionMs = 0L
    }
}
