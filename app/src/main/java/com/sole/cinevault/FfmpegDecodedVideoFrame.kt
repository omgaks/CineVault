package com.sole.cinevault.playback.rescue.video

/**
 * Metadata for one decoded FFmpeg video frame.
 *
 * Pixel/native-buffer ownership intentionally stays outside this pure Kotlin
 * model. The native adapter will later associate its real frame handle with
 * this presentation metadata.
 */
data class FfmpegDecodedVideoFrame(
    val presentationTimeMs: Long,
    val widthPx: Int,
    val heightPx: Int,
    val rotationDegrees: Int = 0,
) {
    init {
        require(presentationTimeMs >= 0L) { "presentationTimeMs must be >= 0" }
        require(widthPx > 0) { "widthPx must be > 0" }
        require(heightPx > 0) { "heightPx must be > 0" }
        require(rotationDegrees in VALID_ROTATIONS) {
            "rotationDegrees must be one of 0, 90, 180, 270"
        }
    }

    fun asOutputTarget(): FfmpegVideoOutputTarget =
        FfmpegVideoOutputTarget(
            widthPx = widthPx,
            heightPx = heightPx,
            rotationDegrees = rotationDegrees,
        )

    private companion object {
        val VALID_ROTATIONS = setOf(0, 90, 180, 270)
    }
}

/**
 * Consumer boundary for frames emitted by the future native FFmpeg decoder.
 */
fun interface FfmpegDecodedVideoFrameSink {
    fun onFrame(frame: FfmpegDecodedVideoFrame)
}
