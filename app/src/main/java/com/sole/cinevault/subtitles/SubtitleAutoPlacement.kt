package com.sole.cinevault.subtitles

/**
 * Places subtitles against the REAL visible picture, never against the device/window bottom.
 *
 * CineVault deliberately keeps subtitle cues inside the video frame. Letterbox/pillarbox space is
 * presentation chrome, not subtitle space: using it made portrait subtitles float far below the
 * movie and made landscape placement vary with aspect ratio.
 *
 * The returned value is Media3 SubtitleView bottom padding (fraction of SubtitleView height).
 * Pure maths, covered by JVM tests.
 */
object SubtitleAutoPlacement {
    private const val PICTURE_BOTTOM_SAFE_FRACTION = 0.075f

    /**
     * Calculates the default position from the player bounds and source aspect ratio.
     *
     * FIT: reconstruct the fitted picture rectangle and place the subtitle 7.5% of picture height
     * above its bottom edge. FILL/ZOOM: the visible picture is the whole viewport, so use the same
     * 7.5% safe margin from the viewport bottom.
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
        val pictureHeight = when {
            !fitMode -> viewHeightPx
            videoAspect > viewAspect -> viewWidthPx / videoAspect
            else -> viewHeightPx
        }
        val bottomBar = ((viewHeightPx - pictureHeight) / 2f).coerceAtLeast(0f)
        val paddingPx = bottomBar + pictureHeight * PICTURE_BOTTOM_SAFE_FRACTION
        return (paddingPx / viewHeightPx).coerceIn(0f, 0.9f)
    }

    /**
     * Same rule when the caller already knows the visible picture rectangle in window pixels.
     * The number of cue lines does not change the anchor: Media3 grows a multi-line cue upward from
     * this bottom-safe position, which keeps one-, two- and three-line cues consistently framed.
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

        val bottomBar = (subtitleViewBottomPx - pictureBottomPx).coerceAtLeast(0f)
        val paddingPx = bottomBar + pictureHeight * PICTURE_BOTTOM_SAFE_FRACTION
        return (paddingPx / subtitleViewHeightPx).coerceIn(0f, 0.9f)
    }
}
