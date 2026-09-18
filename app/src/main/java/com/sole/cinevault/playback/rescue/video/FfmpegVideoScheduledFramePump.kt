package com.sole.cinevault.playback.rescue.video

sealed interface FfmpegVideoFramePresentationResult {
    data object EMPTY : FfmpegVideoFramePresentationResult
    data class WAITING(val frame: FfmpegDecodedVideoFrame) : FfmpegVideoFramePresentationResult
    data class PRESENTED(
        val frame: FfmpegDecodedVideoFrame,
        val droppedLateFrameCount: Int,
    ) : FfmpegVideoFramePresentationResult
    data class DROPPED_ONLY(val droppedLateFrameCount: Int) : FfmpegVideoFramePresentationResult
}

class FfmpegVideoScheduledFramePump(
    private val queue: FfmpegVideoFrameQueue,
    private val coordinator: FfmpegVideoFrameCoordinator,
    private val clock: FfmpegVideoPlaybackClock,
    private val scheduler: FfmpegVideoFrameScheduler = FfmpegVideoFrameScheduler(),
) {
    fun enqueueDecodedFrame(frame: FfmpegDecodedVideoFrame): FfmpegVideoFrameEnqueueResult {
        val dropped = queue.offer(frame)
        return FfmpegVideoFrameEnqueueResult(dropped, queue.size)
    }

    fun presentDueFrame(): FfmpegVideoFramePresentationResult {
        var droppedLate = 0
        while (true) {
            val next = queue.peek() ?: return if (droppedLate > 0) {
                FfmpegVideoFramePresentationResult.DROPPED_ONLY(droppedLate)
            } else FfmpegVideoFramePresentationResult.EMPTY

            when (scheduler.decide(next.presentationTimeMs, clock.positionMs)) {
                FfmpegVideoFrameScheduleDecision.WAIT ->
                    return FfmpegVideoFramePresentationResult.WAITING(next)
                FfmpegVideoFrameScheduleDecision.DROP_LATE -> {
                    queue.poll()
                    droppedLate++
                }
                FfmpegVideoFrameScheduleDecision.PRESENT -> {
                    val frame = requireNotNull(queue.poll())
                    coordinator.onDecodedFrame(frame)
                    return FfmpegVideoFramePresentationResult.PRESENTED(frame, droppedLate)
                }
            }
        }
    }

    fun flush() = queue.clear()
    val queuedFrameCount: Int get() = queue.size
}
