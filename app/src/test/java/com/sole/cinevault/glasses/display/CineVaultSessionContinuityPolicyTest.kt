package com.sole.cinevault.glasses.display

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultSessionContinuityPolicyTest {
    private val local = CineVaultPresentationDecision(
        CineVaultPresentationRole.SHARED_CINEVAULT,
        CineVaultPresentationRole.NONE, true, false,
    )
    private val external = CineVaultPresentationDecision(
        CineVaultPresentationRole.CINEMA_VOID_CONTROLLER,
        CineVaultPresentationRole.SHARED_CINEVAULT, true, false,
    )

    @Test fun localToExternalPreservesOneLiveSession() =
        assertPreserved(local, external)

    @Test fun externalToLocalPreservesOneLiveSession() =
        assertPreserved(external, local)

    @Test fun unavailableSessionDoesNotInventReplacementState() {
        val r = CineVaultSessionContinuityPolicy.resolve(local, external, false)
        assertFalse(r.preserveExistingSession)
        assertFalse(r.transferPresentationOnly)
        assertNoRecreation(r)
    }

    @Test fun duplicateFeatureTreeCannotQualifyForContinuity() {
        val invalid = external.copy(allowsGlassesSpecificFeatureTree = true)
        val r = CineVaultSessionContinuityPolicy.resolve(local, invalid, true)
        assertFalse(r.preserveExistingSession)
        assertFalse(r.transferPresentationOnly)
        assertNoRecreation(r)
    }

    private fun assertPreserved(from: CineVaultPresentationDecision, to: CineVaultPresentationDecision) {
        val r = CineVaultSessionContinuityPolicy.resolve(from, to, true)
        assertTrue(r.preserveExistingSession)
        assertTrue(r.transferPresentationOnly)
        assertNoRecreation(r)
    }

    private fun assertNoRecreation(r: CineVaultSessionContinuityDecision) {
        assertFalse(r.recreatePlaybackEngine)
        assertFalse(r.recreateFeatureState)
        assertFalse(r.recreateNavigationState)
        assertFalse(r.recreateSubtitleState)
        assertFalse(r.recreateAudioState)
    }
}
