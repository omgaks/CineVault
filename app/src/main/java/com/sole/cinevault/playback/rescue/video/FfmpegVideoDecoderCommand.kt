package com.sole.cinevault.playback.rescue.video

/**
 * Commands CineVault may send to the future native FFmpeg video decoder.
 *
 * This is deliberately platform/native-library agnostic. JNI implementation
 * belongs behind FfmpegVideoDecoderCommandSink in a later slice.
 */
sealed interface FfmpegVideoDecoderCommand {
    data class Prepare(
        val generation: FfmpegVideoDecodeGeneration,
        val startPositionMs: Long,
    ) : FfmpegVideoDecoderCommand {
        init {
            require(startPositionMs >= 0L) { "startPositionMs must be >= 0" }
        }
    }

    data object Play : FfmpegVideoDecoderCommand
    data object Pause : FfmpegVideoDecoderCommand

    data class Seek(
        val generation: FfmpegVideoDecodeGeneration,
        val positionMs: Long,
    ) : FfmpegVideoDecoderCommand {
        init {
            require(positionMs >= 0L) { "positionMs must be >= 0" }
        }
    }

    data object Stop : FfmpegVideoDecoderCommand
    data object Release : FfmpegVideoDecoderCommand
}

fun interface FfmpegVideoDecoderCommandSink {
    fun send(command: FfmpegVideoDecoderCommand)
}
