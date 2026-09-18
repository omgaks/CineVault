package com.sole.cinevault.playback.rescue.video

/**
 * Result of routing one native-decoder event.
 */
sealed interface FfmpegVideoDecodeEventResult {
    data object REJECTED_STALE : FfmpegVideoDecodeEventResult
    data object FRAME_ACCEPTED : FfmpegVideoDecodeEventResult
    data object END_OF_STREAM : FfmpegVideoDecodeEventResult
    data class FAILED(val message: String) : FfmpegVideoDecodeEventResult
}

/**
 * Single generation-aware ingress for future native FFmpeg callbacks.
 *
 * B14 protected frame callbacks. B15 extends that rule to terminal callbacks
 * too, so an old decoder cannot falsely end or fail the active rescue session.
 */
class FfmpegVideoDecodeEventRouter(
    private val gate: FfmpegVideoDecodeGenerationGate,
    private val frameIngress: FfmpegGenerationAwareVideoFrameIngress,
) {
    var terminalState: FfmpegVideoDecodeTerminalState =
        FfmpegVideoDecodeTerminalState.NONE
        private set

    var failureMessage: String? = null
        private set

    fun route(event: FfmpegVideoDecodeEvent): FfmpegVideoDecodeEventResult {
        if (!gate.accepts(event.generation)) {
            return FfmpegVideoDecodeEventResult.REJECTED_STALE
        }

        return when (event) {
            is FfmpegVideoDecodeEvent.Frame -> {
                frameIngress.onDecodedFrame(
                    FfmpegGenerationTaggedVideoFrame(
                        generation = event.generation,
                        frame = event.frame,
                    ),
                )
                FfmpegVideoDecodeEventResult.FRAME_ACCEPTED
            }

            is FfmpegVideoDecodeEvent.EndOfStream -> {
                terminalState = FfmpegVideoDecodeTerminalState.END_OF_STREAM
                failureMessage = null
                FfmpegVideoDecodeEventResult.END_OF_STREAM
            }

            is FfmpegVideoDecodeEvent.Failure -> {
                terminalState = FfmpegVideoDecodeTerminalState.FAILED
                failureMessage = event.message
                FfmpegVideoDecodeEventResult.FAILED(event.message)
            }
        }
    }

    /**
     * Called when a new decoder generation becomes active.
     */
    fun resetTerminalState() {
        terminalState = FfmpegVideoDecodeTerminalState.NONE
        failureMessage = null
    }
}
