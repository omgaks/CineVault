package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class ShareLibraryPolicyTest {
    private val items = listOf(
        ShareableLibraryItem("a", "/movies/a.mkv", "movies", false),
        ShareableLibraryItem("b", "/tv/b.mkv", "tv", false),
        ShareableLibraryItem("secret", "/vault/secret.mkv", "vault", true),
    )

    @Test fun entireLibraryStillExcludesVaultContent() {
        val result = filterShareableLibrary(
            items,
            ShareLibrarySelection(SharedLibraryScope.ENTIRE_LIBRARY),
        )
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test fun selectedFoldersShareOnlyApprovedFolders() {
        val result = filterShareableLibrary(
            items,
            ShareLibrarySelection(
                SharedLibraryScope.SELECTED_FOLDERS,
                folderIds = setOf("tv"),
            ),
        )
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test fun sessionAuthorizationRequiresSameDeviceAndUnexpiredToken() {
        val session = NearbyPairingSession("phone-b", "x".repeat(64), 2_000L)
        assertTrue(isSessionAuthorized(session, "phone-b", 1_500L))
        assertFalse(isSessionAuthorized(session, "phone-c", 1_500L))
        assertFalse(isSessionAuthorized(session, "phone-b", 2_001L))
    }
}
