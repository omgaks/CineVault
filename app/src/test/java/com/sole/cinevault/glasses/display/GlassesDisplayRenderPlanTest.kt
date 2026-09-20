package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesDisplayRenderPlanTest {

    @Test
    fun normalMode_rendersFullCineVaultOnHostOnly() {
        val plan = resolveGlassesDisplayMode(
            connected = false,
            displayId = null,
            displayName = null,
        ).toRenderPlan()

        assertEquals(CineVaultDisplayTarget.HOST, plan.host.target)
        assertEquals(CineVaultRenderContent.FULL_CINEVAULT, plan.host.content)
        assertNull(plan.external)
        assertFalse(plan.usesExternalCineVault)
        assertFalse(plan.usesCinemaVoid)
    }

    @Test
    fun glassesMode_rendersCinemaVoidOnHostAndFullCineVaultExternally() {
        val plan = resolveGlassesDisplayMode(
            connected = true,
            displayId = 14,
            displayName = "RayNeo",
        ).toRenderPlan()

        assertEquals(CineVaultRenderContent.CINEMA_VOID, plan.host.content)
        assertTrue(plan.usesCinemaVoid)

        val external = requireNotNull(plan.external)
        assertEquals(CineVaultDisplayTarget.EXTERNAL, external.target)
        assertEquals(CineVaultRenderContent.FULL_CINEVAULT, external.content)
        assertEquals(14, external.displayId)
        assertEquals("RayNeo", external.displayName)
        assertTrue(plan.usesExternalCineVault)
    }

    @Test
    fun disabledMode_neverRequestsCinemaVoidOrExternalCineVault() {
        val plan = resolveGlassesDisplayMode(
            connected = true,
            displayId = 6,
            displayName = "External display",
            enabled = false,
        ).toRenderPlan()

        assertEquals(CineVaultRenderContent.FULL_CINEVAULT, plan.host.content)
        assertNull(plan.external)
        assertFalse(plan.usesCinemaVoid)
        assertFalse(plan.usesExternalCineVault)
    }
}
