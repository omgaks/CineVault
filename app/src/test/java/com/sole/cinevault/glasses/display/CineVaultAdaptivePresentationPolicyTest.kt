package com.sole.cinevault.glasses.display

import com.sole.cinevault.glasses.CinemaVoidWindowProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultAdaptivePresentationPolicyTest {

    private val externalPresentation =
        CineVaultPresentationDecision(
            host = CineVaultPresentationRole.CINEMA_VOID_CONTROLLER,
            external = CineVaultPresentationRole.SHARED_CINEVAULT,
            sharesFeatureState = true,
            allowsGlassesSpecificFeatureTree = false,
        )

    @Test fun compactWindowUsesCompactProfileWithoutChangingFeatureTree() {
        val spec =
            CineVaultAdaptivePresentationPolicy.resolve(
                presentation = externalPresentation,
                availableWidthDp = 420,
                availableHeightDp = 900,
            )

        assertEquals(CinemaVoidWindowProfile.COMPACT, spec.windowProfile)
        assertTrue(spec.renderSharedCineVault)
        assertTrue(spec.renderCinemaVoidController)
        assertTrue(spec.sharesFeatureState)
        assertFalse(spec.allowsGlassesSpecificFeatureTree)
    }

    @Test fun resizedWindowReclassifiesFromAvailableWidthNotDeviceIdentity() {
        val medium =
            CineVaultAdaptivePresentationPolicy.resolve(
                externalPresentation,
                availableWidthDp = 700,
                availableHeightDp = 500,
            )
        val expanded =
            CineVaultAdaptivePresentationPolicy.resolve(
                externalPresentation,
                availableWidthDp = 1000,
                availableHeightDp = 700,
            )

        assertEquals(CinemaVoidWindowProfile.MEDIUM, medium.windowProfile)
        assertEquals(CinemaVoidWindowProfile.EXPANDED, expanded.windowProfile)
    }

    @Test fun invalidWindowDimensionsAreSafelyClamped() {
        val spec =
            CineVaultAdaptivePresentationPolicy.resolve(
                externalPresentation,
                availableWidthDp = 0,
                availableHeightDp = -20,
            )

        assertEquals(1, spec.availableWidthDp)
        assertEquals(1, spec.availableHeightDp)
        assertEquals(CinemaVoidWindowProfile.COMPACT, spec.windowProfile)
    }

    @Test fun localPresentationStillUsesSameAdaptiveContract() {
        val local =
            CineVaultPresentationDecision(
                host = CineVaultPresentationRole.SHARED_CINEVAULT,
                external = CineVaultPresentationRole.NONE,
                sharesFeatureState = true,
                allowsGlassesSpecificFeatureTree = false,
            )

        val spec =
            CineVaultAdaptivePresentationPolicy.resolve(
                local,
                availableWidthDp = 900,
                availableHeightDp = 600,
            )

        assertEquals(CinemaVoidWindowProfile.EXPANDED, spec.windowProfile)
        assertTrue(spec.renderSharedCineVault)
        assertFalse(spec.renderCinemaVoidController)
        assertFalse(spec.allowsGlassesSpecificFeatureTree)
    }
}
