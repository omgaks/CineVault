package com.sole.cinevault.glasses.display

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalPlayerControlsTest {
    @Test fun seekFractionMapsToFilmPosition() {
        assertEquals(0L, seekPositionMs(0f, 7_200_000L))
        assertEquals(3_600_000L, seekPositionMs(0.5f, 7_200_000L))
        assertEquals(7_200_000L, seekPositionMs(1f, 7_200_000L))
    }

    @Test fun seekFractionIsClampedAndSafeWithoutDuration() {
        assertEquals(0L, seekPositionMs(-1f, 1000L))
        assertEquals(1000L, seekPositionMs(3f, 1000L))
        assertEquals(0L, seekPositionMs(0.5f, -5L))
    }

    @Test fun speedLabelsAreShort() {
        assertEquals("1x", speedLabel(1f))
        assertEquals("2x", speedLabel(2f))
        assertEquals("1.25x", speedLabel(1.25f))
        assertEquals(true, GLASSES_SPEEDS.contains(1f))
    }
}
