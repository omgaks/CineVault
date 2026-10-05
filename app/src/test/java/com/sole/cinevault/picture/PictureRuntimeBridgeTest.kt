package com.sole.cinevault.picture

import org.junit.Assert.*
import org.junit.Test

class PictureRuntimeBridgeTest {

    @Test fun bridge_mapsExistingControllerFactsIntoArchitectureState() {
        val state = PictureRuntimeBridge.snapshot(
            PictureRuntimeInputs(
                selectedContent = PictureContent.AUTO,
                detectedContent = PictureContent.ANIMATION,
                requestedTier = PictureQualityTier.BALANCED,
                width = 1920,
                height = 1080,
            )
        )

        assertEquals(PictureContent.ANIMATION, state.profile.content)
        assertEquals(PictureQualityTier.BALANCED, state.effectiveTier)
        assertFalse(state.bypassed)
    }

    @Test fun bridge_preservesHdrSafetyGate() {
        val state = PictureRuntimeBridge.snapshot(
            PictureRuntimeInputs(
                selectedContent = PictureContent.FILM,
                detectedContent = PictureContent.FILM,
                hdr = true,
                width = 1920,
                height = 1080,
            )
        )

        assertTrue(state.bypassed)
        assertEquals("Not available for HDR / Dolby Vision video", state.bypassReason)
        assertTrue(state.enabledStages.isEmpty())
    }

    @Test fun bridge_preservesFourKSafetyGate() {
        val state = PictureRuntimeBridge.snapshot(
            PictureRuntimeInputs(
                selectedContent = PictureContent.ANIME,
                detectedContent = PictureContent.ANIME,
                width = 3840,
                height = 2160,
            )
        )

        assertTrue(state.bypassed)
        assertEquals("Not needed for 4K video", state.bypassReason)
    }

    @Test fun bridge_mapsThermalPressureWithoutChangingRequestedProfile() {
        val state = PictureRuntimeBridge.snapshot(
            PictureRuntimeInputs(
                selectedContent = PictureContent.FILM,
                detectedContent = PictureContent.FILM,
                requestedTier = PictureQualityTier.MAX,
                width = 1920,
                height = 1080,
                thermalLimited = true,
            )
        )

        assertEquals(PictureQualityTier.MAX, state.profile.qualityTier)
        assertEquals(PictureQualityTier.BALANCED, state.effectiveTier)
    }

    @Test fun initialState_isSafeAndRuntimeReady() {
        val state = initialPictureRuntimeState()

        assertFalse(state.bypassed)
        assertEquals(PictureContent.FILM, state.profile.content)
        assertEquals(PictureQualityTier.ECO, state.effectiveTier)
        assertTrue(state.enabledStages.isNotEmpty())
    }
}
