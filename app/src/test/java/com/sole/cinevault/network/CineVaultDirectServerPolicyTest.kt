package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test
import com.google.gson.JsonParser
import java.io.File

class CineVaultDirectServerPolicyTest {
    @Test fun byteRangesSupportSeekingAndSuffixRequests() {
        assertEquals(HttpByteRange(100, 199), parseHttpRange("bytes=100-199", 1000))
        assertEquals(HttpByteRange(900, 999), parseHttpRange("bytes=-100", 1000))
        assertEquals(HttpByteRange(950, 999), parseHttpRange("bytes=950-", 1000))
        assertNull(parseHttpRange("bytes=1000-", 1000))
        assertNull(parseHttpRange("bytes=0-1,5-6", 1000))
    }

    @Test fun authorizationExpiresAndCanBeRevokedImmediately() {
        var now = 1000L
        val auth = CineVaultDirectAuthorization { now }
        val session = NearbyPairingSession("peer", "x".repeat(64), 2000L)
        auth.grant(session)
        assertNotNull(auth.authorize("Bearer ${session.sessionToken}"))
        auth.revoke(session.sessionToken)
        assertNull(auth.authorize("Bearer ${session.sessionToken}"))

        auth.grant(session)
        now = 2001L
        assertNull(auth.authorize("Bearer ${session.sessionToken}"))
    }

    @Test fun catalogueNeverContainsVaultMediaOrFilesystemPaths() {
        val normal = File.createTempFile("cinevault-normal", ".mkv").apply { writeBytes(ByteArray(16)) }
        val secret = File.createTempFile("cinevault-secret", ".mkv").apply { writeBytes(ByteArray(16)) }
        try {
            val catalogue = buildDirectCatalogue(
                listOf(
                    CineVaultDirectMedia("movie-1", "Movie", normal, "movies"),
                    CineVaultDirectMedia("secret-1", "Secret", secret, "vault", isVaultOrSecret = true),
                ),
                ShareLibrarySelection(SharedLibraryScope.ENTIRE_LIBRARY),
            )
            assertEquals(listOf("movie-1"), catalogue.items.map { it.id })
            val exposed = catalogue.items.joinToString()
            assertFalse(exposed.contains(normal.absolutePath))
            assertFalse(exposed.contains(secret.absolutePath))
        } finally {
            normal.delete()
            secret.delete()
        }
    }

    @Test fun selectedFoldersRemainEnforced() {
        val movie = File.createTempFile("cinevault-movie", ".mkv")
        val tv = File.createTempFile("cinevault-tv", ".mkv")
        try {
            val catalogue = buildDirectCatalogue(
                listOf(
                    CineVaultDirectMedia("m", "Movie", movie, "movies"),
                    CineVaultDirectMedia("t", "Episode", tv, "tv"),
                ),
                ShareLibrarySelection(SharedLibraryScope.SELECTED_FOLDERS, setOf("tv")),
            )
            assertEquals(listOf("t"), catalogue.items.map { it.id })
        } finally {
            movie.delete(); tv.delete()
        }
    }
    @Test fun approvalCanReturnOnOriginalPairingConnection() {
        val auth = CineVaultDirectAuthorization()
        val server = CineVaultDirectServer(
            authorization = auth,
            mediaProvider = { emptyList() },
            selectionProvider = { ShareLibrarySelection(SharedLibraryScope.ENTIRE_LIBRARY) },
        )
        val port = server.start()
        val request = NearbyPairingRequest("cv_test_peer", "Test peer", "0123456789abcdef")
        val result = java.util.concurrent.atomic.AtomicReference<String>()
        val worker = Thread {
            val connection = java.net.URL("http://127.0.0.1:$port/v1/pair/request")
                .openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = """{"remoteDeviceId":"${request.remoteDeviceId}","remoteDeviceName":"${request.remoteDeviceName}","inviteNonce":"${request.inviteNonce}"}"""
            connection.outputStream.use { it.write(body.toByteArray()) }
            result.set(connection.inputStream.bufferedReader().use { it.readText() })
            connection.disconnect()
        }
        try {
            worker.start()
            val deadline = System.currentTimeMillis() + 3_000L
            while (server.pendingRequests().isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20L)
            }
            assertEquals(request, server.pendingRequests().single())
            assertNotNull(server.approveDiscovered(request))
            worker.join(3_000L)
            assertFalse("pairing response should complete after approval", worker.isAlive)
            val raw = result.get().orEmpty()
            val json = JsonParser.parseString(raw).asJsonObject
            assertEquals("approved", json["state"].asString)
            assertTrue(json["sessionToken"].asString.length >= 32)
            assertTrue(json["expiresAtEpochMs"].asLong > System.currentTimeMillis())

            // Regression: the exact host response must be accepted by the
            // same parser used on the receiving Android device.
            val parsed = parsePairResponse(raw)
            assertEquals("approved", parsed.state)
            assertNotNull(parsed.sessionToken)
            assertNotNull(parsed.expiresAtEpochMs)
        } finally {
            server.stop()
            worker.join(1_000L)
        }
    }

}
