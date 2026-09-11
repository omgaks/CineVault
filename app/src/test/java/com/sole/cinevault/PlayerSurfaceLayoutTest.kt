package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSurfaceLayoutTest {

    @Test
    fun wideVideoFillsContainerWidth() {
        val width = calculateVisibleMovieWidthDp(
            containerWidthDp = 800f,
            containerHeightDp = 450f,
            videoWidth = 1920,
            videoHeight = 1080,
            pixelWidthHeightRatio = 1f,
        )

        assertEquals(800f, width, 0.001f)
    }

    @Test
    fun narrowVideoIsPillarboxed() {
        val width = calculateVisibleMovieWidthDp(
            containerWidthDp = 800f,
            containerHeightDp = 450f,
            videoWidth = 1440,
            videoHeight = 1080,
            pixelWidthHeightRatio = 1f,
        )

        assertEquals(600f, width, 0.001f)
    }

    @Test
    fun invalidVideoDimensionsFallBackToContainerWidth() {
        val width = calculateVisibleMovieWidthDp(
            containerWidthDp = 800f,
            containerHeightDp = 450f,
            videoWidth = 0,
            videoHeight = 0,
            pixelWidthHeightRatio = 1f,
        )

        assertEquals(800f, width, 0.001f)
    }

    @Test
    fun nonPositivePixelRatioFallsBackToSquarePixels() {
        val width = calculateVisibleMovieWidthDp(
            containerWidthDp = 800f,
            containerHeightDp = 450f,
            videoWidth = 1440,
            videoHeight = 1080,
            pixelWidthHeightRatio = 0f,
        )

        assertEquals(600f, width, 0.001f)
    }
}
