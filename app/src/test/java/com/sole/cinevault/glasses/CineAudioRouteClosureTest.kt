package com.sole.cinevault.glasses.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CineAudioRouteClosureTest {
    @Test fun normalPlaybackHasNoGlassesAudioBadge() {
        val status = CineAudioRouteStatusResolver.resolve(
            CineAudioRouteDecision(null, CineAudioRouteReason.SYSTEM_DEFAULT)
        )
        assertEquals(CineAudioRouteStatusKind.SYSTEM, status.kind)
        assertNull(status.compactLabel)
        assertNull(status.guidance)
    }

    @Test fun glassesRouteHasCompactStatus() {
        val status = CineAudioRouteStatusResolver.resolve(
            CineAudioRouteDecision(7, CineAudioRouteReason.GLASSES_PREFERRED)
        )
        assertEquals(CineAudioRouteStatusKind.GLASSES, status.kind)
        assertEquals("AUDIO • GLASSES", status.compactLabel)
        assertNull(status.guidance)
    }

    @Test fun externalFallbackIsExplicit() {
        val status = CineAudioRouteStatusResolver.resolve(
            CineAudioRouteDecision(4, CineAudioRouteReason.SYSTEM_EXTERNAL_FALLBACK)
        )
        assertEquals(CineAudioRouteStatusKind.EXTERNAL_FALLBACK, status.kind)
        assertEquals("AUDIO • EXTERNAL", status.compactLabel)
        assertTrue(status.guidance?.contains("unavailable") == true)
    }

    @Test fun builtInFallbackIsExplicit() {
        val status = CineAudioRouteStatusResolver.resolve(
            CineAudioRouteDecision(2, CineAudioRouteReason.BUILTIN_FALLBACK)
        )
        assertEquals(CineAudioRouteStatusKind.BUILTIN_FALLBACK, status.kind)
        assertEquals("AUDIO • DEVICE", status.compactLabel)
        assertTrue(status.guidance?.contains("this device") == true)
    }

    @Test fun inactiveLifecycleAndSystemStatusAgree() {
        assertEquals(
            CineAudioRouteLifecycleAction.RELEASE_TO_SYSTEM,
            CineAudioRouteLifecyclePolicy.action(false),
        )
        val status = CineAudioRouteStatusResolver.resolve(
            CineAudioRoutePolicy.decide(emptyList(), false)
        )
        assertEquals(CineAudioRouteStatusKind.SYSTEM, status.kind)
        assertNull(status.compactLabel)
    }
}
