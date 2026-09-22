package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultPresentationContractTest {

    @Test fun localModeRendersSharedCineVaultOnHostOnly() {
        val plan = CineVaultDisplayRenderPlan(
            host = CineVaultRenderSlot(
                target = CineVaultDisplayTarget.HOST,
                content = CineVaultRenderContent.FULL_CINEVAULT,
                displayId = null,
                displayName = null,
            ),
            external = null,
        )

        val decision = CineVaultPresentationContract.resolve(plan)

        assertEquals(CineVaultPresentationRole.SHARED_CINEVAULT, decision.host)
        assertEquals(CineVaultPresentationRole.NONE, decision.external)
        assertTrue(decision.sharesFeatureState)
        assertFalse(decision.allowsGlassesSpecificFeatureTree)
    }

    @Test fun externalModeMovesSharedCineVaultToExternalAndHostToCinemaVoid() {
        val plan = CineVaultDisplayRenderPlan(
            host = CineVaultRenderSlot(
                target = CineVaultDisplayTarget.HOST,
                content = CineVaultRenderContent.CINEMA_VOID,
                displayId = null,
                displayName = null,
            ),
            external = CineVaultRenderSlot(
                target = CineVaultDisplayTarget.EXTERNAL,
                content = CineVaultRenderContent.FULL_CINEVAULT,
                displayId = 7,
                displayName = "External display",
            ),
        )

        val decision = CineVaultPresentationContract.resolve(plan)

        assertEquals(
            CineVaultPresentationRole.CINEMA_VOID_CONTROLLER,
            decision.host,
        )
        assertEquals(
            CineVaultPresentationRole.SHARED_CINEVAULT,
            decision.external,
        )
        assertTrue(decision.sharesFeatureState)
        assertFalse(decision.allowsGlassesSpecificFeatureTree)
    }

    @Test fun contractNeverAuthorizesDuplicateGlassesFeatureTree() {
        val local = CineVaultPresentationContract.resolve(
            CineVaultDisplayRenderPlan(
                host = CineVaultRenderSlot(
                    CineVaultDisplayTarget.HOST,
                    CineVaultRenderContent.FULL_CINEVAULT,
                    null,
                    null,
                ),
                external = null,
            )
        )

        val external = CineVaultPresentationContract.resolve(
            CineVaultDisplayRenderPlan(
                host = CineVaultRenderSlot(
                    CineVaultDisplayTarget.HOST,
                    CineVaultRenderContent.CINEMA_VOID,
                    null,
                    null,
                ),
                external = CineVaultRenderSlot(
                    CineVaultDisplayTarget.EXTERNAL,
                    CineVaultRenderContent.FULL_CINEVAULT,
                    11,
                    "Display",
                ),
            )
        )

        assertFalse(local.allowsGlassesSpecificFeatureTree)
        assertFalse(external.allowsGlassesSpecificFeatureTree)
    }
}
