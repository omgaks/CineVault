package com.sole.cinevault.glasses.halo

/**
 * D5-1 — player integration gate for canonical Halo.
 *
 * Keeps the decision about where Halo interaction belongs independent from
 * device names. The only inputs are runtime capabilities/state.
 */
object HaloPlayerIntegrationPolicy {

    fun resolve(
        externalDisplayActive: Boolean,
        externalTargetSurfaceAvailable: Boolean,
        transientUiVisible: Boolean,
    ): HaloPlayerIntegrationState {
        if (!externalDisplayActive) {
            return HaloPlayerIntegrationState(
                route = HaloPlayerInteractionRoute.LOCAL_TOUCH,
                haloTargetingEnabled = false,
                transientUiHasPriority = transientUiVisible,
            )
        }

        if (!externalTargetSurfaceAvailable) {
            return HaloPlayerIntegrationState(
                route = HaloPlayerInteractionRoute.EXTERNAL_GESTURES_ONLY,
                haloTargetingEnabled = false,
                transientUiHasPriority = transientUiVisible,
            )
        }

        return HaloPlayerIntegrationState(
            route = HaloPlayerInteractionRoute.CANONICAL_HALO,
            haloTargetingEnabled = true,
            transientUiHasPriority = transientUiVisible,
        )
    }
}

data class HaloPlayerIntegrationState(
    val route: HaloPlayerInteractionRoute,
    val haloTargetingEnabled: Boolean,
    val transientUiHasPriority: Boolean,
)

enum class HaloPlayerInteractionRoute {
    LOCAL_TOUCH,
    EXTERNAL_GESTURES_ONLY,
    CANONICAL_HALO,
}
