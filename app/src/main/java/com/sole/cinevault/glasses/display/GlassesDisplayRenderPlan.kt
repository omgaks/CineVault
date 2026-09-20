package com.sole.cinevault.glasses.display

/**
 * D1-4: converts D1-3 physical display routing into a rendering plan.
 *
 * The key architectural rule is encoded here:
 * EXTERNAL_CINEVAULT means render CineVault itself on the external display.
 * It does NOT mean launch a glasses-specific player, subtitle panel, or
 * navigation implementation.
 */
enum class CineVaultRenderContent {
    FULL_CINEVAULT,
    CINEMA_VOID,
}

data class CineVaultRenderSlot(
    val target: CineVaultDisplayTarget,
    val content: CineVaultRenderContent,
    val displayId: Int?,
    val displayName: String?,
)

data class CineVaultDisplayRenderPlan(
    val host: CineVaultRenderSlot,
    val external: CineVaultRenderSlot?,
) {
    val usesExternalCineVault: Boolean
        get() = external?.content == CineVaultRenderContent.FULL_CINEVAULT

    val usesCinemaVoid: Boolean
        get() = host.content == CineVaultRenderContent.CINEMA_VOID
}

/**
 * Pure render-plan resolver. Android/Compose ownership stays out of this layer
 * so MainActivity/Presentation integration can be introduced separately and
 * green-gated.
 */
fun CineVaultDisplaySurfaces.toRenderPlan(): CineVaultDisplayRenderPlan {
    val hostContent = if (host.rendersCinemaVoidController()) {
        CineVaultRenderContent.CINEMA_VOID
    } else {
        CineVaultRenderContent.FULL_CINEVAULT
    }

    val hostSlot = CineVaultRenderSlot(
        target = CineVaultDisplayTarget.HOST,
        content = hostContent,
        displayId = null,
        displayName = null,
    )

    val externalSlot = external
        ?.takeIf { it.rendersFullCineVault() }
        ?.let { surface ->
            CineVaultRenderSlot(
                target = CineVaultDisplayTarget.EXTERNAL,
                content = CineVaultRenderContent.FULL_CINEVAULT,
                displayId = surface.displayId,
                displayName = surface.displayName,
            )
        }

    return CineVaultDisplayRenderPlan(
        host = hostSlot,
        external = externalSlot,
    )
}

/**
 * Convenience chain for the future integration point.
 */
fun GlassesDisplayModeState.toRenderPlan(): CineVaultDisplayRenderPlan =
    toDisplaySurfaces().toRenderPlan()
