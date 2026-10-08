package com.sole.cinevault.picture

/** P8-S4 reference gamut conversion in LINEAR light. Never clips or enables HDR effects. */
object PictureHdrGamut {
    data class Rgb(val r: Double, val g: Double, val b: Double)

    fun bt2020ToBt709(v: Rgb): Rgb = Rgb(
        1.660491*v.r - 0.587641*v.g - 0.072850*v.b,
        -0.124550*v.r + 1.132900*v.g - 0.008350*v.b,
        -0.018151*v.r - 0.100579*v.g + 1.118730*v.b
    )

    fun bt709ToBt2020(v: Rgb): Rgb = Rgb(
        0.627404*v.r + 0.329282*v.g + 0.043314*v.b,
        0.069097*v.r + 0.919540*v.g + 0.011362*v.b,
        0.016391*v.r + 0.088013*v.g + 0.895595*v.b
    )

    fun isInUnitGamut(v: Rgb): Boolean =
        listOf(v.r, v.g, v.b).all { it.isFinite() && it in 0.0..1.0 }

    /** Explicit clipping for display only; conversion itself preserves out-of-gamut values. */
    fun clampForDisplay(v: Rgb): Rgb = Rgb(clamp(v.r), clamp(v.g), clamp(v.b))

    private fun clamp(v: Double): Double = if (v.isFinite()) v.coerceIn(0.0, 1.0) else 0.0
}
