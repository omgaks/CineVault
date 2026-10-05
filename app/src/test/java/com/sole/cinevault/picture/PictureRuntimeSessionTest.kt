package com.sole.cinevault.picture

import org.junit.Assert.*
import org.junit.Test

class PictureRuntimeSessionTest {

    @Test fun session_composesControllerFactsAndTelemetry() {
        val session = PictureRuntimeSession(
            selectedContent = PictureContent.AUTO,
            detectedContent = PictureContent.ANIME,
            requestedTier = PictureQualityTier.MAX,
            refreshRateHz = 60f,
        )
        session.onVideoFormat(1920, 1080, hdr = false, dolbyVision = false)

        assertEquals(PictureContent.ANIME, session.runtimeState.profile.content)
        assertEquals(PictureQualityTier.MAX, session.runtimeState.effectiveTier)

        session.recordPerformance(PicturePerformanceSample(gpuFrameMs = 18f))

        assertEquals(PicturePerformanceState.OVER_BUDGET, session.runtimeTelemetry.latestState)
        assertEquals(PictureQualityTier.BALANCED, session.runtimeState.effectiveTier)
    }

    @Test fun session_keepsHdrAsHardBypassRegardlessOfPerformance() {
        val session = PictureRuntimeSession(
            selectedContent = PictureContent.FILM,
            detectedContent = PictureContent.FILM,
            requestedTier = PictureQualityTier.MAX,
        )
        session.onVideoFormat(1920, 1080, hdr = true, dolbyVision = false)
        session.recordPerformance(PicturePerformanceSample(gpuFrameMs = 5f))

        assertTrue(session.runtimeState.bypassed)
        assertEquals("Not available for HDR / Dolby Vision video", session.runtimeState.bypassReason)
    }

    @Test fun resetForVideo_clearsTelemetryAndTransientPressure() {
        val session = PictureRuntimeSession(requestedTier = PictureQualityTier.MAX)
        session.recordPerformance(
            PicturePerformanceSample(gpuFrameMs = 40f, droppedFrames = 9)
        )
        assertEquals(1, session.runtimeTelemetry.sampleCount)
        assertEquals(PictureQualityTier.BALANCED, session.runtimeState.effectiveTier)

        session.resetForVideo(PictureContent.AUTO, PictureContent.ANIMATION)

        assertEquals(0, session.runtimeTelemetry.sampleCount)
        assertEquals(PictureContent.ANIMATION, session.runtimeState.profile.content)
        assertEquals(PictureQualityTier.MAX, session.runtimeState.effectiveTier)
    }

    @Test fun diagnostics_areReadOnlyProjectionOfRuntimeState() {
        val session = PictureRuntimeSession(
            selectedContent = PictureContent.ANIMATION,
            detectedContent = PictureContent.FILM,
            requestedTier = PictureQualityTier.BALANCED,
        )
        val diagnostics = session.runtimeState.toDiagnostics()

        assertEquals("ANIMATION", diagnostics.content)
        assertEquals("BALANCED", diagnostics.requestedTier)
        assertEquals("BALANCED", diagnostics.effectiveTier)
        assertEquals("Ready", diagnostics.state)
        assertTrue(diagnostics.activeStages.isNotEmpty())
    }
}
