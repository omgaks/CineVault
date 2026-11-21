package com.sole.cinevault.glasses.halo

/**
 * How much of the glasses window the app actually occupies, sideways.
 *
 * Normally 1. When the glasses are in their own 3D display mode the app is laid out in the
 * left half and drawn a second time in the right half (one copy per eye), so the pointer
 * must travel only over the left half. There is only one glasses surface at a time.
 */
internal object HaloSurfaceBounds {
    @Volatile
    var widthFraction: Float = 1f

    fun projectedWidthPx(viewWidthPx: Int): Int =
        (viewWidthPx * widthFraction).toInt().coerceAtLeast(1)
}
