package com.sole.cinevault.playback.rescue.video

/**
 * Stable description of the video target made available to a future native
 * FFmpeg decoder.
 *
 * The rescue package intentionally does not depend on Android Surface here.
 * A later Android/native adapter can own that platform object while this
 * contract remains deterministic and unit-testable.
 */
data class FfmpegVideoOutputTarget(
    val widthPx: Int,
    val heightPx: Int,
    val rotationDegrees: Int = 0,
) {
    init {
        require(widthPx > 0) { "widthPx must be > 0" }
        require(heightPx > 0) { "heightPx must be > 0" }
        require(rotationDegrees in VALID_ROTATIONS) {
            "rotationDegrees must be one of 0, 90, 180, 270"
        }
    }

    val displayWidthPx: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) {
            heightPx
        } else {
            widthPx
        }

    val displayHeightPx: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) {
            widthPx
        } else {
            heightPx
        }

    private companion object {
        val VALID_ROTATIONS = setOf(0, 90, 180, 270)
    }
}

/**
 * Boundary implemented later by the Android/native rendering adapter.
 *
 * attach() and detach() are deliberately explicit so decoder/session teardown
 * cannot silently retain an old rendering target.
 */
interface FfmpegVideoOutput {
    val target: FfmpegVideoOutputTarget?

    fun attach(target: FfmpegVideoOutputTarget)
    fun detach()
}
