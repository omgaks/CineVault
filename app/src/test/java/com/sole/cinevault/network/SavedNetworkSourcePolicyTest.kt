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
        // org.json is an Android platform class; local JVM tests use the
        // unimplemented android.jar stub, so policy tests must not execute it.
        assertFalse(source.address.contains("@"))
        assertFalse(source.address.contains("password", ignoreCase = true))
        assertFalse(source.address.contains("secret", ignoreCase = true))
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

    @Test fun savedSftpConfigContainsNoSecretMaterial() {
        val source = SavedNetworkSource(
            id = stableNetworkSourceId(NetworkType.SFTP, "sftp://nas.local"),
            displayName = "Vault NAS",
            type = NetworkType.SFTP,
            address = "sftp://nas.local",
            rootPath = "/movies",
            port = 22,
            hostKeySha256 = "SHA256:abcdefghijklmnop",
        )
        assertEquals(NetworkType.SFTP, source.type)
        assertEquals("/movies", source.rootPath)
        assertEquals(22, source.port)
        assertFalse(containsCredentialMaterial(source.address))
    }
}
