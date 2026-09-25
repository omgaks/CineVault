package com.sole.cinevault.glasses.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class CineAudioRouteLifecyclePolicyTest {
    @Test fun activeExternalSessionAppliesRoute() {
        assertEquals(
            CineAudioRouteLifecycleAction.APPLY_ACTIVE_ROUTE,
            CineAudioRouteLifecyclePolicy.action(glassesSessionActive = true),
        )
    }

    @Test fun inactiveOrDisconnectedSessionReleasesRoute() {
        assertEquals(
            CineAudioRouteLifecycleAction.RELEASE_TO_SYSTEM,
            CineAudioRouteLifecyclePolicy.action(glassesSessionActive = false),
        )
    }
}
