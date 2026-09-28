package com.sole.cinevault.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultConnectStateTest {

    @Test
    fun stoppedSharingMapsToOff() {
        val state = CineVaultConnectState().withSharing(CineVaultSharingState())
        assertFalse(state.enabled)
        assertEquals(CineVaultConnectPhase.OFF, state.phase)
    }

    @Test
    fun runningSharingMapsToAvailable() {
        val sharing = CineVaultSharingState(running = true)
        val state = CineVaultConnectState().withSharing(sharing)
        assertTrue(state.enabled)
        assertEquals(CineVaultConnectPhase.AVAILABLE, state.phase)
    }

    @Test
    fun pendingRequestTakesApprovalRequiredPriority() {
        val sharing = CineVaultSharingState(
            running = true,
            pending = NearbyPairingRequest("peer", "Peer", "nonce"),
        )
        val state = CineVaultConnectState().withSharing(sharing)
        assertEquals(CineVaultConnectPhase.APPROVAL_REQUIRED, state.phase)
    }

    @Test
    fun approvedSessionIsNotYetCalledConnected() {
        val sharing = CineVaultSharingState(
            running = true,
            approved = NearbyPairingSession(
                remoteDeviceId = "peer",
                sessionToken = "token",
                expiresAtEpochMs = 1234L,
            ),
        )
        val state = CineVaultConnectState().withSharing(sharing)
        assertEquals(CineVaultConnectPhase.APPROVED, state.phase)
    }
}
