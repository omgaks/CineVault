package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class NetworkHubClosurePolicyTest {
    @Test fun dlnaDisplayNameNeverLeaksLocation() {
        val device = DlnaDevice(
            usn = "uuid:test",
            location = "http://192.168.1.2:8200/root.xml",
            server = "MiniDLNA/1.3 UPnP/1.1",
        )
        assertEquals("MiniDLNA/1.3", dlnaDisplayName(device))
        assertFalse(dlnaDisplayName(device).contains("192.168"))
    }

    @Test fun discoveredDlnaIdsUseOpaqueNetworkIds() {
        val id = stableNetworkSourceId(NetworkType.DLNA, "http://192.168.1.2/root.xml")
        assertTrue(isSafeNetworkSourceId(id))
        assertFalse(id.contains("192.168"))
    }

    @Test fun discoverySanitizerRedactsCredentialBearingHints() {
        val sanitized = sanitizeDiscoveredDevices(
            listOf(
                DiscoveredNetworkDevice(
                    id = "device-123",
                    displayName = " Server ",
                    kind = NetworkDiscoveryKind.DLNA,
                    addressHint = "http://user:pass@192.168.1.2/root.xml",
                )
            )
        )
        assertEquals("Server", sanitized.single().displayName)
        assertEquals("http://192.168.1.2/root.xml", sanitized.single().addressHint)
        assertFalse(sanitized.single().addressHint.orEmpty().contains("user:pass"))
        assertFalse(containsCredentialMaterial(sanitized.single().addressHint.orEmpty()))
    }
}
