package com.sole.cinevault.audiofx

import kotlin.math.sqrt

/**
 * Android Visualizer FFT uses signed interleaved real/imaginary bytes.
 * The first two entries represent DC and Nyquist, followed by complex pairs.
 * A zero-filled or absent FFT should produce no illumination boost.
 */
internal object NightCityFftEnergy {
    fun at(fft: ByteArray, buildingIndex: Int, buildingCount: Int): Float {
        if (fft.size < 4 || buildingCount <= 0) return 0f
        val complexBins = (fft.size - 2) / 2
        if (complexBins <= 0) return 0f
        val index = (buildingIndex.coerceAtLeast(0).toLong() * complexBins /
            buildingCount).toInt().coerceIn(0, complexBins - 1)
        val offset = 2 + index * 2
        val real = fft[offset].toInt().toFloat()
        val imaginary = fft[offset + 1].toInt().toFloat()
        // Each signed component ranges from -128 to 127.
        return (sqrt(real * real + imaginary * imaginary) / 181.02f).coerceIn(0f, 1f)
    }
}
