package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesDisplayLifecycleTest {

    @Test
    fun firstValidConnection_entersExternalCineVault() {
        val state = resolveGlassesDisplayMode(
            connected = true,
            displayId = 21,
            displayName = "RayNeo",
        )

        val decision = resolveGlassesDisplayLifecycle(
            previous = GlassesDisplayLifecycleSnapshot(),
            displayModeState = state,
        )

        assertEquals(
            GlassesDisplayLifecycleAction.ENTER_EXTERNAL_CINEVAULT,
            decision.action,
        )
        assertTrue(decision.next.externalCineVaultActive)
        assertEquals(21, decision.next.externalDisplayId)
        assertTrue(decision.renderPlan.usesCinemaVoid)
    }

    @Test
    fun unchangedConnectedDisplay_isIdempotent() {
        val state = resolveGlassesDisplayMode(
            connected = true,
            displayId = 21,
            displayName = "RayNeo",
        )

        val decision = resolveGlassesDisplayLifecycle(
            previous = GlassesDisplayLifecycleSnapshot(
                externalCineVaultActive = true,
                externalDisplayId = 21,
            ),
            displayModeState = state,
        )

        assertEquals(GlassesDisplayLifecycleAction.NONE, decision.action)
        assertTrue(decision.next.externalCineVaultActive)
        assertEquals(21, decision.next.externalDisplayId)
    }

    @Test
    fun changedExternalDisplay_switchesWithoutDroppingUnifiedMode() {
        val state = resolveGlassesDisplayMode(
            connected = true,
            displayId = 22,
            displayName = "RayNeo",
        )

        val decision = resolveGlassesDisplayLifecycle(
            previous = GlassesDisplayLifecycleSnapshot(
                externalCineVaultActive = true,
                externalDisplayId = 21,
            ),
            displayModeState = state,
        )

        assertEquals(
            GlassesDisplayLifecycleAction.SWITCH_EXTERNAL_DISPLAY,
            decision.action,
        )
        assertTrue(decision.next.externalCineVaultActive)
        assertEquals(22, decision.next.externalDisplayId)
    }

    @Test
    fun disconnect_exitsExternalCineVaultAndRestoresHostPlan() {
        val state = resolveGlassesDisplayMode(
            connected = false,
            displayId = null,
            displayName = null,
        )

        val decision = resolveGlassesDisplayLifecycle(
            previous = GlassesDisplayLifecycleSnapshot(
                externalCineVaultActive = true,
                externalDisplayId = 21,
            ),
            displayModeState = state,
        )

        assertEquals(
            GlassesDisplayLifecycleAction.EXIT_EXTERNAL_CINEVAULT,
            decision.action,
        )
        assertFalse(decision.next.externalCineVaultActive)
        assertEquals(null, decision.next.externalDisplayId)
        assertFalse(decision.renderPlan.usesCinemaVoid)
    }

    @Test
    fun disconnectedAndInactive_doesNothing() {
        val state = resolveGlassesDisplayMode(
            connected = false,
            displayId = null,
            displayName = null,
        )

        val decision = resolveGlassesDisplayLifecycle(
            previous = GlassesDisplayLifecycleSnapshot(),
            displayModeState = state,
        )

        assertEquals(GlassesDisplayLifecycleAction.NONE, decision.action)
        assertFalse(decision.next.externalCineVaultActive)
    }
}
