package com.sole.cinevault.glasses.display

/**
 * D7-3 — migration readiness coordinator.
 *
 * D7-1 defines when the shared One-CineVault path is ready.
 * D7-2 isolates player interaction behind ExternalPlayerInteractionPort.
 * This coordinator converts those facts into one explicit migration phase.
 */
object CineVaultExternalMigrationCoordinator {
    fun resolve(
        sharedPresentationAvailable: Boolean,
        sharedInteractionPortAvailable: Boolean,
        sharedSessionContinuityAvailable: Boolean,
        legacyPresentationStillHasLiveCallers: Boolean,
    ): CineVaultExternalMigrationState {
        val decision = CineVaultExternalMigrationPolicy.resolve(
            sharedPresentationAvailable = sharedPresentationAvailable,
            sharedInteractionAvailable = sharedInteractionPortAvailable,
            sharedSessionContinuityAvailable = sharedSessionContinuityAvailable,
            legacyPresentationStillHasLiveCallers = legacyPresentationStillHasLiveCallers,
        )

        val phase = when {
            decision.legacyPresentationMayBeDeleted ->
                CineVaultExternalMigrationPhase.SHARED_ONLY
            decision.useSharedCineVaultPresentation &&
                decision.keepLegacyPresentationTemporarily ->
                CineVaultExternalMigrationPhase.SHARED_WITH_LEGACY_FALLBACK
            else -> CineVaultExternalMigrationPhase.LEGACY_BRIDGE
        }

        return CineVaultExternalMigrationState(
            phase = phase,
            useSharedPresentation = decision.useSharedCineVaultPresentation,
            keepLegacyBridge = decision.keepLegacyPresentationTemporarily,
            mayDeleteLegacyPresentation = decision.legacyPresentationMayBeDeleted,
            allowNewLegacyUiDependencies = decision.allowNewLegacyUiDependencies,
        )
    }
}

data class CineVaultExternalMigrationState(
    val phase: CineVaultExternalMigrationPhase,
    val useSharedPresentation: Boolean,
    val keepLegacyBridge: Boolean,
    val mayDeleteLegacyPresentation: Boolean,
    val allowNewLegacyUiDependencies: Boolean,
)

enum class CineVaultExternalMigrationPhase {
    LEGACY_BRIDGE,
    SHARED_WITH_LEGACY_FALLBACK,
    SHARED_ONLY,
}
