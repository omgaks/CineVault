package com.sole.cinevault.picture

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * P8-S2: deterministic BT.2100 transfer-function reference math.
 *
 * Pure Kotlin for unit testing. These functions do not process Media3 frames, change
 * colour metadata, perform tone mapping, or enable the HDR shader. Inputs are
 * normalized non-negative encoded channel values, with output in linear light.
 */
object PictureHdrTransfer {
    private const val PQ_M1 = 2610.0 / 16384.0
    private const val PQ_M2 = 2523.0 / 32.0
    private const val PQ_C1 = 3424.0 / 4096.0
    private const val PQ_C2 = 2413.0 / 128.0
    private const val PQ_C3 = 2392.0 / 128.0

    /** ST 2084 (PQ) EOTF: encoded [0,1] -> absolute luminance in nits [0,10000]. */
    fun pqToNits(encoded: Double): Double {
        val e = bounded(encoded)
        val v = e.pow(1.0 / PQ_M2)
        val numerator = max(v - PQ_C1, 0.0)
        val denominator = PQ_C2 - PQ_C3 * v
        if (denominator <= 0.0) return 10000.0
        return (numerator / denominator).pow(1.0 / PQ_M1) * 10000.0
    }

    /** Inverse ST 2084: absolute luminance in nits -> encoded [0,1]. */
    fun nitsToPq(nits: Double): Double {
        val y = if (nits.isFinite()) (nits / 10000.0).coerceIn(0.0, 1.0) else 0.0
        val p = y.pow(PQ_M1)
        return ((PQ_C1 + PQ_C2 * p) / (1.0 + PQ_C3 * p)).pow(PQ_M2).coerceIn(0.0, 1.0)
    }

    /** BT.2100 HLG inverse OETF: encoded [0,1] -> relative scene-linear [0,1]. */
    fun hlgToSceneLinear(encoded: Double): Double {
        val e = bounded(encoded)
        val a = 0.17883277
        val b = 1.0 - 4.0 * a
        val c = 0.55991073
        return if (e <= 0.5) (e * e) / 3.0 else (exp((e - c) / a) + b) / 12.0
    }

    /** BT.2100 HLG OETF: relative scene-linear [0,1] -> encoded [0,1]. */
    fun sceneLinearToHlg(linear: Double): Double {
        val y = bounded(linear)
        val a = 0.17883277
        val b = 1.0 - 4.0 * a
        val c = 0.55991073
        return if (y <= 1.0 / 12.0) sqrt(3.0 * y) else a * ln(12.0 * y - b) + c
    }

    private fun bounded(value: Double): Double =
        if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.0
}
