package com.sole.cinevault.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CineVaultReconnectStoreTest {
    @Test
    fun rememberedPeerBecomesUntrustedDiscoveryHint() {
        val remembered = RememberedCineVaultPeer(
            id = "cv_peer123",
            displayName = "Living Room CineVault",
            endpoint = "http://192.168.1.20:4040",
            rememberedAtEpochMs = 1234L,
        )

        val device = remembered.asDiscoveredDevice()

        assertEquals("cv_peer123", device.id)
        assertEquals("Living Room CineVault", device.displayName)
        assertEquals(NetworkDiscoveryKind.CINEVAULT, device.kind)
        assertEquals("http://192.168.1.20:4040", device.addressHint)
    }

    @Test
    fun rememberedPeerContainsNoSessionCredentialFields() {
        val fields = RememberedCineVaultPeer::class.java.declaredFields.map { it.name }.toSet()

        assertNull(fields.firstOrNull { it.contains("token", ignoreCase = true) })
        assertNull(fields.firstOrNull { it.contains("nonce", ignoreCase = true) })
        assertNull(fields.firstOrNull { it.contains("session", ignoreCase = true) })
    }
}
