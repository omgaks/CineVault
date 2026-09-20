package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesDisplaySurfaceTest {

    @Test
    fun normalMode_routesFullCineVaultToHostOnly() {
        val surfaces = resolveGlassesDisplayMode(
            connected = false,
            displayId = null,
            displayName = null,
        ).toDisplaySurfaces()

        assertEquals(CineVaultDisplayTarget.HOST, surfaces.host.target)
        assertEquals(CineVaultSurfaceRole.NORMAL_APP, surfaces.host.role)
        assertTrue(surfaces.host.rendersFullCineVault())
        assertFalse(surfaces.host.rendersCinemaVoidController())
        assertNull(surfaces.external)
        assertFalse(surfaces.externalCineVaultActive)
    }

    @Test
    fun activeGlassesMode_routesCinemaVoidToHostAndCineVaultToExternal() {
        val surfaces = resolveGlassesDisplayMode(
            connected = true,
            displayId = 8,
            displayName = "RayNeo",
        ).toDisplaySurfaces()

        assertEquals(
            CineVaultSurfaceRole.CINEMA_VOID_CONTROLLER,
            surfaces.host.role,
        )
        assertTrue(surfaces.host.rendersCinemaVoidController())
        assertFalse(surfaces.host.rendersFullCineVault())

        val external = requireNotNull(surfaces.external)
        assertEquals(CineVaultDisplayTarget.EXTERNAL, external.target)
        assertEquals(
            CineVaultSurfaceRole.CINEVAULT_EXTERNAL_DISPLAY,
            external.role,
        )
        assertEquals(8, external.displayId)
        assertEquals("RayNeo", external.displayName)
        assertTrue(external.rendersFullCineVault())
        assertTrue(surfaces.externalCineVaultActive)
    }

    @Test
    fun disabledMode_doesNotCreateExternalCineVaultSurface() {
        val surfaces = resolveGlassesDisplayMode(
            connected = true,
            displayId = 3,
            displayName = "External display",
            enabled = false,
        ).toDisplaySurfaces()

        assertEquals(CineVaultSurfaceRole.NORMAL_APP, surfaces.host.role)
        assertNull(surfaces.external)
        assertFalse(surfaces.externalCineVaultActive)
    }
}
