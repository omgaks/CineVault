package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalSubtitleFramePolicyTest {

    @Test
    fun fullViewportKeepsCanonicalSubtitlePosition() {
        val result =
            ExternalSubtitleFramePolicy.bottomPaddingForVisibleFrame(
                requestedBottomPadding = 0.10f,
                textSizeSp = 20f,
                visibleFrame = ExternalVisibleFrame(scale = 1f),
            )

        assertEquals(0.10f, result, 0.0001f)
    }

    @Test
    fun reducedViewportMapsSubtitleInsideVisibleMovieFrame() {
        val result =
            ExternalSubtitleFramePolicy.bottomPaddingForVisibleFrame(
                requestedBottomPadding = 0.10f,
                textSizeSp = 20f,
                visibleFrame = ExternalVisibleFrame(scale = 0.90f),
            )

        // 5% lower inset + 10% of the 90% visible movie frame.
        assertEquals(0.14f, result, 0.0001f)
    }

    @Test
    fun reducedViewportStillHonoursCanonicalSafeCeiling() {
        val result =
            ExternalSubtitleFramePolicy.bottomPaddingForVisibleFrame(
                requestedBottomPadding = 1f,
                textSizeSp = 32f,
                visibleFrame = ExternalVisibleFrame(scale = 0.90f),
            )

        assertEquals(0.482f, result, 0.0001f)
    }

    @Test
    fun enlargedViewportDoesNotInventExtraSubtitleInset() {
        val result =
            ExternalSubtitleFramePolicy.bottomPaddingForVisibleFrame(
                requestedBottomPadding = 0.10f,
                textSizeSp = 20f,
                visibleFrame = ExternalVisibleFrame(scale = 1.5f),
            )

        assertEquals(0.10f, result, 0.0001f)
    }
}
