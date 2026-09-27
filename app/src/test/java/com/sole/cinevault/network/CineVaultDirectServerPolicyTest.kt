package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test
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
}
