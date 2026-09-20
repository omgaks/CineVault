package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesDisplayModeStateTest {

    @Test
    fun disconnected_keepsTabletAsNormalCineVault() {
        val state = resolveGlassesDisplayMode(
            connected = false,
            displayId = null,
            displayName = null,
        )

        assertFalse(state.active)
        assertEquals(CineVaultSurfaceRole.NORMAL_APP, state.hostRole)
        assertNull(state.externalRole)
    }

    @Test
    fun connectedDisplay_assignsCinemaVoidAndExternalCineVaultRoles() {
        val state = resolveGlassesDisplayMode(
            connected = true,
            displayId = 7,
            displayName = "RayNeo",
        )

        assertTrue(state.active)
        assertEquals(CineVaultSurfaceRole.CINEMA_VOID_CONTROLLER, state.hostRole)
        assertEquals(
            CineVaultSurfaceRole.CINEVAULT_EXTERNAL_DISPLAY,
            state.externalRole,
        )
    }

    @Test
    fun connectionWithoutUsableDisplay_doesNotActivateMode() {
        val state = resolveGlassesDisplayMode(
            connected = true,
            displayId = null,
            displayName = "External display",
        )

        assertFalse(state.active)
        assertEquals(CineVaultSurfaceRole.NORMAL_APP, state.hostRole)
        assertNull(state.externalRole)
    }

    @Test
    fun disabledMode_doesNotTakeOverHostEvenWhenDisplayIsConnected() {
        val state = resolveGlassesDisplayMode(
            connected = true,
            displayId = 9,
            displayName = "RayNeo",
            enabled = false,
        )

        assertFalse(state.active)
        assertEquals(CineVaultSurfaceRole.NORMAL_APP, state.hostRole)
        assertNull(state.externalRole)
    }
}
