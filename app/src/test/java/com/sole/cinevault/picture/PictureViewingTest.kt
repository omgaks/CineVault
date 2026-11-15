package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureViewingTest {
    @Test fun unknownSizesAreNeverRecommended() {
        val a = PictureViewing.assess(0, 0, 3200, 2136)
        assertEquals(PictureViewClass.UNKNOWN, a.viewClass)
        assertFalse(a.upscaleWorthIt)
    }

    @Test fun nativeMatchSaysUpscalingAddsNothing() {
        val a = PictureViewing.assess(1920, 1080, 1920, 1080)
        assertEquals(PictureViewClass.NATIVE, a.viewClass)
        assertFalse(a.upscaleWorthIt)
    }

    @Test fun smallerScreenIsDownscaled() {
        val a = PictureViewing.assess(3840, 2160, 1920, 1080)
        assertEquals(PictureViewClass.DOWNSCALED, a.viewClass)
        assertFalse(a.upscaleWorthIt)
    }

    @Test fun padSeventhOnFullHdIsModerate() {
        val a = PictureViewing.assess(1920, 1080, 3200, 2136, PictureContent.ANIME)
        assertEquals(PictureViewClass.MODERATE, a.viewClass)
        assertTrue(a.upscaleWorthIt)
    }

    @Test fun lowResOnBigScreenIsStrong() {
        val a = PictureViewing.assess(640, 360, 3200, 2136)
        assertEquals(PictureViewClass.STRONG, a.viewClass)
        assertTrue(a.upscaleWorthIt)
    }

    @Test fun fitRatioPreservesAspect() {
        assertEquals(2.0, PictureViewing.fitRatio(1280, 720, 2560, 2000), 1e-9)
    }

    @Test fun policyRefusesMarginalUpscale() {
        val plan = PictureAnime4kPolicy.plan(
            PictureAnime4kPolicy.Input(
                animeSelected = true, sourceWidth = 1920, sourceHeight = 1080,
                outputWidth = 2000, outputHeight = 1125,
                rendererSupportsOutputScaling = true, hdrSource = false,
                thermalLimited = false, droppedFramePressure = false,
            )
        )
        assertEquals(PictureAnime4kPolicy.Mode.ENHANCE_ONLY, plan.mode)
    }

    @Test fun probeReportsOnlyAfterRealFrames() {
        val p = PictureStageProbe()
        assertNull(p.summary())
        p.onConfigured(1920, 1080, 3200, 1800)
        assertNull(p.summary())
        p.onFrame()
        assertEquals("Upscale active · 1920×1080 → 3200×1800", p.summary())
        assertEquals(1L, p.frames)
    }
}
