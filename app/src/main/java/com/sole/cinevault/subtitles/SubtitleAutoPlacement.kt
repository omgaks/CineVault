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

    /**
     * Same placement rule as [bottomPaddingFraction], but from the REAL positions of the views on
     * screen instead of from assumed screen/video sizes: the subtitle view's bottom edge and
     * height, and the top/bottom edge of the picture, all in the same coordinate space (pixels).
     * Returns null when the geometry isn't usable yet (not laid out).
     */
    fun bottomPaddingFractionFromRects(
        subtitleViewBottomPx: Float,
        subtitleViewHeightPx: Float,
        pictureTopPx: Float,
        pictureBottomPx: Float,
        textPx: Float,
        lines: Int = 2,
    ): Float? {
        if (subtitleViewHeightPx <= 0f || textPx <= 0f) return null
        val pictureHeight = pictureBottomPx - pictureTopPx
        if (pictureHeight <= 0f) return null

        val bar = (subtitleViewBottomPx - pictureBottomPx).coerceAtLeast(0f)
        val block = textPx * 1.25f * lines.coerceAtLeast(1)
        val gap = textPx * 0.5f

        // Text block's bottom edge, measured from the subtitle view's bottom.
        val paddingPx =
            if (bar >= block + gap * 2f) {
                // Room under the picture: sit just beneath it.
                bar - gap - block
            } else {
                // No usable bar: just inside the bottom of the picture.
                bar + pictureHeight * 0.04f
            }
        return (paddingPx / subtitleViewHeightPx).coerceIn(0f, 0.9f)
    }
}
