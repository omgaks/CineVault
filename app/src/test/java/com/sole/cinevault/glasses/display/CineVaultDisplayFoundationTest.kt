package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultDisplayFoundationTest {

    @Test
    fun d1ClosureContainsAllFoundationInvariants() {
        assertEquals(
            CineVaultDisplayInvariant.entries.toSet(),
            CineVaultDisplayFoundation.invariants,
        )
    }

    @Test
    fun futureDisplayFeatureMustRemainAdditive() {
        val halo = CineVaultDisplayFeatureContract("Halo")

        assertTrue(halo.additiveToCanonicalUi)
        assertTrue(
            CineVaultDisplayFoundation.supports(
                CineVaultDisplayInvariant.NO_GLASSES_ONLY_PLAYER
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun forkedDisplayUiIsRejectedByContract() {
        CineVaultDisplayFeatureContract(
            name = "Separate glasses player",
            additiveToCanonicalUi = false,
        )
    }
}
