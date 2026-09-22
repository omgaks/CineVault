package com.sole.cinevault.glasses.display

import com.sole.cinevault.glasses.CinemaVoidAdaptivePolicy
import com.sole.cinevault.glasses.CinemaVoidWindowProfile

/**
 * D6-2 — adaptive presentation sizing for One-CineVault display mode.
 *
 * The same CineVault UI is presented against the actual available window.
 * This policy never selects a device model or a glasses-specific UI tree.
 */
object CineVaultAdaptivePresentationPolicy {

    fun resolve(
        presentation: CineVaultPresentationDecision,
        availableWidthDp: Int,
        availableHeightDp: Int,
    ): CineVaultAdaptivePresentationSpec {
        val width = availableWidthDp.coerceAtLeast(1)
        val height = availableHeightDp.coerceAtLeast(1)
        val adaptive = CinemaVoidAdaptivePolicy.resolve(width, height)

        return CineVaultAdaptivePresentationSpec(
            availableWidthDp = width,
            availableHeightDp = height,
            windowProfile = adaptive.profile,
            renderSharedCineVault =
                presentation.external == CineVaultPresentationRole.SHARED_CINEVAULT ||
                    presentation.host == CineVaultPresentationRole.SHARED_CINEVAULT,
            renderCinemaVoidController =
                presentation.host == CineVaultPresentationRole.CINEMA_VOID_CONTROLLER,
            sharesFeatureState = presentation.sharesFeatureState,
            allowsGlassesSpecificFeatureTree =
                presentation.allowsGlassesSpecificFeatureTree,
        )
    }
}

data class CineVaultAdaptivePresentationSpec(
    val availableWidthDp: Int,
    val availableHeightDp: Int,
    val windowProfile: CinemaVoidWindowProfile,
    val renderSharedCineVault: Boolean,
    val renderCinemaVoidController: Boolean,
    val sharesFeatureState: Boolean,
    val allowsGlassesSpecificFeatureTree: Boolean,
)
