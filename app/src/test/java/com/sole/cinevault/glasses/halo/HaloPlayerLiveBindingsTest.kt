package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPlayerLiveBindingsTest {

    @Test fun canonicalRouteForwardsPointerMovement() {
        var x = 0f
        var y = 0f
        val bindings = HaloPlayerLiveBindings(
            bridge = HaloPlayerInputBridge(true, true, false),
            moveCanonicalPointer = { px, py -> x = px; y = py },
            clickCanonicalTarget = { true },
        )

        bindings.movePointer(12f, -7f)

        assertEquals(12f, x)
        assertEquals(-7f, y)
    }

    @Test fun fallbackRouteDoesNotForwardPointerMovement() {
        var calls = 0
        val bindings = HaloPlayerLiveBindings(
            bridge = HaloPlayerInputBridge(true, false, false),
            moveCanonicalPointer = { _, _ -> calls++ },
            clickCanonicalTarget = { true },
        )

        bindings.movePointer(4f, 5f)

        assertEquals(0, calls)
    }

    @Test fun canonicalClickReturnsRealTargetResult() {
        val bindings = HaloPlayerLiveBindings(
            bridge = HaloPlayerInputBridge(true, true, false),
            moveCanonicalPointer = { _, _ -> },
            clickCanonicalTarget = { true },
        )

        assertTrue(bindings.clickTarget())
    }

    @Test fun unavailableCanonicalTargetCannotClickLegacyPointer() {
        var calls = 0
        val bindings = HaloPlayerLiveBindings(
            bridge = HaloPlayerInputBridge(true, false, false),
            moveCanonicalPointer = { _, _ -> },
            clickCanonicalTarget = { calls++; true },
        )

        assertFalse(bindings.clickTarget())
        assertEquals(0, calls)
    }

    @Test fun transientUiBlocksBackgroundControlToggle() {
        val bindings = HaloPlayerLiveBindings(
            bridge = HaloPlayerInputBridge(true, true, true),
            moveCanonicalPointer = { _, _ -> },
            clickCanonicalTarget = { true },
        )

        assertFalse(bindings.backgroundTapMayToggleControls())
    }

    @Test fun noTransientUiAllowsNormalBackgroundControlToggle() {
        val bindings = HaloPlayerLiveBindings(
            bridge = HaloPlayerInputBridge(true, true, false),
            moveCanonicalPointer = { _, _ -> },
            clickCanonicalTarget = { true },
        )

        assertTrue(bindings.backgroundTapMayToggleControls())
    }
}
