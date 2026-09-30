package com.sole.cinevault.network

enum class CineVaultConnectRole { IDLE, PROVIDER, RECEIVER, BOTH }

enum class CineVaultConnectPhase {
    OFF, AVAILABLE, DISCOVERING, REQUESTING_APPROVAL,
    APPROVAL_REQUIRED, APPROVED, CONNECTED, ERROR,
}

data class CineVaultRemoteConnection(
    val device: DiscoveredNetworkDevice,
    val endpoint: String,
    val session: NearbyPairingSession,
)

data class CineVaultConnectState(
    val enabled: Boolean = false,
    val phase: CineVaultConnectPhase = CineVaultConnectPhase.OFF,
    val sharing: CineVaultSharingState = CineVaultSharingState(),
    val discoveredPeers: List<DiscoveredNetworkDevice> = emptyList(),
    val connectingPeer: DiscoveredNetworkDevice? = null,
    val remoteConnection: CineVaultRemoteConnection? = null,
    val message: String? = null,
) {
    val isProviding get() = sharing.running
    val isReceiving get() = remoteConnection != null ||
        phase == CineVaultConnectPhase.DISCOVERING ||
        phase == CineVaultConnectPhase.REQUESTING_APPROVAL
    val role get() = when {
        isProviding && isReceiving -> CineVaultConnectRole.BOTH
        isProviding -> CineVaultConnectRole.PROVIDER
        isReceiving -> CineVaultConnectRole.RECEIVER
        else -> CineVaultConnectRole.IDLE
    }
    val isBusy get() = phase == CineVaultConnectPhase.DISCOVERING ||
        phase == CineVaultConnectPhase.REQUESTING_APPROVAL
    val requiresLocalApproval get() = sharing.pending != null
    val isConnected get() = remoteConnection != null
    val statusLabel get() = when {
        remoteConnection != null && sharing.running -> "Connected · Sharing"
        remoteConnection != null -> "Connected"
        sharing.pending != null -> "Approval needed"
        phase == CineVaultConnectPhase.REQUESTING_APPROVAL -> "Waiting for approval"
        phase == CineVaultConnectPhase.DISCOVERING -> "Finding CineVault devices"
        sharing.approved != null -> "Access approved"
        sharing.running -> "Visible nearby"
        phase == CineVaultConnectPhase.ERROR -> "Connection issue"
        else -> "Connect"
    }
}

internal fun CineVaultConnectState.withSharing(next: CineVaultSharingState): CineVaultConnectState {
    val nextPhase = when {
        next.pending != null -> CineVaultConnectPhase.APPROVAL_REQUIRED
        remoteConnection != null -> CineVaultConnectPhase.CONNECTED
        phase == CineVaultConnectPhase.DISCOVERING ||
            phase == CineVaultConnectPhase.REQUESTING_APPROVAL ||
            phase == CineVaultConnectPhase.ERROR -> phase
        next.approved != null -> CineVaultConnectPhase.APPROVED
        next.running -> CineVaultConnectPhase.AVAILABLE
        else -> CineVaultConnectPhase.OFF
    }
    return copy(
        enabled = next.running || remoteConnection != null ||
            phase == CineVaultConnectPhase.DISCOVERING ||
            phase == CineVaultConnectPhase.REQUESTING_APPROVAL,
        phase = nextPhase,
        sharing = next,
    )
}

internal fun CineVaultConnectState.withError(message: String): CineVaultConnectState =
    copy(
        enabled = sharing.running || remoteConnection != null,
        phase = CineVaultConnectPhase.ERROR,
        connectingPeer = null,
        message = message,
    )

internal fun CineVaultConnectState.afterMessageDismissed(): CineVaultConnectState {
    val restoredPhase = when {
        remoteConnection != null -> CineVaultConnectPhase.CONNECTED
        sharing.pending != null -> CineVaultConnectPhase.APPROVAL_REQUIRED
        sharing.approved != null -> CineVaultConnectPhase.APPROVED
        sharing.running -> CineVaultConnectPhase.AVAILABLE
        else -> CineVaultConnectPhase.OFF
    }
    return copy(
        enabled = sharing.running || remoteConnection != null,
        phase = restoredPhase,
        message = null,
    )
}
