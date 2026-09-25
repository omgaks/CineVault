package com.sole.cinevault.glasses.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CineAudioRouteControllerTest {
    @Test fun inactiveSessionPolicyReleasesRouteToSystem() {
        val result = CineAudioRoutePolicy.decide(
            devices = listOf(
                CineAudioRouteDevice(11, CineAudioRouteKind.GLASSES, "RayNeo")
            ),
            glassesSessionActive = false,
        )
        assertNull(result.preferredDeviceId)
        assertEquals(CineAudioRouteReason.SYSTEM_DEFAULT, result.reason)
    }

    @Test fun canonicalRouteDecisionTargetsDetectedGlassesSink() {
        val result = CineAudioRoutePolicy.decide(
            devices = listOf(
                CineAudioRouteDevice(1, CineAudioRouteKind.BUILTIN, "Speaker"),
                CineAudioRouteDevice(11, CineAudioRouteKind.GLASSES, "RayNeo"),
            ),
            glassesSessionActive = true,
        )
        assertEquals(11, result.preferredDeviceId)
        assertEquals(CineAudioRouteReason.GLASSES_PREFERRED, result.reason)
    }
}
