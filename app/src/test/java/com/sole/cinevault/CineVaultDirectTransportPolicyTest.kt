package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class CineVaultDirectTransportPolicyTest {
    @Test
    fun protocolBuildsPairApprovalLibraryAndMediaRoutes() {
        val base = "http://192.168.1.20:49152"
        assertEquals("$base/v1/pair/request", CineVaultLanProtocol.pairRequestUrl(base))
        assertEquals("$base/v1/pair/approve", CineVaultLanProtocol.pairApproveUrl(base).toString())
        assertEquals("$base/v1/library", CineVaultLanProtocol.libraryUrl(base))
        assertEquals("$base/v1/media/movie%201", CineVaultLanProtocol.resolve(base, "/v1/media/movie%201"))
    }

    @Test
    fun directSourceUsesGatewayTypeAndOpaqueId() {
        val session = NearbyPairingSession(
            remoteDeviceId = "host_12345678",
            sessionToken = "a".repeat(64),
            expiresAtEpochMs = Long.MAX_VALUE,
        )
        val source = CineVaultDirectNetworkSource(
            displayName = "Living Room CineVault",
            endpoint = "http://10.0.0.5:48111",
            session = session,
        )
        assertEquals(NetworkType.CINEVAULT_GATEWAY, source.type)
        assertFalse(source.id.contains("10.0.0.5"))
        assertFalse(source.id.contains(session.sessionToken))
    }

    @Test
    fun pairingResponseDoesNotNeedFilesystemOrCredentialFields() {
        val response = CineVaultPairResponseEnvelope(
            state = "approved",
            sessionToken = "b".repeat(64),
            expiresAtEpochMs = 1234L,
        )
        assertEquals("approved", response.state)
        assertNull(response.message)
    }
}
