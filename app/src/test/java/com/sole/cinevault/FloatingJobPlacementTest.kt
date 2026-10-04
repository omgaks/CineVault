package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingJobPlacementTest {

    @Test
    fun defaultPosition_isSafeTopRight() {
        assertEquals(1f, DefaultFloatingJobBias.x, 0f)
        assertEquals(-1f, DefaultFloatingJobBias.y, 0f)
    }

    @Test
    fun clamp_keepsPillInsideNormalizedViewport() {
        assertEquals(
            FloatingJobBias(1f, -1f),
            clampFloatingJobBias(4.5f, -3.2f),
        )
        assertEquals(
            FloatingJobBias(-1f, 1f),
            clampFloatingJobBias(-8f, 9f),
        )
    }

    @Test
    fun clamp_preservesValidUserPlacement() {
        assertEquals(
            FloatingJobBias(0.35f, -0.42f),
            clampFloatingJobBias(0.35f, -0.42f),
        )
    }
}
