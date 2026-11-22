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

    @Test fun averagedBandIncludesMultipleComplexBins() {
        // Four complex bins: one loud, three silent.
        val fft = byteArrayOf(0, 0, 127, 0, 0, 0, 0, 0, 0, 0)
        val energy = NightCityFftEnergy.band(fft, 0, 1)
        assertTrue(energy > 0.15f)
        assertTrue(energy < 0.20f)
    }

    @Test fun averagedBandRejectsInvalidIndices() {
        val fft = byteArrayOf(0, 0, 127, 0)
        assertEquals(0f, NightCityFftEnergy.band(fft, -1, 2), 0f)
        assertEquals(0f, NightCityFftEnergy.band(fft, 2, 2), 0f)
        assertEquals(0f, NightCityFftEnergy.band(fft, 0, 0), 0f)
    }

    @Test fun averagedBandSilenceIsDarkAndPeakClamped() {
        assertEquals(0f, NightCityFftEnergy.band(ByteArray(66), 0, 8), 0f)
        assertTrue(NightCityFftEnergy.band(byteArrayOf(0, 0, -128, -128), 0, 1) in 0f..1f)
    }
}
