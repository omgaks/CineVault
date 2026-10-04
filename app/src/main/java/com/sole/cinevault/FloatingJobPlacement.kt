package com.sole.cinevault

/**
 * Pure placement policy for long-running player job pills.
 *
 * Bias is normalized to [-1, 1] so the same user placement survives a
 * portrait/landscape or freeform-window size change without becoming
 * unreachable off-screen.
 */
internal data class FloatingJobBias(
    val x: Float,
    val y: Float,
)

internal fun clampFloatingJobBias(x: Float, y: Float): FloatingJobBias =
    FloatingJobBias(
        x = x.coerceIn(-1f, 1f),
        y = y.coerceIn(-1f, 1f),
    )

internal val DefaultFloatingJobBias = FloatingJobBias(
    x = 1f,
    y = -1f,
)
