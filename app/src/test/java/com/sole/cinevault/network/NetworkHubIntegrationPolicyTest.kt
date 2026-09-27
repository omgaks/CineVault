package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class NetworkHubIntegrationPolicyTest {
    @Test fun onlySafeDiscoveryKindsCanPrefillManualSetup() {
        assertTrue(discoveryCanPrefillManualSetup(NetworkDiscoveryKind.WEBDAV))
        assertFalse(discoveryCanPrefillManualSetup(NetworkDiscoveryKind.UNKNOWN))
        assertFalse(discoveryCanPrefillManualSetup(NetworkDiscoveryKind.CINEVAULT))
        assertFalse(discoveryCanPrefillManualSetup(NetworkDiscoveryKind.SMB))
    }

    @Test fun discoveryTypeMappingIsExplicit() {
        assertEquals(NetworkType.WEBDAV, suggestedTypeForDiscovery(NetworkDiscoveryKind.WEBDAV))
        assertEquals(NetworkType.SMB, suggestedTypeForDiscovery(NetworkDiscoveryKind.SMB))
        assertEquals(NetworkType.JELLYFIN, suggestedTypeForDiscovery(NetworkDiscoveryKind.JELLYFIN))
        assertEquals(NetworkType.EMBY, suggestedTypeForDiscovery(NetworkDiscoveryKind.EMBY))
        assertEquals(NetworkType.DLNA, suggestedTypeForDiscovery(NetworkDiscoveryKind.DLNA))
        assertEquals(NetworkType.CINEVAULT_GATEWAY, suggestedTypeForDiscovery(NetworkDiscoveryKind.CINEVAULT))
        assertNull(suggestedTypeForDiscovery(NetworkDiscoveryKind.UNKNOWN))
    }
}
