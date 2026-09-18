package com.sole.cinevault.playback.rescue.video

/**
 * Owns the cooperating Kotlin-side pieces of one FFmpeg video rescue runtime.
 *
 * B18 is the composition root: callers no longer need to manually construct
 * the clock, queue, generation gate, event router, bridge adapter and decoder
 * controller in the correct order.
 */
class FfmpegVideoRescueRuntime private constructor(
    val output: FfmpegVideoOutputBinding,
    val clock: FfmpegVideoPlaybackClockState,
    val timeline: FfmpegVideoTimelineCoordinator,
    val framePump: FfmpegVideoScheduledFramePump,
    val generationGate: FfmpegVideoDecodeGenerationGate,
    val eventRouter: FfmpegVideoDecodeEventRouter,
    val bridgeAdapter: FfmpegVideoNativeBridgeAdapter,
    val decoderController: FfmpegVideoDecoderController,
) {
    var isReleased: Boolean = false
        private set

    fun createNativeBridge() {
        check(!isReleased) { "rescue runtime is released" }
        bridgeAdapter.create()
    }

    fun release() {
        if (isReleased) return

        framePump.flush()
        output.detach()
        clock.stop()
        decoderController.release()
        isReleased = true
    }

    companion object {
        fun create(
            nativeBridge: FfmpegVideoNativeBridge,
            frameSink: FfmpegDecodedVideoFrameSink,
            frameQueueCapacity: Int = FfmpegVideoFrameQueue.DEFAULT_CAPACITY,
            backwardJitterToleranceMs: Long = 250L,
            frameScheduler: FfmpegVideoFrameScheduler =
                FfmpegVideoFrameScheduler(),
        ): FfmpegVideoRescueRuntime {
            require(frameQueueCapacity > 0) {
                "frameQueueCapacity must be > 0"
            }

            val output = FfmpegVideoOutputBinding()
            val clock = FfmpegVideoPlaybackClockState()
            val timeline = FfmpegVideoTimelineCoordinator(
                clock = clock,
                backwardJitterToleranceMs = backwardJitterToleranceMs,
            )
            val frameCoordinator = FfmpegVideoFrameCoordinator(
                output = output,
                timeline = timeline,
                sink = frameSink,
            )
            val framePump = FfmpegVideoScheduledFramePump(
                queue = FfmpegVideoFrameQueue(frameQueueCapacity),
                coordinator = frameCoordinator,
                clock = clock,
                scheduler = frameScheduler,
            )
            val generationGate = FfmpegVideoDecodeGenerationGate()
            val frameIngress = FfmpegGenerationAwareVideoFrameIngress(
                gate = generationGate,
                framePump = framePump,
            )
            val eventRouter = FfmpegVideoDecodeEventRouter(
                gate = generationGate,
                frameIngress = frameIngress,
            )
            val bridgeAdapter = FfmpegVideoNativeBridgeAdapter(
                bridge = nativeBridge,
                eventRouter = eventRouter,
            )
            val decoderController = FfmpegVideoDecoderController(
                gate = generationGate,
                commandSink = bridgeAdapter,
            )

            return FfmpegVideoRescueRuntime(
                output = output,
                clock = clock,
                timeline = timeline,
                framePump = framePump,
                generationGate = generationGate,
                eventRouter = eventRouter,
                bridgeAdapter = bridgeAdapter,
                decoderController = decoderController,
            )
        }
    }
}
