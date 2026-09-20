package com.sole.cinevault.glasses.display

/**
 * One-CineVault display-mode contract.
 *
 * Glasses mode is NOT a second CineVault player or a second navigation stack.
 * The external display renders the same CineVault experience/state, while the
 * host tablet becomes the Cinema Void controller surface.
 */
enum class CineVaultSurfaceRole {
    NORMAL_APP,
    CINEMA_VOID_CONTROLLER,
    CINEVAULT_EXTERNAL_DISPLAY,
}

data class GlassesDisplayModeState(
    val externalDisplayConnected: Boolean,
    val externalDisplayId: Int? = null,
    val externalDisplayName: String? = null,
    val displayModeEnabled: Boolean = true,
) {
    val active: Boolean
        get() = displayModeEnabled &&
            externalDisplayConnected &&
            externalDisplayId != null

    val hostRole: CineVaultSurfaceRole
        get() = if (active) {
            CineVaultSurfaceRole.CINEMA_VOID_CONTROLLER
        } else {
            CineVaultSurfaceRole.NORMAL_APP
        }

    val externalRole: CineVaultSurfaceRole?
        get() = if (active) {
            CineVaultSurfaceRole.CINEVAULT_EXTERNAL_DISPLAY
        } else {
            null
        }
}

/**
 * Pure mapping kept outside Compose/Presentation code so connection lifecycle
 * can be tested without creating a second player or second feature state.
 */
fun resolveGlassesDisplayMode(
    connected: Boolean,
    displayId: Int?,
    displayName: String?,
    enabled: Boolean = true,
): GlassesDisplayModeState = GlassesDisplayModeState(
    externalDisplayConnected = connected,
    externalDisplayId = displayId,
    externalDisplayName = displayName,
    displayModeEnabled = enabled,
)
