package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTransportFitTest {
    @Test fun roomyScreenKeepsFullSize() {
        assertEquals(1f, transportFitFactor(900f, 48f, 64f, 7, 7f, 4f, 12f), 0.0001f)
    }

    @Test fun narrowScreenShrinksToFit() {
        val f = transportFitFactor(360f, 48f, 64f, 8, 7f, 4f, 12f)
        assertTrue(f < 1f && f >= MIN_TRANSPORT_FIT)
        val content = (8 * 48f + 64f + 8 * 7f + 4f + 24f) * f
        assertTrue(content <= 360.01f || f == MIN_TRANSPORT_FIT)
    }

    @Test fun neverShrinksBelowTheFloor() {
        assertEquals(MIN_TRANSPORT_FIT, transportFitFactor(100f, 48f, 64f, 8, 7f, 4f, 12f), 0.0001f)
    }

    @Test fun badInputsAreIgnored() {
        assertEquals(1f, transportFitFactor(0f, 48f, 64f, 7, 7f, 4f, 12f), 0.0001f)
    }
}
