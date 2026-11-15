package com.sole.cinevault.audiofx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val PCM_TAP_SIZE = 1024

/** Mixes [mono] (length [PCM_TAP_SIZE], range -1..1) into Visualizer-style waveform and FFT bytes. */
internal fun buildTapFrames(mono: FloatArray): Pair<ByteArray, ByteArray> {
    val n = PCM_TAP_SIZE
    val waveform = ByteArray(n) { i ->
        (mono[i].coerceIn(-1f, 1f) * 127f + 128f).toInt().coerceIn(0, 255).toByte()
    }
    val re = DoubleArray(n)
    val im = DoubleArray(n)
    for (i in 0 until n) {
        // Hann window keeps one loud sample from smearing across every bin.
        val w = 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))
        re[i] = mono[i] * w
    }
    fftInPlace(re, im)
    val fft = ByteArray(n)
    val scale = 127.0 * 4.0 / (n / 2) // gain so ordinary film audio fills the bytes
    fft[0] = (re[0] * scale).toInt().coerceIn(-127, 127).toByte()
    fft[1] = (re[n / 2] * scale).toInt().coerceIn(-127, 127).toByte()
    for (k in 1 until n / 2) {
        fft[2 * k] = (re[k] * scale).toInt().coerceIn(-127, 127).toByte()
        fft[2 * k + 1] = (im[k] * scale).toInt().coerceIn(-127, 127).toByte()
    }
    return waveform to fft
}

internal fun fftInPlace(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
        j = j xor bit
        if (i < j) {
            val tr = re[i]; re[i] = re[j]; re[j] = tr
            val ti = im[i]; im[i] = im[j]; im[j] = ti
        }
    }
    var len = 2
    while (len <= n) {
        val ang = -2.0 * PI / len
        val wr = cos(ang); val wi = sin(ang)
        var i = 0
        while (i < n) {
            var cr = 1.0; var ci = 0.0
            for (k in 0 until len / 2) {
                val ur = re[i + k]; val ui = im[i + k]
                val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                re[i + k] = ur + vr; im[i + k] = ui + vi
                re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                val nr = cr * wr - ci * wi
                ci = cr * wi + ci * wr
                cr = nr
            }
            i += len
        }
        len = len shl 1
    }
}
