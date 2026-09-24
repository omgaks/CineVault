package com.sole.cinevault

import com.sole.cinevault.glasses.display.CineVaultRenderDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSubtitleAiRenderPolicyTest {

    @Test
    fun hostCinemaVoidSuppressesAiSubtitleSurfaces() {
        assertFalse(
            shouldRenderSubtitleAiSurfaces(
                isInPipMode = false,
                externalDisplayActive = true,
                renderDestination = CineVaultRenderDestination.HOST_DISPLAY,
            )
        )
    }

    @Test
    fun externalCanonicalRenderKeepsAiSubtitleSurfacesAvailable() {
        assertTrue(
            shouldRenderSubtitleAiSurfaces(
                isInPipMode = false,
                externalDisplayActive = true,
                renderDestination = CineVaultRenderDestination.EXTERNAL_DISPLAY,
            )
        )
    }

    @Test
    fun normalTabletPlaybackKeepsAiSubtitleSurfacesAvailable() {
        assertTrue(
            shouldRenderSubtitleAiSurfaces(
                isInPipMode = false,
                externalDisplayActive = false,
                renderDestination = CineVaultRenderDestination.HOST_DISPLAY,
            )
        )
    }

    @Test
    fun pipSuppressesAiSubtitleSurfacesEverywhere() {
        assertFalse(
            shouldRenderSubtitleAiSurfaces(
                isInPipMode = true,
                externalDisplayActive = true,
                renderDestination = CineVaultRenderDestination.EXTERNAL_DISPLAY,
            )
        )
    }
}
