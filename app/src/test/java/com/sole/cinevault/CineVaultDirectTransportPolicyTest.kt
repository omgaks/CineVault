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
    fun nearbyTransportIntentionallyUsesLanHttpEndpoint() {
        val endpoint = CineVaultLanEndpoint(
            deviceId = "device_12345678",
            deviceName = "Living Room CineVault",
            host = "192.168.8.76",
            port = 49152,
        )

        assertEquals("http://192.168.8.76:49152", endpoint.baseUrl())
        assertEquals(
            "http://192.168.8.76:49152/v1/pair/request",
            CineVaultLanProtocol.pairRequestUrl(endpoint.baseUrl()),
        )
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
    @Test
    fun pairingResponseParserRejectsNullOrIncompleteApprovedPayloads() {
        val missingState = parsePairResponse("""{"state":null}""")
        assertEquals("error", missingState.state)

        val incompleteApproved = parsePairResponse("""{"state":"approved","sessionToken":null}""")
        assertEquals("error", incompleteApproved.state)
        assertNotNull(incompleteApproved.message)
    }

    @Test
    fun pairingResponseParserAcceptsCompleteApprovedPayload() {
        val token = "c".repeat(64)
        val parsed = parsePairResponse(
            """{"state":"approved","sessionToken":"$token","expiresAtEpochMs":9999999999999}"""
        )
        assertEquals("approved", parsed.state)
        assertEquals(token, parsed.sessionToken)
        assertEquals(9999999999999L, parsed.expiresAtEpochMs)
    }


    @Test
    fun approvalPollingKeepsWaitingAfterEmptyOrTruncatedLanResponse() {
        assertEquals("pending", parseApprovalPollResponse("").state)
        assertEquals("pending", parseApprovalPollResponse("{").state)
        assertEquals("pending", parseApprovalPollResponse("""{"unexpected":"payload"}""").state)
    }

    @Test
    fun approvalPollingDoesNotHideValidServerErrors() {
        val parsed = parseApprovalPollResponse(
            """{"state":"error","message":"Pairing request expired."}"""
        )
        assertEquals("error", parsed.state)
        assertEquals("Pairing request expired.", parsed.message)
    }



    @Test
    fun directCatalogueParserReadsRealWireShapeWithoutReflectionCasting() {
        val parsed = parseDirectCatalogue(
            """{"protocolVersion":1,"items":[{"id":"movie-1","title":"Movie One","sizeBytes":12345,"mimeType":"video/mp4","streamPath":"/v1/media/movie-1","subtitlePaths":["/v1/media/movie-1/subtitle/0"],"artworkPath":"/v1/media/movie-1/artwork"}]}"""
        )
        assertEquals(1, parsed.items.size)
        assertEquals("movie-1", parsed.items.single().id)
        assertEquals("Movie One", parsed.items.single().title)
        assertEquals(12345L, parsed.items.single().sizeBytes)
        assertEquals("/v1/media/movie-1", parsed.items.single().streamPath)
    }

    @Test
    fun directCatalogueParserAcceptsEmptyLibrary() {
        val parsed = parseDirectCatalogue("""{"protocolVersion":1,"items":[]}""")
        assertTrue(parsed.items.isEmpty())
    }

    @Test
    fun directCatalogueParserRejectsWrongTopLevelShapeWithFriendlyProtocolError() {
        val failure = runCatching { parseDirectCatalogue("[]") }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message.orEmpty().contains("incompatible library response"))
    }

    @Test
    fun directCatalogueParserSkipsMalformedItemsInsteadOfClassCasting() {
        val parsed = parseDirectCatalogue(
            """{"protocolVersion":1,"items":[{"unexpected":true},{"id":"ok","title":"Playable","streamPath":"/v1/media/ok"}]}"""
        )
        assertEquals(listOf("ok"), parsed.items.map { it.id })
    }

}
