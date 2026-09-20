package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * D1-17 regression guard for the tablet entry-point swap.
 *
 * MainActivity now enters through CineVaultTabletSessionHost; this test keeps
 * the architectural invariant testable without trying to unit-test an Activity
 * source file as text.
 */
class CineVaultTabletEntryPointContractTest {

    @Test
    fun tabletHostAndExternalHostUseCanonicalSession() {
        val tablet = CineVaultDisplaySessionEnvironment.appState()
        val external = CineVaultDisplaySessionEnvironment.appState()

        assertSame(tablet, external)
        assertEquals(
            CineVaultDisplaySessionEnvironment.sessionId,
            tablet.sessionId,
        )
    }
}
