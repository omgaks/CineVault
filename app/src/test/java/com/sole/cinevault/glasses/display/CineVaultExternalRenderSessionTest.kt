package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Test

class CineVaultExternalRenderSessionTest {

    @Test
    fun movingFromTabletToGlasses_keepsSameLogicalSession() {
        val controller = CineVaultRenderSessionController(
            CineVaultRenderSessionId(41L)
        )

        val hostSession = controller.binding.sessionId

        val external = controller.apply(
            CineVaultRenderRequest(
                entryPoint = CineVaultRenderEntryPoint.CINEVAULT_ROOT,
                destination = CineVaultRenderDestination.EXTERNAL_DISPLAY,
                displayId = 77,
                displayName = "RayNeo",
            )
        )

        assertEquals(hostSession, external.sessionId)
        assertEquals(CineVaultRenderSessionId(41L), external.sessionId)
        assertEquals(
            CineVaultRenderDestination.EXTERNAL_DISPLAY,
            external.destination,
        )
        assertEquals(77, external.displayId)
    }

    @Test
    fun movingBackToTablet_keepsSameLogicalSession() {
        val controller = CineVaultRenderSessionController(
            CineVaultRenderSessionId(42L)
        )

        controller.apply(
            CineVaultRenderRequest(
                entryPoint = CineVaultRenderEntryPoint.CINEVAULT_ROOT,
                destination = CineVaultRenderDestination.EXTERNAL_DISPLAY,
                displayId = 78,
            )
        )

        val host = controller.apply(
            CineVaultRenderRequest(
                entryPoint = CineVaultRenderEntryPoint.CINEVAULT_ROOT,
                destination = CineVaultRenderDestination.HOST_DISPLAY,
            )
        )

        assertEquals(CineVaultRenderSessionId(42L), host.sessionId)
        assertEquals(CineVaultRenderDestination.HOST_DISPLAY, host.destination)
        assertEquals(null, host.displayId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun externalBindingWithoutDisplayId_isRejected() {
        CineVaultRenderSessionBinding(
            sessionId = CineVaultRenderSessionId(43L),
            destination = CineVaultRenderDestination.EXTERNAL_DISPLAY,
            displayId = null,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveSessionId_isRejected() {
        CineVaultRenderSessionId(0L)
    }
}
