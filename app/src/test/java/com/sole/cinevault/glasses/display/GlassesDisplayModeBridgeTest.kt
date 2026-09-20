package com.sole.cinevault.glasses.display

import com.sole.cinevault.glasses.ExternalDisplayInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesDisplayModeBridgeTest {

    @Test
    fun connectedDetectorState_mapsToUnifiedDisplayMode() {
        val detectorState = ExternalDisplayInfo(
            isConnected = true,
            displayId = 12,
            displayName = "RayNeo",
        )

        val state = detectorState.toGlassesDisplayModeState()

        assertTrue(state.active)
        assertEquals(12, state.externalDisplayId)
        assertEquals("RayNeo", state.externalDisplayName)
        assertEquals(
            CineVaultSurfaceRole.CINEMA_VOID_CONTROLLER,
            state.hostRole,
        )
        assertEquals(
            CineVaultSurfaceRole.CINEVAULT_EXTERNAL_DISPLAY,
            state.externalRole,
        )
    }

    @Test
    fun disconnectedDetectorState_keepsNormalHostRole() {
        val detectorState = ExternalDisplayInfo(
            isConnected = false,
            displayId = null,
            displayName = null,
        )

        val state = detectorState.toGlassesDisplayModeState()

        assertFalse(state.active)
        assertEquals(CineVaultSurfaceRole.NORMAL_APP, state.hostRole)
        assertNull(state.externalRole)
    }

    @Test
    fun disabledDisplayMode_doesNotTakeOverConnectedDisplay() {
        val detectorState = ExternalDisplayInfo(
            isConnected = true,
            displayId = 4,
            displayName = "External display",
        )

        val state = detectorState.toGlassesDisplayModeState(enabled = false)

        assertFalse(state.active)
        assertEquals(CineVaultSurfaceRole.NORMAL_APP, state.hostRole)
        assertNull(state.externalRole)
    }
}
