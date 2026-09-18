package com.sole.cinevault.playback.rescue.video

/**
 * Identifies one logical native-decoder generation.
 *
 * After seek/source-change/reset, callbacks from the previous native decoder
 * may still arrive asynchronously. Generation tagging lets CineVault reject
 * those stale callbacks before they can enter the frame queue.
 */
@JvmInline
value class FfmpegVideoDecodeGeneration(val value: Long) {
    init {
        require(value >= 0L) { "generation must be >= 0" }
    }

    fun next(): FfmpegVideoDecodeGeneration {
        require(value < Long.MAX_VALUE) { "generation overflow" }
        return FfmpegVideoDecodeGeneration(value + 1L)
    }

    companion object {
        val INITIAL = FfmpegVideoDecodeGeneration(0L)
    }
}

data class FfmpegGenerationTaggedVideoFrame(
    val generation: FfmpegVideoDecodeGeneration,
    val frame: FfmpegDecodedVideoFrame,
)
