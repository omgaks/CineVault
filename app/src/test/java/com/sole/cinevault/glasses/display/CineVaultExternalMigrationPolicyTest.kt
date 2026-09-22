package com.sole.cinevault.glasses.display

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultExternalMigrationPolicyTest {

    @Test fun readySharedPathBecomesPreferredPresentation() {
        val result = CineVaultExternalMigrationPolicy.resolve(
            sharedPresentationAvailable = true,
            sharedInteractionAvailable = true,
            sharedSessionContinuityAvailable = true,
            legacyPresentationStillHasLiveCallers = true,
        )
        assertTrue(result.useSharedCineVaultPresentation)
        assertTrue(result.keepLegacyPresentationTemporarily)
        assertFalse(result.legacyPresentationMayBeDeleted)
        assertFalse(result.allowNewLegacyUiDependencies)
    }

    @Test fun legacyCanBeDeletedOnlyAfterFinalCallerMigrates() {
        val result = CineVaultExternalMigrationPolicy.resolve(
            sharedPresentationAvailable = true,
            sharedInteractionAvailable = true,
            sharedSessionContinuityAvailable = true,
            legacyPresentationStillHasLiveCallers = false,
        )
        assertTrue(result.useSharedCineVaultPresentation)
        assertFalse(result.keepLegacyPresentationTemporarily)
        assertTrue(result.legacyPresentationMayBeDeleted)
    }

    @Test fun incompleteSharedPathCannotClaimMigrationComplete() {
        val result = CineVaultExternalMigrationPolicy.resolve(
            sharedPresentationAvailable = true,
            sharedInteractionAvailable = false,
            sharedSessionContinuityAvailable = true,
            legacyPresentationStillHasLiveCallers = true,
        )
        assertFalse(result.useSharedCineVaultPresentation)
        assertTrue(result.keepLegacyPresentationTemporarily)
        assertFalse(result.legacyPresentationMayBeDeleted)
    }

    @Test fun roadmapNeverAllowsNewLegacyUiDependencies() {
        val result = CineVaultExternalMigrationPolicy.resolve(
            sharedPresentationAvailable = false,
            sharedInteractionAvailable = false,
            sharedSessionContinuityAvailable = false,
            legacyPresentationStillHasLiveCallers = true,
        )
        assertFalse(result.allowNewLegacyUiDependencies)
    }
}
