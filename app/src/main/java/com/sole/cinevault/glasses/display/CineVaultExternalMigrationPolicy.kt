package com.sole.cinevault.glasses.display

/**
 * D7-1 — migration plan from the legacy external player presentation to
 * One-CineVault shared presentation.
 *
 * This is intentionally a migration boundary, not another UI implementation.
 * Callers can move one responsibility at a time while the legacy presentation
 * remains available until its final live dependency is gone.
 */
object CineVaultExternalMigrationPolicy {

    fun resolve(
        sharedPresentationAvailable: Boolean,
        sharedInteractionAvailable: Boolean,
        sharedSessionContinuityAvailable: Boolean,
        legacyPresentationStillHasLiveCallers: Boolean,
    ): CineVaultExternalMigrationDecision {
        val sharedPathReady =
            sharedPresentationAvailable &&
                sharedInteractionAvailable &&
                sharedSessionContinuityAvailable

        return CineVaultExternalMigrationDecision(
            useSharedCineVaultPresentation = sharedPathReady,
            allowNewLegacyUiDependencies = false,
            keepLegacyPresentationTemporarily =
                legacyPresentationStillHasLiveCallers,
            legacyPresentationMayBeDeleted =
                sharedPathReady && !legacyPresentationStillHasLiveCallers,
        )
    }
}

data class CineVaultExternalMigrationDecision(
    val useSharedCineVaultPresentation: Boolean,
    val allowNewLegacyUiDependencies: Boolean,
    val keepLegacyPresentationTemporarily: Boolean,
    val legacyPresentationMayBeDeleted: Boolean,
)
