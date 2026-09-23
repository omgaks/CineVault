package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultExternalMigrationCoordinatorTest {
    @Test fun incompleteSharedPathKeepsLegacyBridge() {
        val state = CineVaultExternalMigrationCoordinator.resolve(true, true, false, true)
        assertEquals(CineVaultExternalMigrationPhase.LEGACY_BRIDGE, state.phase)
        assertFalse(state.useSharedPresentation)
        assertTrue(state.keepLegacyBridge)
        assertFalse(state.mayDeleteLegacyPresentation)
        assertFalse(state.allowNewLegacyUiDependencies)
    }

    @Test fun readySharedPathBecomesPrimaryWhileLegacyCallersRemain() {
        val state = CineVaultExternalMigrationCoordinator.resolve(true, true, true, true)
        assertEquals(CineVaultExternalMigrationPhase.SHARED_WITH_LEGACY_FALLBACK, state.phase)
        assertTrue(state.useSharedPresentation)
        assertTrue(state.keepLegacyBridge)
        assertFalse(state.mayDeleteLegacyPresentation)
    }

    @Test fun readySharedPathWithoutLegacyCallersAllowsDeletion() {
        val state = CineVaultExternalMigrationCoordinator.resolve(true, true, true, false)
        assertEquals(CineVaultExternalMigrationPhase.SHARED_ONLY, state.phase)
        assertTrue(state.useSharedPresentation)
        assertFalse(state.keepLegacyBridge)
        assertTrue(state.mayDeleteLegacyPresentation)
    }

    @Test fun missingInteractionPortPreventsSharedCutover() {
        val state = CineVaultExternalMigrationCoordinator.resolve(true, false, true, false)
        assertEquals(CineVaultExternalMigrationPhase.LEGACY_BRIDGE, state.phase)
        assertFalse(state.useSharedPresentation)
        assertFalse(state.mayDeleteLegacyPresentation)
    }
}
