package com.sole.cinevault.playback.rescue.video

/**
 * Gates asynchronous decoder callbacks by generation.
 *
 * A new generation is opened whenever the decoder timeline is invalidated.
 * Frames from older generations are rejected instead of entering B11/B12.
 */
class FfmpegVideoDecodeGenerationGate(
    initialGeneration: FfmpegVideoDecodeGeneration =
        FfmpegVideoDecodeGeneration.INITIAL,
) {
    var activeGeneration: FfmpegVideoDecodeGeneration = initialGeneration
        private set

    fun advance(): FfmpegVideoDecodeGeneration {
        activeGeneration = activeGeneration.next()
        return activeGeneration
    }

    fun accepts(generation: FfmpegVideoDecodeGeneration): Boolean =
        generation == activeGeneration
}

data class FfmpegGenerationGateResult(
    val accepted: Boolean,
    val activeGeneration: FfmpegVideoDecodeGeneration,
)

/**
 * Entry point for frames arriving asynchronously from the future native
 * FFmpeg decoder.
 */
class FfmpegGenerationAwareVideoFrameIngress(
    private val gate: FfmpegVideoDecodeGenerationGate,
    private val framePump: FfmpegVideoScheduledFramePump,
) {
    fun onDecodedFrame(
        taggedFrame: FfmpegGenerationTaggedVideoFrame,
    ): FfmpegGenerationGateResult {
        if (!gate.accepts(taggedFrame.generation)) {
            return FfmpegGenerationGateResult(
                accepted = false,
                activeGeneration = gate.activeGeneration,
            )
        }

        framePump.enqueueDecodedFrame(taggedFrame.frame)

        return FfmpegGenerationGateResult(
            accepted = true,
            activeGeneration = gate.activeGeneration,
        )
    }
}
