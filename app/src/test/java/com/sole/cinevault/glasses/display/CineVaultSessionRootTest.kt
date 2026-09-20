package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class CineVaultSessionRootTest {

    @Test
    fun environmentReturnsSameBridgeForCanonicalSession() {
        val first = CineVaultDisplaySessionEnvironment.appState()
        val second = CineVaultDisplaySessionEnvironment.appState()

        assertSame(first, second)
        assertEquals(
            CineVaultDisplaySessionEnvironment.sessionId,
            first.sessionId,
        )
    }

    @Test
    fun resetReleasesCanonicalSessionBridge() {
        val first = CineVaultDisplaySessionEnvironment.appState()

        CineVaultDisplaySessionEnvironment.reset()

        val second = CineVaultDisplaySessionEnvironment.appState()
        assertNotSame(first, second)
        assertEquals(first.sessionId, second.sessionId)
    }
}
