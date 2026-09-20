package com.sole.cinevault.glasses.display

/**
 * D1-6: small Android-facing bridge for the unified Glasses Display lifecycle.
 *
 * The controller owns only the last applied lifecycle snapshot and delegates
 * actual Presentation/display work to [GlassesDisplayLifecycleHost].
 *
 * Important: it does NOT create another CineVault navigation tree or player.
 */
interface GlassesDisplayLifecycleHost {
    fun enterExternalCineVault(displayId: Int)
    fun switchExternalCineVault(fromDisplayId: Int?, toDisplayId: Int)
    fun exitExternalCineVault()
}

class GlassesDisplayLifecycleController(
    private val host: GlassesDisplayLifecycleHost,
) {
    var snapshot: GlassesDisplayLifecycleSnapshot = GlassesDisplayLifecycleSnapshot()
        private set

    fun apply(renderPlan: CineVaultDisplayRenderPlan): GlassesDisplayLifecycleDecision {
        val decision = resolveGlassesDisplayLifecycle(
            previous = snapshot,
            renderPlan = renderPlan,
        )

        when (decision.action) {
            GlassesDisplayLifecycleAction.NONE -> Unit

            GlassesDisplayLifecycleAction.ENTER_EXTERNAL_CINEVAULT -> {
                decision.next.externalDisplayId?.let(host::enterExternalCineVault)
            }

            GlassesDisplayLifecycleAction.SWITCH_EXTERNAL_DISPLAY -> {
                decision.next.externalDisplayId?.let { nextId ->
                    host.switchExternalCineVault(
                        fromDisplayId = snapshot.externalDisplayId,
                        toDisplayId = nextId,
                    )
                }
            }

            GlassesDisplayLifecycleAction.EXIT_EXTERNAL_CINEVAULT -> {
                host.exitExternalCineVault()
            }
        }

        snapshot = decision.next
        return decision
    }

    fun apply(displayModeState: GlassesDisplayModeState): GlassesDisplayLifecycleDecision =
        apply(displayModeState.toRenderPlan())

    /**
     * Used when the Android host is destroyed/recreated. This forgets applied
     * ownership without issuing another display operation.
     */
    fun reset() {
        snapshot = GlassesDisplayLifecycleSnapshot()
    }
}
