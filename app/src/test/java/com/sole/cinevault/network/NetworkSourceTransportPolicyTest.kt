package com.sole.cinevault.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSourceTransportPolicyTest {
    @Test fun httpAndWebDavIdsNeverContainCredentialsOrHosts() {
        val httpId = stableNetworkSourceId(
            NetworkType.HTTP_DIRECTORY, "https://media.example.local/library"
        )
        val davId = stableNetworkSourceId(
            NetworkType.WEBDAV, "https://dav.example.local/media"
        )
        assertFalse(httpId.contains("media.example"))
        assertFalse(davId.contains("dav.example"))
        assertTrue(httpId.startsWith("http_directory-"))
        assertTrue(davId.startsWith("webdav-"))
    }

    @Test fun credentialBearingHttpUrlsRemainRejected() {
        assertFalse(isSafeHttpUrl("https://ash:secret@server.local/media"))
        assertFalse(isSafeHttpUrl("http://user@server.local/media"))
        assertTrue(isSafeHttpUrl("https://server.local/media"))
    }

    @Test fun safeFailureDoesNotExposeUnderlyingNetworkDetails() {
        val failure = safeNetworkFailure("Home Library")
        assertEquals(
            "Couldn't connect to Home Library. Check the network and sign-in details.",
            failure.userMessage,
        )
        assertFalse(failure.userMessage.contains("password", ignoreCase = true))
        assertFalse(failure.userMessage.contains("token", ignoreCase = true))
    }
}
