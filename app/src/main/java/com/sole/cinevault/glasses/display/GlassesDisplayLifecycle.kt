package com.sole.cinevault.glasses.display

/**
 * D1-5: lifecycle decisions for the unified Glasses Display Mode.
 *
 * This layer decides WHEN the host/external render ownership must change.
 * It deliberately performs no Android Presentation work itself, which keeps
 * connection/reconnection policy testable and prevents a second CineVault
 * navigation/player state from appearing here.
 */
enum class GlassesDisplayLifecycleAction {
    NONE,
    ENTER_EXTERNAL_CINEVAULT,
    SWITCH_EXTERNAL_DISPLAY,
    EXIT_EXTERNAL_CINEVAULT,
}

data class GlassesDisplayLifecycleSnapshot(
    val externalCineVaultActive: Boolean = false,
    val externalDisplayId: Int? = null,
)

data class GlassesDisplayLifecycleDecision(
    val action: GlassesDisplayLifecycleAction,
    val next: GlassesDisplayLifecycleSnapshot,
    val renderPlan: CineVaultDisplayRenderPlan,
)

/**
 * Resolve the next display-lifecycle action from the last applied snapshot
 * and the newest render plan.
 *
 * Recomposition with an unchanged display is intentionally idempotent: NONE.
 */
fun resolveGlassesDisplayLifecycle(
    previous: GlassesDisplayLifecycleSnapshot,
    renderPlan: CineVaultDisplayRenderPlan,
): GlassesDisplayLifecycleDecision {
    val requestedExternal = renderPlan.external
        ?.takeIf { it.content == CineVaultRenderContent.FULL_CINEVAULT }

    val requestedDisplayId = requestedExternal?.displayId
    val wantsExternal = renderPlan.usesExternalCineVault &&
        requestedDisplayId != null

    val action = when {
        !previous.externalCineVaultActive && wantsExternal ->
            GlassesDisplayLifecycleAction.ENTER_EXTERNAL_CINEVAULT

        previous.externalCineVaultActive && !wantsExternal ->
            GlassesDisplayLifecycleAction.EXIT_EXTERNAL_CINEVAULT

        previous.externalCineVaultActive &&
            wantsExternal &&
            previous.externalDisplayId != requestedDisplayId ->
            GlassesDisplayLifecycleAction.SWITCH_EXTERNAL_DISPLAY

        else ->
            GlassesDisplayLifecycleAction.NONE
    }

    val next = if (wantsExternal) {
        GlassesDisplayLifecycleSnapshot(
            externalCineVaultActive = true,
            externalDisplayId = requestedDisplayId,
        )
    } else {
        GlassesDisplayLifecycleSnapshot()
    }

    return GlassesDisplayLifecycleDecision(
        action = action,
        next = next,
        renderPlan = renderPlan,
    )
}

/**
 * Convenience overload for the state produced by D1-1/D1-2.
 */
fun resolveGlassesDisplayLifecycle(
    previous: GlassesDisplayLifecycleSnapshot,
    displayModeState: GlassesDisplayModeState,
): GlassesDisplayLifecycleDecision =
    resolveGlassesDisplayLifecycle(
        previous = previous,
        renderPlan = displayModeState.toRenderPlan(),
    )
