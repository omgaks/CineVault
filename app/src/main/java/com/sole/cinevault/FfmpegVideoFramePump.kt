package com.sole.cinevault.playback.rescue.video

/**
 * Result of enqueueing one decoded frame.
 */
data class FfmpegVideoFrameEnqueueResult(
    val droppedFrame: FfmpegDecodedVideoFrame?,
    val queuedFrameCount: Int,
)

/**
 * Bounded handoff from decoder production to frame presentation.
 *
 * enqueueDecodedFrame() is called by the future decoder side.
 * presentNextFrame() is called by the future render/vsync side.
 *
 * Keeping these operations separate prevents decoding speed from directly
 * controlling UI/render timing.
 */
class FfmpegVideoFramePump(
    private val queue: FfmpegVideoFrameQueue,
    private val coordinator: FfmpegVideoFrameCoordinator,
) {
    fun enqueueDecodedFrame(
        frame: FfmpegDecodedVideoFrame,
    ): FfmpegVideoFrameEnqueueResult {
        val dropped = queue.offer(frame)
        return FfmpegVideoFrameEnqueueResult(
            droppedFrame = dropped,
            queuedFrameCount = queue.size,
        )
    }

    fun presentNextFrame(): FfmpegDecodedVideoFrame? {
        val frame = queue.poll() ?: return null
        coordinator.onDecodedFrame(frame)
        return frame
    }

    /**
     * Seek/discontinuity safety: stale pre-seek frames must never render after
     * the playback position has jumped.
     */
    fun flush() {
        queue.clear()
    }

    val queuedFrameCount: Int
        get() = queue.size
}
