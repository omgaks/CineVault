package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class CineVaultNearbyClosurePolicyTest {
    @Test
    fun discoveryPairingCreatesOpaqueCorrelationNonceWithoutQr() {
        val request = createNearbyPairRequest(
            localDeviceId = "cv_12345678",
            localDeviceName = "CineVault Tablet",
            qr = null,
        )
        assertNotNull(request)
        assertEquals("cv_12345678", request!!.remoteDeviceId)
        assertTrue(request.inviteNonce.length >= 16)
        assertFalse(request.inviteNonce.contains("192.168"))
    }

    @Test
    fun qrPairingPreservesHostInviteNonce() {
        val qr = CineVaultQrPayload(
            deviceId = "cv_host123",
            deviceName = "Host",
            endpoint = "http://192.168.1.8:40000",
            nonce = "abcdefghijklmnop1234",
            expiresAtEpochMs = Long.MAX_VALUE,
        )
        val request = createNearbyPairRequest("cv_guest123", "Guest", qr)
        assertEquals(qr.nonce, request!!.inviteNonce)
    }

    @Test
    fun selectedFolderPolicyRemainsFailClosedForSecretMedia() {
        val allowed = filterShareableLibrary(
            listOf(
                ShareableLibraryItem("a", "/Movies/A.mkv", "/Movies", false),
                ShareableLibraryItem("b", "/Vault/B.mkv", "/Vault", true),
            ),
            ShareLibrarySelection(SharedLibraryScope.SELECTED_FOLDERS, setOf("/Movies", "/Vault")),
        )
        assertEquals(listOf("a"), allowed.map { it.id })
    }

    @Test
    fun emptySelectedFolderChoiceNeverFallsBackToEntireLibrary() {
        val selection = shareSelectionForFolders(emptySet())
        assertEquals(SharedLibraryScope.SELECTED_FOLDERS, selection.scope)
        assertTrue(selection.folderIds.isEmpty())
    }

    @Test
    fun selectedFolderChoicePreservesExactFolders() {
        val selection = shareSelectionForFolders(setOf("/Movies", "/Series"))
        assertEquals(SharedLibraryScope.SELECTED_FOLDERS, selection.scope)
        assertEquals(setOf("/Movies", "/Series"), selection.folderIds)
    }
}
