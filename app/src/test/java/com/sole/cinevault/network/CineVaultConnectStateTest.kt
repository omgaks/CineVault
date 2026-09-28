package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class CineVaultConnectStateTest {
    @Test fun stoppedSharingMapsToOff() {
        val state = CineVaultConnectState().withSharing(CineVaultSharingState())
        assertFalse(state.enabled)
        assertEquals(CineVaultConnectPhase.OFF, state.phase)
    }

    @Test fun runningSharingMapsToAvailable() {
        val state = CineVaultConnectState().withSharing(CineVaultSharingState(running = true))
        assertTrue(state.enabled)
        assertEquals(CineVaultConnectPhase.AVAILABLE, state.phase)
    }

    @Test fun pendingRequestTakesApprovalRequiredPriority() {
        val state = CineVaultConnectState().withSharing(
            CineVaultSharingState(
                running = true,
                pending = NearbyPairingRequest("peer", "Peer", "nonce"),
            )
        )
        assertEquals(CineVaultConnectPhase.APPROVAL_REQUIRED, state.phase)
    }

    @Test fun approvedSessionIsNotYetCalledConnected() {
        val state = CineVaultConnectState().withSharing(
            CineVaultSharingState(
                running = true,
                approved = NearbyPairingSession("peer", "token", 1234L),
            )
        )
        assertEquals(CineVaultConnectPhase.APPROVED, state.phase)
    }

    @Test fun receiverLifecycleIsNotOverwrittenByProviderRefresh() {
        val discovering = CineVaultConnectState(
            enabled = true,
            phase = CineVaultConnectPhase.DISCOVERING,
        )
        assertEquals(
            CineVaultConnectPhase.DISCOVERING,
            discovering.withSharing(CineVaultSharingState(running = true)).phase,
        )

        val requesting = discovering.copy(phase = CineVaultConnectPhase.REQUESTING_APPROVAL)
        assertEquals(
            CineVaultConnectPhase.REQUESTING_APPROVAL,
            requesting.withSharing(CineVaultSharingState(running = true)).phase,
        )
    }

    @Test fun connectedReceiverSurvivesSharingStateRefresh() {
        val device = DiscoveredNetworkDevice(
            id = "cv_peer123",
            displayName = "Peer",
            kind = NetworkDiscoveryKind.CINEVAULT,
            addressHint = "http://192.168.1.2:1234",
        )
        val connected = CineVaultConnectState(
            enabled = true,
            phase = CineVaultConnectPhase.CONNECTED,
            remoteConnection = CineVaultRemoteConnection(
                device,
                "http://192.168.1.2:1234",
                NearbyPairingSession("cv_peer123", "token", 1234L),
            ),
        )
        assertEquals(
            CineVaultConnectPhase.CONNECTED,
            connected.withSharing(CineVaultSharingState(running = true)).phase,
        )
    }
}
