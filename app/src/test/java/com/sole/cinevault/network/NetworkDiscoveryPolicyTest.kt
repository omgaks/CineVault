package com.sole.cinevault.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NetworkDiscoveryPolicyTest {
    @Test fun discoveryDeduplicatesByOpaqueId() {
        val result = sanitizeDiscoveredDevices(
            listOf(
                DiscoveredNetworkDevice("device-a", "Living Room", NetworkDiscoveryKind.CINEVAULT),
                DiscoveredNetworkDevice("device-a", "Duplicate", NetworkDiscoveryKind.CINEVAULT),
            )
        )
        assertEquals(1, result.size)
        assertEquals("Living Room", result.single().displayName)
    }

    @Test fun discoveryRedactsUriUserInfo() {
        val result = sanitizeDiscoveredDevices(
            listOf(
                DiscoveredNetworkDevice(
                    "dav-a",
                    "WebDAV",
                    NetworkDiscoveryKind.WEBDAV,
                    "https://ash:secret@server.local/dav",
                )
            )
        )
        val hint = result.single().addressHint.orEmpty()
        assertEquals("https://server.local/dav", hint)
        assertFalse(hint.contains("ash"))
        assertFalse(hint.contains("secret"))
    }

    @Test fun invalidDiscoveryEntriesAreDropped() {
        val result = sanitizeDiscoveredDevices(
            listOf(
                DiscoveredNetworkDevice("", "Server", NetworkDiscoveryKind.UNKNOWN),
                DiscoveredNetworkDevice("ok", "   ", NetworkDiscoveryKind.UNKNOWN),
            )
        )
        assertEquals(0, result.size)
    }

    @Test fun discoveryNeverTreatsCredentialsAsSafeHints() {
        assertFalse(containsCredentialMaterial("https://server.local/media"))
        assertEquals(
            true,
            containsCredentialMaterial("https://user:pass@server.local/media"),
        )
    }
}
