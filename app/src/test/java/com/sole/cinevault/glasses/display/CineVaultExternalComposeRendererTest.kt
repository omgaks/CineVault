package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultExternalComposeRendererTest {

    @Test
    fun canonicalExternalRequest_stillTargetsCineVaultRoot() {
        val request = CineVaultRenderRequest(
            entryPoint = CineVaultRenderEntryPoint.CINEVAULT_ROOT,
            destination = CineVaultRenderDestination.EXTERNAL_DISPLAY,
            displayId = 77,
            displayName = "RayNeo",
        )

        assertTrue(request.usesCanonicalCineVaultRoot())
        assertEquals(
            CineVaultRenderEntryPoint.CINEVAULT_ROOT,
            request.entryPoint,
        )
        assertEquals(
            CineVaultRenderDestination.EXTERNAL_DISPLAY,
            request.destination,
        )
        assertEquals(77, request.displayId)
    }

    @Test
    fun normalAndExternalRendering_keepOneCanonicalRootIdentity() {
        val normal = CineVaultRenderRequest(
            entryPoint = CineVaultRenderEntryPoint.CINEVAULT_ROOT,
            destination = CineVaultRenderDestination.HOST_DISPLAY,
        )
        val external = CineVaultRenderRequest(
            entryPoint = CineVaultRenderEntryPoint.CINEVAULT_ROOT,
            destination = CineVaultRenderDestination.EXTERNAL_DISPLAY,
            displayId = 78,
        )

        assertEquals(normal.entryPoint, external.entryPoint)
    }
}
