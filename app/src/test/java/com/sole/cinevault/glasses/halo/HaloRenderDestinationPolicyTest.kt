package com.sole.cinevault.glasses.halo

import com.sole.cinevault.glasses.display.CineVaultRenderDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloRenderDestinationPolicyTest {
    @Test fun hostDisplayNeverOwnsHalo() {
        assertFalse(
            HaloRenderDestinationPolicy.shouldOwnHalo(
                CineVaultRenderDestination.HOST_DISPLAY
            )
        )
    }

    @Test fun externalDisplayOwnsHalo() {
        assertTrue(
            HaloRenderDestinationPolicy.shouldOwnHalo(
                CineVaultRenderDestination.EXTERNAL_DISPLAY
            )
        )
    }
}
