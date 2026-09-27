package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class MediaServerSessionPolicyTest {
    @Test fun jellyfinAndEmbyMapToTheirOwnNetworkTypes() {
        assertEquals(NetworkType.JELLYFIN, mediaServerType(MediaServerKind.JELLYFIN))
        assertEquals(NetworkType.EMBY, mediaServerType(MediaServerKind.EMBY))
    }

    @Test fun sessionTokenUsesCredentialEnvelopeInsteadOfSourceJson() {
        val session = MediaServerSession(
            serverUrl = "http://192.168.1.2:8096",
            userId = "user-1",
            accessToken = "very-secret-token",
        )
        val credential = mediaServerCredential(session)
        assertEquals("user-1", credential.username)
        assertEquals("very-secret-token", credential.secret)

        val source = SavedNetworkSource(
            id = stableNetworkSourceId(NetworkType.JELLYFIN, session.serverUrl),
            displayName = "Living Room Jellyfin",
            type = NetworkType.JELLYFIN,
            address = session.serverUrl,
        )
        assertFalse(source.toJson().toString().contains("very-secret-token"))
        assertFalse(source.toJson().toString().contains("user-1"))
    }

    @Test fun encryptedCredentialCanRebuildMediaSession() {
        val source = SavedNetworkSource(
            id = stableNetworkSourceId(NetworkType.EMBY, "http://nas.local:8096"),
            displayName = "Emby",
            type = NetworkType.EMBY,
            address = "http://nas.local:8096",
        )
        val restored = mediaServerSession(source, NetworkCredential("user-7", "token-7"))
        assertNotNull(restored)
        assertEquals(source.address, restored!!.serverUrl)
        assertEquals("user-7", restored.userId)
        assertEquals("token-7", restored.accessToken)
    }

    @Test fun blankOrWrongSourceCredentialsDoNotCreateSession() {
        val web = SavedNetworkSource(
            id = stableNetworkSourceId(NetworkType.HTTP_DIRECTORY, "https://example.test"),
            displayName = "Web",
            type = NetworkType.HTTP_DIRECTORY,
            address = "https://example.test",
        )
        assertNull(mediaServerSession(web, NetworkCredential("u", "t")))
    }
}
