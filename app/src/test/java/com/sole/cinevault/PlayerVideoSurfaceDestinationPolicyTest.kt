package com.sole.cinevault

import com.sole.cinevault.glasses.display.CineVaultRenderDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerVideoSurfaceDestinationPolicyTest {

    @Test
    fun hostBecomesCinemaVoidWhileExternalDisplayIsActive() {
        assertTrue(
            shouldUseCinemaVoidPlayerSurface(
                externalDisplayActive = true,
                renderDestination = CineVaultRenderDestination.HOST_DISPLAY,
            )
        )
    }

    @Test
    fun externalDestinationKeepsCanonicalPlayerSurfaceVisible() {
        assertFalse(
            shouldUseCinemaVoidPlayerSurface(
                externalDisplayActive = true,
                renderDestination = CineVaultRenderDestination.EXTERNAL_DISPLAY,
            )
        )
    }

    @Test
    fun normalHostPlaybackKeepsCanonicalPlayerSurfaceVisible() {
        assertFalse(
            shouldUseCinemaVoidPlayerSurface(
                externalDisplayActive = false,
                renderDestination = CineVaultRenderDestination.HOST_DISPLAY,
            )
        )
    }
}
