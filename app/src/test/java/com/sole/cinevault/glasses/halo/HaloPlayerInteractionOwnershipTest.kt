package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPlayerInteractionOwnershipTest {

    @Test fun canonicalTargetSurfaceOwnsPointerAndClickWhilePlaybackKeepsDrags() {
        val state = HaloPlayerInteractionOwnership.resolve(
            HaloPlayerInputBridge(
                externalDisplayActive = true,
                externalTargetSurfaceAvailable = true,
                transientUiVisible = false,
            )
        )

        assertTrue(state.canonicalPointer)
        assertTrue(state.canonicalClick)
        assertTrue(state.playbackDrags)
        assertTrue(state.backgroundTap)
    }

    @Test fun transientWindowSuppressesBackgroundTapButKeepsCanonicalTargeting() {
        val state = HaloPlayerInteractionOwnership.resolve(
            HaloPlayerInputBridge(
                externalDisplayActive = true,
                externalTargetSurfaceAvailable = true,
                transientUiVisible = true,
            )
        )

        assertTrue(state.canonicalPointer)
        assertTrue(state.canonicalClick)
        assertTrue(state.playbackDrags)
        assertFalse(state.backgroundTap)
    }

    @Test fun missingTargetSurfaceCannotCreateSecondPointerOwner() {
        val state = HaloPlayerInteractionOwnership.resolve(
            HaloPlayerInputBridge(
                externalDisplayActive = true,
                externalTargetSurfaceAvailable = false,
                transientUiVisible = true,
            )
        )

        assertFalse(state.canonicalPointer)
        assertFalse(state.canonicalClick)
        assertTrue(state.playbackDrags)
        assertTrue(state.backgroundTap)
    }

    @Test fun localPlaybackRemainsOutsideExternalHaloOwnership() {
        val state = HaloPlayerInteractionOwnership.resolve(
            HaloPlayerInputBridge(
                externalDisplayActive = false,
                externalTargetSurfaceAvailable = false,
                transientUiVisible = true,
            )
        )

        assertFalse(state.canonicalPointer)
        assertFalse(state.canonicalClick)
        assertFalse(state.playbackDrags)
        assertTrue(state.backgroundTap)
    }
}
