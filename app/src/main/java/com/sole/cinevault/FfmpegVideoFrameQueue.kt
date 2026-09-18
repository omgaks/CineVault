package com.sole.cinevault.playback.rescue.video

/**
 * Small bounded queue between the future native FFmpeg decoder and renderer.
 *
 * Video rescue must never allow decoded frames to grow without limit. When
 * the renderer falls behind, the oldest queued frame is dropped so playback
 * can recover toward the newest decoded presentation time.
 */
class FfmpegVideoFrameQueue(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    private val frames = ArrayDeque<FfmpegDecodedVideoFrame>()

    val size: Int
        get() = frames.size

    val isEmpty: Boolean
        get() = frames.isEmpty()

    /**
     * Adds a frame and returns the frame dropped because of capacity, if any.
     */
    fun offer(frame: FfmpegDecodedVideoFrame): FfmpegDecodedVideoFrame? {
        val dropped = if (frames.size >= capacity) {
            frames.removeFirst()
        } else {
            null
        }

        frames.addLast(frame)
        return dropped
    }

    fun poll(): FfmpegDecodedVideoFrame? =
        if (frames.isEmpty()) null else frames.removeFirst()

    fun peek(): FfmpegDecodedVideoFrame? =
        frames.firstOrNull()

    fun clear() {
        frames.clear()
    }

    companion object {
        const val DEFAULT_CAPACITY = 3
    }
}
