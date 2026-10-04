package com.sole.cinevault.subtitles

/**
 * Works out where subtitles should sit so they are close to the picture.
 *
 * A fixed "2% from the bottom of the screen" is fine for a full-screen video but, in portrait
 * (or with a wide film on a tablet), it puts the text on the far edge of a big black area,
 * nowhere near the action. Here the text is placed just under the picture when the black bar
 * below it is tall enough, and just inside the bottom of the picture when it is not.
 *
 * Pure maths, covered by plain JVM tests.
 */
object SubtitleAutoPlacement {

    /**
     * @return the bottom padding as a fraction of the view height, or null when the view or
     *   video size isn't known yet (the caller then keeps its manual value).
     */
    fun bottomPaddingFraction(
        viewWidthPx: Float,
        viewHeightPx: Float,
        videoAspect: Float,
        textPx: Float,
        lines: Int = 2,
        fitMode: Boolean = true,
    ): Float? {
        if (viewWidthPx <= 0f || viewHeightPx <= 0f || videoAspect <= 0f || textPx <= 0f) return null

        val viewAspect = viewWidthPx / viewHeightPx
        val videoHeight = when {
            !fitMode -> viewHeightPx
            videoAspect > viewAspect -> viewWidthPx / videoAspect
            else -> viewHeightPx
        }
        val bar = ((viewHeightPx - videoHeight) / 2f).coerceAtLeast(0f)
        val block = textPx * 1.25f * lines.coerceAtLeast(1)
        val gap = textPx * 0.5f

        val paddingPx =
            if (bar >= block + gap * 2f) {
                // Room below the picture: sit just under it, inside the black bar.
                bar - gap - block
            } else {
                // No usable bar: sit just inside the bottom of the picture.
                bar + videoHeight * 0.04f
            }
        return (paddingPx / viewHeightPx).coerceIn(0f, 0.9f)
    }
}
