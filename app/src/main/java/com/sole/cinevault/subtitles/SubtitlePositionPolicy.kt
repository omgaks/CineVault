package com.sole.cinevault.subtitles

/**
 * Single source of truth for user-controlled subtitle vertical placement.
 *
 * Media3 bottom padding grows upward from the bottom edge. Very large values
 * can push multi-line or large-font cues beyond the visible video area.
 * The safe ceiling therefore becomes slightly lower as text size increases.
 */
object SubtitlePositionPolicy {
    const val MIN_BOTTOM_PADDING = 0.02f
    private const val MAX_AT_SMALL_TEXT = 0.62f
    private const val MAX_AT_LARGE_TEXT = 0.48f
    private const val MIN_TEXT_SIZE_SP = 12f
    private const val MAX_TEXT_SIZE_SP = 32f

    fun maxBottomPadding(textSizeSp: Float): Float {
        val normalized =
            ((textSizeSp.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP) - MIN_TEXT_SIZE_SP) /
                (MAX_TEXT_SIZE_SP - MIN_TEXT_SIZE_SP))
        return MAX_AT_SMALL_TEXT +
            (MAX_AT_LARGE_TEXT - MAX_AT_SMALL_TEXT) * normalized
    }

    fun sanitize(bottomPadding: Float, textSizeSp: Float): Float =
        bottomPadding.coerceIn(
            MIN_BOTTOM_PADDING,
            maxBottomPadding(textSizeSp),
        )
}
