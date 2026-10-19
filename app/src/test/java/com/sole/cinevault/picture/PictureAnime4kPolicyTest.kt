package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureAnime4kPolicyTest {
    private fun input() = PictureAnime4kPolicy.Input(
        animeSelected = true, sourceWidth = 1920, sourceHeight = 1080,
        outputWidth = 3840, outputHeight = 2160,
        rendererSupportsOutputScaling = true, hdrSource = false,
        thermalLimited = false, droppedFramePressure = false
    )

    @Test fun upscalesOnlyWithRealRendererCapability() {
        val plan = PictureAnime4kPolicy.plan(input())
        assertEquals(PictureAnime4kPolicy.Mode.UPSCALE, plan.mode)
        assertEquals(3840, plan.targetWidth)
        assertEquals(2160, plan.targetHeight)
    }

    @Test fun noRendererNeverClaimsUpscaling() {
        assertEquals(PictureAnime4kPolicy.Mode.ENHANCE_ONLY,
            PictureAnime4kPolicy.plan(input().copy(rendererSupportsOutputScaling = false)).mode)
    }

    @Test fun hdrAndPerformancePressureFallBackSafely() {
        for (sample in listOf(input().copy(hdrSource = true),
            input().copy(thermalLimited = true), input().copy(droppedFramePressure = true))) {
            assertEquals(PictureAnime4kPolicy.Mode.ENHANCE_ONLY,
                PictureAnime4kPolicy.plan(sample).mode)
        }
    }

    @Test fun smallerDisplayNeverClaims4k() {
        assertEquals(PictureAnime4kPolicy.Mode.ENHANCE_ONLY,
            PictureAnime4kPolicy.plan(input().copy(outputWidth = 1920, outputHeight = 1080)).mode)
    }
    @Test fun xiaomiPad7NativeResolutionUpscalesWithoutClaiming4k() {
        val plan = PictureAnime4kPolicy.plan(
            input().copy(outputWidth = 3200, outputHeight = 2136)
        )
        assertEquals(PictureAnime4kPolicy.Mode.UPSCALE, plan.mode)
        assertEquals(3200, plan.targetWidth)
        assertEquals(1800, plan.targetHeight)
    }

    @Test fun neverDownscalesWhenSurfaceSmallerThanSource() {
        val plan = PictureAnime4kPolicy.plan(
            input().copy(outputWidth = 1280, outputHeight = 720)
        )
        assertEquals(PictureAnime4kPolicy.Mode.ENHANCE_ONLY, plan.mode)
    }

}
