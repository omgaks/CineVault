package com.sole.cinevault.network

enum class CineVaultConnectPhase {
    OFF,
    AVAILABLE,
    DISCOVERING,
    REQUESTING_APPROVAL,
    APPROVAL_REQUIRED,
    APPROVED,
    CONNECTED,
    ERROR,
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
)

internal fun CineVaultConnectState.withSharing(next: CineVaultSharingState): CineVaultConnectState {
    val nextPhase = when {
        next.pending != null -> CineVaultConnectPhase.APPROVAL_REQUIRED
        phase in setOf(
            CineVaultConnectPhase.DISCOVERING,
            CineVaultConnectPhase.REQUESTING_APPROVAL,
            CineVaultConnectPhase.CONNECTED,
            CineVaultConnectPhase.ERROR,
        ) -> phase
        next.approved != null -> CineVaultConnectPhase.APPROVED
        next.running -> CineVaultConnectPhase.AVAILABLE
        else -> CineVaultConnectPhase.OFF
    }
    return copy(enabled = next.running || enabled, phase = nextPhase, sharing = next)
}

internal fun CineVaultConnectState.withError(message: String): CineVaultConnectState =
    copy(phase = CineVaultConnectPhase.ERROR, connectingPeer = null, message = message)
