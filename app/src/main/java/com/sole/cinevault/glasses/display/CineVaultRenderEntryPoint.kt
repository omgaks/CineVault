package com.sole.cinevault.glasses.display

/**
 * D1-9: identity contract for the real CineVault render entry point.
 *
 * MainActivity currently enters the app through CineVaultRoot(). The external
 * display must ultimately enter that SAME CineVault tree/state architecture,
 * never a glasses-only player/navigation tree.
 *
 * This file intentionally contains no Compose code yet. D1-10 can bind the
 * actual @Composable root after the ownership contract is green.
 */
enum class CineVaultRenderEntryPoint {
    CINEVAULT_ROOT,
}

enum class CineVaultRenderDestination {
    HOST_DISPLAY,
    EXTERNAL_DISPLAY,
}

data class CineVaultRenderRequest(
    val entryPoint: CineVaultRenderEntryPoint,
    val destination: CineVaultRenderDestination,
    val displayId: Int? = null,
    val displayName: String? = null,
) {
    init {
        require(
            destination != CineVaultRenderDestination.EXTERNAL_DISPLAY ||
                displayId != null
        ) {
            "External CineVault rendering requires a displayId."
        }
    }
}

/**
 * Maps D1-4 render slots to one app-root identity.
 *
 * Both normal tablet CineVault and external-display CineVault resolve to
 * CINEVAULT_ROOT. Cinema Void is intentionally not a CineVault app-root render
 * request; it gets its own controller surface in a later phase.
 */
fun CineVaultDisplayRenderPlan.toCineVaultRenderRequests(): List<CineVaultRenderRequest> =
    buildList {
        if (host.content == CineVaultRenderContent.FULL_CINEVAULT) {
            add(
                CineVaultRenderRequest(
                    entryPoint = CineVaultRenderEntryPoint.CINEVAULT_ROOT,
                    destination = CineVaultRenderDestination.HOST_DISPLAY,
                )
            )
        }

        external
            ?.takeIf { it.content == CineVaultRenderContent.FULL_CINEVAULT }
            ?.displayId
            ?.let { displayId ->
                add(
                    CineVaultRenderRequest(
                        entryPoint = CineVaultRenderEntryPoint.CINEVAULT_ROOT,
                        destination = CineVaultRenderDestination.EXTERNAL_DISPLAY,
                        displayId = displayId,
                        displayName = external.displayName,
                    )
                )
            }
    }

/**
 * Guardrail against architectural drift: there is exactly one full-app entry
 * point for CineVault regardless of which physical display renders it.
 */
fun CineVaultRenderRequest.usesCanonicalCineVaultRoot(): Boolean =
    entryPoint == CineVaultRenderEntryPoint.CINEVAULT_ROOT
