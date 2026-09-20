package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloCanonicalCineVaultSurfaceTest {

    @Test
    fun firstEventUsesNominalFrameDelta() {
        val clock = HaloEventClock()

        assertEquals(16L, clock.deltaMillis(1_000L))
    }

    @Test
    fun laterEventsUseActualElapsedTime() {
        val clock = HaloEventClock()

        clock.deltaMillis(1_000L)
        assertEquals(12L, clock.deltaMillis(1_012L))
        assertEquals(24L, clock.deltaMillis(1_036L))
    }

    @Test
    fun nonIncreasingClockStillProducesPositiveDelta() {
        val clock = HaloEventClock()

        clock.deltaMillis(1_000L)
        assertEquals(1L, clock.deltaMillis(1_000L))
    }

    @Test
    fun resetReanchorsTiming() {
        val clock = HaloEventClock()

        clock.deltaMillis(1_000L)
        clock.deltaMillis(1_040L)
        clock.reset()

        assertEquals(16L, clock.deltaMillis(5_000L))
    }
}
