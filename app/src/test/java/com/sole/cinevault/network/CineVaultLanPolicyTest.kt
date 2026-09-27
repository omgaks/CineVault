package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class CineVaultLanPolicyTest {
    @Test fun safeDeviceIdsAreOpaqueAndCredentialFree() {
        assertTrue(isSafeLanDeviceId("device_0123456789"))
        assertFalse(isSafeLanDeviceId("http://user:pass@host"))
        assertFalse(isSafeLanDeviceId("tiny"))
    }

    @Test fun fallbackDeviceIdIsStableAndOpaque() {
        val a = stableLanDeviceId("Living Room", "192.168.1.20", 49152)
        val b = stableLanDeviceId("Living Room", "192.168.1.20", 49152)
        assertEquals(a, b)
        assertEquals(32, a.length)
        assertFalse(a.contains("192.168"))
    }

    @Test fun ipv6HostsAreBracketedForHttpUrls() {
        assertEquals("[fe80::1234]", formatHostForUrl("fe80::1234"))
        assertEquals("192.168.1.20", formatHostForUrl("192.168.1.20"))
    }

    @Test fun serviceNamesCannotInjectControlCharacters() {
        assertEquals("Ash CineVault", sanitizeServiceName(" Ash\nCineVault "))
    }

    @Test fun protocolBuildsPairingAndHealthUrlsWithoutCredentials() {
        assertEquals(
            "http://192.168.1.20:49152/v1/pair/request",
            CineVaultLanProtocol.pairRequestUrl("http://192.168.1.20:49152")
        )
        assertEquals(
            "http://192.168.1.20:49152/v1/health",
            CineVaultLanProtocol.healthUrl("http://192.168.1.20:49152")
        )
    }

    @Test fun endpointFingerprintDoesNotExposeAddress() {
        val endpoint = CineVaultLanEndpoint("device_12345678", "Tablet", "10.0.0.8", 49152)
        val fingerprint = CineVaultLanProtocol.fingerprint(endpoint)
        assertFalse(fingerprint.contains("10.0.0.8"))
        assertTrue(fingerprint.contains(":"))
    }
}
