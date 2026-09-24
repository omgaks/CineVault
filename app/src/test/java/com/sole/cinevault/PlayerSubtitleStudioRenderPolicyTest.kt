package com.sole.cinevault

import com.sole.cinevault.glasses.display.CineVaultRenderDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSubtitleStudioRenderPolicyTest {

    @Test
    fun hostTabletIsSuppressedWhileExternalDisplayIsActive() {
        assertFalse(
            shouldRenderSubtitleStudio(
                isInPipMode = false,
                externalDisplayActive = true,
                renderDestination = CineVaultRenderDestination.HOST_DISPLAY,
            )
        )
    }

    @Test
    fun externalCanonicalRenderKeepsSubtitleStudioAvailable() {
        assertTrue(
            shouldRenderSubtitleStudio(
                isInPipMode = false,
                externalDisplayActive = true,
                renderDestination = CineVaultRenderDestination.EXTERNAL_DISPLAY,
            )
        )
    }

    @Test
    fun normalTabletPlaybackKeepsSubtitleStudioAvailable() {
        assertTrue(
            shouldRenderSubtitleStudio(
                isInPipMode = false,
                externalDisplayActive = false,
                renderDestination = CineVaultRenderDestination.HOST_DISPLAY,
            )
        )
    }

    @Test
    fun pipSuppressesSubtitleStudioOnEveryDestination() {
        assertFalse(
            shouldRenderSubtitleStudio(
                isInPipMode = true,
                externalDisplayActive = true,
                renderDestination = CineVaultRenderDestination.EXTERNAL_DISPLAY,
            )
        )
    }
}
