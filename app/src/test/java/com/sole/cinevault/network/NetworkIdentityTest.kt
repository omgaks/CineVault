package com.sole.cinevault.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkIdentityTest {

    @Test
    fun stableIdIsDeterministicAndContainsNoHostOrCredentialMaterial() {
        val first = stableNetworkSourceId(NetworkType.SMB, "NAS.local", "Movies")
        val second = stableNetworkSourceId(NetworkType.SMB, "nas.LOCAL", "/movies/")

        assertEquals(first, second)
        assertTrue(first.startsWith("smb-"))
        assertFalse(first.contains("nas", ignoreCase = true))
        assertFalse(first.contains("movies", ignoreCase = true))
    }

    @Test
    fun differentResourcesProduceDifferentIds() {
        assertNotEquals(
            stableNetworkSourceId(NetworkType.SMB, "nas", "Movies"),
            stableNetworkSourceId(NetworkType.SMB, "nas", "TV"),
        )
    }

    @Test
    fun redactionRemovesUriUserInfo() {
        assertEquals(
            "webdav://server.local/media",
            redactNetworkAddress("webdav://ash:secret@server.local/media"),
        )
        assertEquals(
            "sftp://10.0.0.5/movies",
            redactNetworkAddress("sftp://user@10.0.0.5/movies"),
        )
    }

    @Test
    fun redactionLeavesNormalAddressUntouched() {
        assertEquals(
            "https://server.local/media",
            redactNetworkAddress("https://server.local/media"),
        )
    }
}
