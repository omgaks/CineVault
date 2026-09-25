package com.sole.cinevault.glasses.audio

/**
 * Pure lifecycle contract used by D13-S3 regression tests.
 */
internal enum class CineAudioRouteLifecycleAction {
    APPLY_ACTIVE_ROUTE,
    RELEASE_TO_SYSTEM,
}

internal object CineAudioRouteLifecyclePolicy {
    fun action(glassesSessionActive: Boolean): CineAudioRouteLifecycleAction =
        if (glassesSessionActive) {
            CineAudioRouteLifecycleAction.APPLY_ACTIVE_ROUTE
        } else {
            CineAudioRouteLifecycleAction.RELEASE_TO_SYSTEM
        }
}
