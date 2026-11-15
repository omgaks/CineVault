package com.sole.cinevault.audiofx

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmTapTest {
    @Test
    fun toneLandsInTheRightFftBin() {
        val rate = 48000
        val freq = 3000.0
        val mono = FloatArray(PCM_TAP_SIZE) { (0.5 * sin(2 * PI * freq * it / rate)).toFloat() }
        val (wave, fft) = buildTapFrames(mono)
        assertEquals(PCM_TAP_SIZE, wave.size)
        assertEquals(PCM_TAP_SIZE, fft.size)
        var best = 0
        var bestMag = -1f
        for (k in 1 until PCM_TAP_SIZE / 2) {
            val re = fft[2 * k].toInt(); val im = fft[2 * k + 1].toInt()
            val m = sqrt((re * re + im * im).toFloat())
            if (m > bestMag) { bestMag = m; best = k }
        }
        val expected = (freq * PCM_TAP_SIZE / rate).toInt() // bin spacing = rate / size
        assertTrue(kotlin.math.abs(best - expected) <= 1)
    }

    @Test
    fun silenceIsCentredAt128() {
        val (wave, _) = buildTapFrames(FloatArray(PCM_TAP_SIZE))
        assertTrue(wave.all { (it.toInt() and 0xFF) == 128 })
    }
}
