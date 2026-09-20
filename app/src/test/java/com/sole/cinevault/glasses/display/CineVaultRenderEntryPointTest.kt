package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultRenderEntryPointTest {

    @Test
    fun normalMode_routesCanonicalRootToHost() {
        val requests = resolveGlassesDisplayMode(
            connected = false,
            displayId = null,
            displayName = null,
        ).toRenderPlan().toCineVaultRenderRequests()

        assertEquals(1, requests.size)
        val request = requests.single()
        assertEquals(CineVaultRenderDestination.HOST_DISPLAY, request.destination)
        assertTrue(request.usesCanonicalCineVaultRoot())
    }

    @Test
    fun glassesMode_routesCanonicalRootToExternalOnly() {
        val requests = resolveGlassesDisplayMode(
            connected = true,
            displayId = 51,
            displayName = "RayNeo",
        ).toRenderPlan().toCineVaultRenderRequests()

        assertEquals(1, requests.size)
        val request = requests.single()
        assertEquals(
            CineVaultRenderDestination.EXTERNAL_DISPLAY,
            request.destination,
        )
        assertEquals(51, request.displayId)
        assertEquals("RayNeo", request.displayName)
        assertTrue(request.usesCanonicalCineVaultRoot())
    }

    @Test
    fun hostAndExternalNeverUseDifferentFullAppEntryPoints() {
        val normal = resolveGlassesDisplayMode(
            connected = false,
            displayId = null,
            displayName = null,
        ).toRenderPlan().toCineVaultRenderRequests().single()

        val glasses = resolveGlassesDisplayMode(
            connected = true,
            displayId = 52,
            displayName = "RayNeo",
        ).toRenderPlan().toCineVaultRenderRequests().single()

        assertEquals(normal.entryPoint, glasses.entryPoint)
        assertEquals(CineVaultRenderEntryPoint.CINEVAULT_ROOT, glasses.entryPoint)
    }

    @Test(expected = IllegalArgumentException::class)
    fun externalRequestWithoutDisplayId_isRejected() {
        CineVaultRenderRequest(
            entryPoint = CineVaultRenderEntryPoint.CINEVAULT_ROOT,
            destination = CineVaultRenderDestination.EXTERNAL_DISPLAY,
            displayId = null,
        )
    }
}
