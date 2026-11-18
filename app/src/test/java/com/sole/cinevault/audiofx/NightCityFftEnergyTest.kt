package com.sole.cinevault.audiofx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightCityFftEnergyTest {
    @Test fun emptyAndShortFftHaveNoEnergy() {
        assertEquals(0f, NightCityFftEnergy.at(byteArrayOf(), 0, 27), 0f)
        assertEquals(0f, NightCityFftEnergy.at(byteArrayOf(127, 127), 0, 27), 0f)
    }

    @Test fun silenceIsDark() {
        assertEquals(0f, NightCityFftEnergy.at(ByteArray(64), 12, 27), 0f)
    }

    @Test fun signedComplexComponentsContributeMagnitude() {
        val fft = byteArrayOf(0, 0, -128, -128, 0, 0)
        assertTrue(NightCityFftEnergy.at(fft, 0, 2) > 0.99f)
        assertEquals(0f, NightCityFftEnergy.at(fft, 1, 2), 0f)
    }

    @Test fun invalidBuildingCountHasNoEnergy() {
        assertEquals(0f, NightCityFftEnergy.at(byteArrayOf(0, 0, 127, 127), 0, 0), 0f)
    }

    @Test fun energyIsAlwaysNormalized() {
        val fft = byteArrayOf(0, 0, -128, -128)
        assertTrue(NightCityFftEnergy.at(fft, 100, 27) in 0f..1f)
    }
}
