package com.sole.cinevault.glasses.display

import com.sole.cinevault.subtitles.SubtitlePositionPolicy

/**
 * Converts CineVault's user subtitle position into the currently visible
 * external movie frame. Both primary and merged dual subtitles use the same
 * Media3 SubtitleView, so this single correction applies to both.
 */
internal object ExternalSubtitleFramePolicy {

    fun bottomPaddingForVisibleFrame(
        requestedBottomPadding: Float,
        textSizeSp: Float,
        visibleFrame: ExternalVisibleFrame,
    ): Float {
        val safeRequested =
            SubtitlePositionPolicy.sanitize(
                bottomPadding = requestedBottomPadding,
                textSizeSp = textSizeSp,
            )

        if (visibleFrame.scale >= 1f) return safeRequested

        val inset = visibleFrame.verticalInsetFraction
        val visibleFraction = visibleFrame.scale.coerceAtLeast(0.01f)

        // SubtitleView still measures against the full PlayerView. Translate
        // the user's movie-frame-relative padding into full-view coordinates.
        val mapped = inset + safeRequested * visibleFraction

        val mappedMax =
            inset +
                SubtitlePositionPolicy.maxBottomPadding(textSizeSp) *
                    visibleFraction

        return mapped.coerceIn(inset, mappedMax)
    }
}
