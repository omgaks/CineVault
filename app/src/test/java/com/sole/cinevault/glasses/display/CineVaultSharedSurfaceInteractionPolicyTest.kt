package com.sole.cinevault.glasses.display

import com.sole.cinevault.glasses.CinemaVoidWindowProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultSharedSurfaceInteractionPolicyTest {

    private fun sharedPresentation() =
        CineVaultAdaptivePresentationSpec(
            availableWidthDp = 900,
            availableHeightDp = 600,
            windowProfile = CinemaVoidWindowProfile.EXPANDED,
            renderSharedCineVault = true,
            renderCinemaVoidController = true,
            sharesFeatureState = true,
            allowsGlassesSpecificFeatureTree = false,
        )

    @Test fun haloTargetsRealSharedCineVaultSurfaceGenerically() {
        val spec =
            CineVaultSharedSurfaceInteractionPolicy.resolve(
                presentation = sharedPresentation(),
                haloAvailable = true,
                transientUiVisible = false,
            )

        assertTrue(spec.sharedSurfaceAvailable)
        assertTrue(spec.haloTargetsSharedSurface)
        assertTrue(spec.useAndroidHitTesting)
        assertFalse(spec.allowFeatureSpecificGlassesMappings)
    }

    @Test fun transientUiOwnsForegroundInteractionWithoutFeatureMapping() {
        val spec =
            CineVaultSharedSurfaceInteractionPolicy.resolve(
                presentation = sharedPresentation(),
                haloAvailable = true,
                transientUiVisible = true,
            )

        assertTrue(spec.transientUiOwnsForegroundInteraction)
        assertFalse(spec.backgroundMayReceiveActivation)
        assertFalse(spec.allowFeatureSpecificGlassesMappings)
    }

    @Test fun noTransientUiAllowsNormalSharedSurfaceBackgroundActivation() {
        val spec =
            CineVaultSharedSurfaceInteractionPolicy.resolve(
                presentation = sharedPresentation(),
                haloAvailable = true,
                transientUiVisible = false,
            )

        assertFalse(spec.transientUiOwnsForegroundInteraction)
        assertTrue(spec.backgroundMayReceiveActivation)
    }

    @Test fun unavailableHaloDoesNotCreateAlternativeInteractionTree() {
        val spec =
            CineVaultSharedSurfaceInteractionPolicy.resolve(
                presentation = sharedPresentation(),
                haloAvailable = false,
                transientUiVisible = true,
            )

        assertTrue(spec.sharedSurfaceAvailable)
        assertFalse(spec.haloTargetsSharedSurface)
        assertFalse(spec.useAndroidHitTesting)
        assertFalse(spec.allowFeatureSpecificGlassesMappings)
        assertFalse(spec.transientUiOwnsForegroundInteraction)
    }

    @Test fun duplicateFeatureTreeIsNeverAcceptedAsSharedSurface() {
        val invalid =
            sharedPresentation().copy(
                allowsGlassesSpecificFeatureTree = true,
            )

        val spec =
            CineVaultSharedSurfaceInteractionPolicy.resolve(
                presentation = invalid,
                haloAvailable = true,
                transientUiVisible = false,
            )

        assertFalse(spec.sharedSurfaceAvailable)
        assertFalse(spec.haloTargetsSharedSurface)
        assertFalse(spec.allowFeatureSpecificGlassesMappings)
    }
}
