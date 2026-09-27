package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class ManualNetworkSourcePolicyTest {
    @Test fun videoExtensionPolicyIsCaseInsensitive() {
        assertTrue(isNetworkVideoName("Movie.MKV"))
        assertTrue(isNetworkVideoName("clip.mp4"))
        assertFalse(isNetworkVideoName("subtitle.srt"))
        assertFalse(isNetworkVideoName("poster.jpg"))
    }

    @Test fun sftpPathNormalizationRejectsTraversal() {
        assertEquals("/movies/Film.mkv", normalizeSftpPath("//movies/./Film.mkv"))
        try {
            normalizeSftpPath("/movies/../secret/file.mkv")
            fail("Traversal should be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun sftpEndpointNeverContainsCredentials() {
        val endpoint = SftpEndpoint(
            host = "10.0.0.5",
            port = 22,
            rootPath = "/movies",
            hostKeySha256 = "SHA256:abcdefghijklmnopqrstuvwxyz",
        )
        assertEquals("sftp://10.0.0.5:22/movies", endpoint.redactedAddress)
        assertFalse(endpoint.redactedAddress.contains("@"))
    }

    @Test fun manualFactoryPreservesProtocolTypes() {
        val http = createManualNetworkSource(
            ManualNetworkSourceSpec.HttpDirectory("HTTP", "https://example.com/media/")
        )
        val m3u = createManualNetworkSource(
            ManualNetworkSourceSpec.M3u("Playlist", "https://example.com/list.m3u")
        )
        assertEquals(NetworkType.HTTP_DIRECTORY, http.type)
        assertEquals(NetworkType.M3U, m3u.type)
    }
}
