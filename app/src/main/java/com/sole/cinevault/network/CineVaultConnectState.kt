package com.sole.cinevault.network

/**
 * App-level state for CineVault Connect.
 *
 * CONNECT-1 deliberately keeps transport details out of the UI.  Screens
 * observe this state; they do not own the server/discovery lifetime.
 */
enum class CineVaultConnectPhase {
    OFF,
    AVAILABLE,
    APPROVAL_REQUIRED,
    APPROVED,
    CONNECTED,
    ERROR,
}

data class CineVaultConnectState(
    val enabled: Boolean = false,
    val phase: CineVaultConnectPhase = CineVaultConnectPhase.OFF,
    val sharing: CineVaultSharingState = CineVaultSharingState(),
    val message: String? = null,
)

internal fun CineVaultConnectState.withSharing(
    next: CineVaultSharingState,
): CineVaultConnectState {
    val phase = when {
        !next.running -> CineVaultConnectPhase.OFF
        next.pending != null -> CineVaultConnectPhase.APPROVAL_REQUIRED
        next.approved != null -> CineVaultConnectPhase.APPROVED
        else -> CineVaultConnectPhase.AVAILABLE
    }
    return copy(
        enabled = next.running,
        phase = phase,
        sharing = next,
        message = null,
    )
}

internal fun CineVaultConnectState.withError(message: String): CineVaultConnectState =
    copy(phase = CineVaultConnectPhase.ERROR, message = message)
