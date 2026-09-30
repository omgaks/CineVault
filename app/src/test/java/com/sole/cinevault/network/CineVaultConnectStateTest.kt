package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class CineVaultConnectStateTest {
    @Test fun stoppedSharingMapsToOff() {
        val state = CineVaultConnectState().withSharing(CineVaultSharingState())
        assertFalse(state.enabled)
        assertEquals(CineVaultConnectPhase.OFF, state.phase)
        assertEquals(CineVaultConnectRole.IDLE, state.role)
    }

    @Test fun runningSharingMapsToAvailableProvider() {
        val state = CineVaultConnectState().withSharing(CineVaultSharingState(running = true))
        assertTrue(state.enabled)
        assertTrue(state.isProviding)
        assertEquals(CineVaultConnectPhase.AVAILABLE, state.phase)
        assertEquals(CineVaultConnectRole.PROVIDER, state.role)
        assertEquals("Visible nearby", state.statusLabel)
    }

    @Test fun pendingRequestTakesApprovalRequiredPriority() {
        val state = CineVaultConnectState().withSharing(
            CineVaultSharingState(
                running = true,
                pending = NearbyPairingRequest("peer", "Peer", "nonce"),
            )
        )
        assertEquals(CineVaultConnectPhase.APPROVAL_REQUIRED, state.phase)
        assertTrue(state.requiresLocalApproval)
    }

    @Test fun approvedSessionIsNotYetCalledConnected() {
        val state = CineVaultConnectState().withSharing(
            CineVaultSharingState(
                running = true,
                approved = NearbyPairingSession("peer", "token", 1234L),
            )
        )
        assertEquals(CineVaultConnectPhase.APPROVED, state.phase)
        assertFalse(state.isConnected)
    }

    @Test fun receiverLifecycleIsNotOverwrittenByProviderRefresh() {
        val discovering = CineVaultConnectState(
            enabled = true,
            phase = CineVaultConnectPhase.DISCOVERING,
        )
        val both = discovering.withSharing(CineVaultSharingState(running = true))
        assertEquals(CineVaultConnectPhase.DISCOVERING, both.phase)
        assertEquals(CineVaultConnectRole.BOTH, both.role)

        val requesting = discovering.copy(phase = CineVaultConnectPhase.REQUESTING_APPROVAL)
        assertEquals(
            CineVaultConnectPhase.REQUESTING_APPROVAL,
            requesting.withSharing(CineVaultSharingState(running = true)).phase,
        )
    }

    @Test fun connectedReceiverSurvivesSharingStateRefresh() {
        val refreshed = connectedState().withSharing(CineVaultSharingState(running = true))
        assertEquals(CineVaultConnectPhase.CONNECTED, refreshed.phase)
        assertTrue(refreshed.isConnected)
        assertEquals(CineVaultConnectRole.BOTH, refreshed.role)
        assertEquals("Connected · Sharing", refreshed.statusLabel)
    }

    @Test fun receiverOnlyConnectionHasReceiverRole() {
        val connected = connectedState()
        assertEquals(CineVaultConnectRole.RECEIVER, connected.role)
        assertEquals("Connected", connected.statusLabel)
    }

    @Test fun errorDoesNotDisableActiveProvider() {
        val state = CineVaultConnectState(
            enabled = true,
            sharing = CineVaultSharingState(running = true),
        ).withError("network")
        assertTrue(state.enabled)
        assertEquals(CineVaultConnectPhase.ERROR, state.phase)
        assertEquals(CineVaultConnectRole.PROVIDER, state.role)
    }

    @Test fun dismissingErrorRestoresConnectedReceiver() {
        val restored = connectedState().copy(
            phase = CineVaultConnectPhase.ERROR,
            message = "temporary failure",
        ).afterMessageDismissed()
        assertEquals(CineVaultConnectPhase.CONNECTED, restored.phase)
        assertTrue(restored.enabled)
        assertNull(restored.message)
    }

    @Test fun dismissingErrorRestoresProviderAvailability() {
        val restored = CineVaultConnectState(
            enabled = true,
            phase = CineVaultConnectPhase.ERROR,
            sharing = CineVaultSharingState(running = true),
            message = "temporary failure",
        ).afterMessageDismissed()
        assertEquals(CineVaultConnectPhase.AVAILABLE, restored.phase)
        assertTrue(restored.enabled)
        assertEquals("Visible nearby", restored.statusLabel)
    }

    private fun connectedState(): CineVaultConnectState {
        val device = DiscoveredNetworkDevice(
            id = "cv_peer123",
            displayName = "Peer",
            kind = NetworkDiscoveryKind.CINEVAULT,
            addressHint = "http://192.168.1.2:1234",
        )
        return CineVaultConnectState(
            enabled = true,
            phase = CineVaultConnectPhase.CONNECTED,
            remoteConnection = CineVaultRemoteConnection(
                device,
                "http://192.168.1.2:1234",
                NearbyPairingSession("cv_peer123", "token", 1234L),
            ),
        )
    }
}
