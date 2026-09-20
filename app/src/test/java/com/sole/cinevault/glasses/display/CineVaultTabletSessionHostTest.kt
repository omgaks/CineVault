package com.sole.cinevault.glasses.display

import org.junit.Assert.assertSame
import org.junit.Test

class CineVaultTabletSessionHostTest {

    @Test
    fun tabletAndExternalDisplayResolveSameCanonicalSessionState() {
        val tabletState = CineVaultDisplaySessionEnvironment.appState()
        val externalState = CineVaultDisplaySessionEnvironment.appState()

        assertSame(tabletState, externalState)
    }
}
