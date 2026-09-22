package com.sole.cinevault.glasses.display

/**
 * D6-3 — generic interaction contract for the shared CineVault surface.
 *
 * There are no Sub Studio, Audio Studio, Tracks, FFmpeg, or other
 * glasses-specific mappings here. Halo interacts with the real rendered
 * CineVault surface through Android's normal target/input pipeline.
 */
object CineVaultSharedSurfaceInteractionPolicy {

    fun resolve(
        presentation: CineVaultAdaptivePresentationSpec,
        haloAvailable: Boolean,
        transientUiVisible: Boolean,
    ): CineVaultSharedSurfaceInteractionSpec {
        val sharedSurfaceAvailable =
            presentation.renderSharedCineVault &&
                presentation.sharesFeatureState &&
                !presentation.allowsGlassesSpecificFeatureTree

        val haloTargetsSharedSurface =
            sharedSurfaceAvailable && haloAvailable

        return CineVaultSharedSurfaceInteractionSpec(
            sharedSurfaceAvailable = sharedSurfaceAvailable,
            haloTargetsSharedSurface = haloTargetsSharedSurface,
            useAndroidHitTesting = haloTargetsSharedSurface,
            allowFeatureSpecificGlassesMappings = false,
            transientUiOwnsForegroundInteraction =
                haloTargetsSharedSurface && transientUiVisible,
            backgroundMayReceiveActivation =
                haloTargetsSharedSurface && !transientUiVisible,
        )
    }
}

data class CineVaultSharedSurfaceInteractionSpec(
    val sharedSurfaceAvailable: Boolean,
    val haloTargetsSharedSurface: Boolean,
    val useAndroidHitTesting: Boolean,
    val allowFeatureSpecificGlassesMappings: Boolean,
    val transientUiOwnsForegroundInteraction: Boolean,
    val backgroundMayReceiveActivation: Boolean,
)
