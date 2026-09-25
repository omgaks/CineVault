package com.sole.cinevault.glasses.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CineAudioRoutePolicyTest {
    private fun device(id: Int, kind: CineAudioRouteKind, sink: Boolean = true) =
        CineAudioRouteDevice(id, kind, kind.name, sink)

    @Test fun normalPhonePlaybackNeverSeizesAudioRouting() {
        val result = CineAudioRoutePolicy.decide(
            listOf(device(1, CineAudioRouteKind.GLASSES)), false
        )
        assertNull(result.preferredDeviceId)
        assertEquals(CineAudioRouteReason.SYSTEM_DEFAULT, result.reason)
    }

    @Test fun activeGlassesSessionPrefersGlassesSink() {
        val result = CineAudioRoutePolicy.decide(
            listOf(device(2, CineAudioRouteKind.BUILTIN), device(7, CineAudioRouteKind.GLASSES)), true
        )
        assertEquals(7, result.preferredDeviceId)
        assertEquals(CineAudioRouteReason.GLASSES_PREFERRED, result.reason)
    }

    @Test fun externalFallbackOrderIsDeterministic() {
        val result = CineAudioRoutePolicy.decide(
            listOf(
                device(3, CineAudioRouteKind.BLUETOOTH),
                device(4, CineAudioRouteKind.HDMI),
                device(5, CineAudioRouteKind.WIRED),
            ), true
        )
        assertEquals(4, result.preferredDeviceId)
        assertEquals(CineAudioRouteReason.SYSTEM_EXTERNAL_FALLBACK, result.reason)
    }

    @Test fun sourceOnlyDevicesAreIgnored() {
        val result = CineAudioRoutePolicy.decide(
            listOf(
                device(9, CineAudioRouteKind.GLASSES, false),
                device(2, CineAudioRouteKind.BUILTIN),
            ), true
        )
        assertEquals(2, result.preferredDeviceId)
        assertEquals(CineAudioRouteReason.BUILTIN_FALLBACK, result.reason)
    }

    @Test fun noUsableSinkLeavesRoutingToAndroid() {
        val result = CineAudioRoutePolicy.decide(emptyList(), true)
        assertNull(result.preferredDeviceId)
        assertEquals(CineAudioRouteReason.SYSTEM_DEFAULT, result.reason)
    }
}
