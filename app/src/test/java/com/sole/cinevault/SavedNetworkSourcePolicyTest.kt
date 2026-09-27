package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class SavedNetworkSourcePolicyTest {
    @Test fun sourceJsonNeverContainsCredentials() {
        val source = SavedNetworkSource(
            id = stableNetworkSourceId(NetworkType.WEBDAV, "https://nas.local/media"),
            displayName = "Home NAS",
            type = NetworkType.WEBDAV,
            address = "https://nas.local/media",
        )
        val raw = source.toJson().toString()
        assertFalse(raw.contains("password"))
        assertFalse(raw.contains("secret"))
        assertFalse(containsCredentialMaterial(source.address))
    }

    @Test(expected = IllegalArgumentException::class)
    fun embeddedCredentialsAreRejected() {
        SavedNetworkSource(
            id = stableNetworkSourceId(NetworkType.WEBDAV, "https://nas.local"),
            displayName = "Bad",
            type = NetworkType.WEBDAV,
            address = "https://user:pass@nas.local/media",
        )
    }

    @Test fun friendlyAddressNormalizationAvoidsProtocolTyping() {
        assertEquals("http://nas.local/videos", normalizedManualAddress(NetworkType.HTTP_DIRECTORY, "nas.local/videos"))
        assertEquals("sftp://nas.local", normalizedManualAddress(NetworkType.SFTP, "nas.local"))
    }

    @Test fun automaticNamesRemainHumanReadable() {
        assertEquals("WebDAV • nas.local", suggestedSourceName(NetworkType.WEBDAV, "https://nas.local/media"))
        assertEquals("Playlist • tv.local", suggestedSourceName(NetworkType.M3U, "https://tv.local/list.m3u"))
    }

    @Test fun jsonRoundTripPreservesOnlyNonSecretConfig() {
        val original = SavedNetworkSource(
            id = stableNetworkSourceId(NetworkType.SFTP, "sftp://nas.local"),
            displayName = "Vault NAS",
            type = NetworkType.SFTP,
            address = "sftp://nas.local",
            rootPath = "/movies",
            port = 22,
            hostKeySha256 = "SHA256:abcdefghijklmnop",
        )
        assertEquals(original, SavedNetworkSource.fromJson(original.toJson()))
    }
}
