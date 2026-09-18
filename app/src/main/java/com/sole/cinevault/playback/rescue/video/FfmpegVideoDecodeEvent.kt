package com.sole.cinevault.playback.rescue.video

/**
 * Events expected from the future native FFmpeg video decoder.
 *
 * Every asynchronous event is generation-tagged so callbacks from an invalid
 * decoder generation can be rejected consistently, not only decoded frames.
 */
sealed interface FfmpegVideoDecodeEvent {
    val generation: FfmpegVideoDecodeGeneration

    data class Frame(
        override val generation: FfmpegVideoDecodeGeneration,
        val frame: FfmpegDecodedVideoFrame,
    ) : FfmpegVideoDecodeEvent

    data class EndOfStream(
        override val generation: FfmpegVideoDecodeGeneration,
    ) : FfmpegVideoDecodeEvent

    data class Failure(
        override val generation: FfmpegVideoDecodeGeneration,
        val message: String,
    ) : FfmpegVideoDecodeEvent {
        init {
            require(message.isNotBlank()) { "message must not be blank" }
        }
    }
}

enum class FfmpegVideoDecodeTerminalState {
    NONE,
    END_OF_STREAM,
    FAILED,
}
