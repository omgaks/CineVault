package com.sole.cinevault.glasses.display

/**
 * D1-3: shared surface routing for One-CineVault Display Mode.
 *
 * This is deliberately UI-agnostic. It tells the app which CineVault surface
 * belongs on each physical display; it does NOT create a second navigation
 * stack, duplicate player state, or replace the existing glasses Presentation.
 *
 * Later slices can render the existing CineVault content against this routing
 * contract while the tablet becomes Cinema Void.
 */
enum class CineVaultDisplayTarget {
    HOST,
    EXTERNAL,
}

data class CineVaultDisplaySurface(
    val target: CineVaultDisplayTarget,
    val role: CineVaultSurfaceRole,
    val displayId: Int?,
    val displayName: String?,
)

data class CineVaultDisplaySurfaces(
    val host: CineVaultDisplaySurface,
    val external: CineVaultDisplaySurface?,
) {
    val externalCineVaultActive: Boolean
        get() = external?.role == CineVaultSurfaceRole.CINEVAULT_EXTERNAL_DISPLAY
}

/**
 * Pure routing function so display ownership can be regression-tested without
 * Android Presentation/Activity objects.
 */
fun GlassesDisplayModeState.toDisplaySurfaces(): CineVaultDisplaySurfaces {
    val host = CineVaultDisplaySurface(
        target = CineVaultDisplayTarget.HOST,
        role = hostRole,
        displayId = null,
        displayName = null,
    )

    val external = if (active) {
        CineVaultDisplaySurface(
            target = CineVaultDisplayTarget.EXTERNAL,
            role = CineVaultSurfaceRole.CINEVAULT_EXTERNAL_DISPLAY,
            displayId = externalDisplayId,
            displayName = externalDisplayName,
        )
    } else {
        null
    }

    return CineVaultDisplaySurfaces(
        host = host,
        external = external,
    )
}

/**
 * Guard used by future rendering code. Keeping this decision here prevents
 * individual screens/player features from inventing their own "glass mode".
 */
fun CineVaultDisplaySurface.rendersFullCineVault(): Boolean =
    role == CineVaultSurfaceRole.NORMAL_APP ||
        role == CineVaultSurfaceRole.CINEVAULT_EXTERNAL_DISPLAY

/**
 * The host becomes a controller/void surface only while the unified external
 * CineVault surface is active.
 */
fun CineVaultDisplaySurface.rendersCinemaVoidController(): Boolean =
    role == CineVaultSurfaceRole.CINEMA_VOID_CONTROLLER
