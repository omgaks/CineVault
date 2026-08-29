package com.sole.cinevault

/**
 * Converts the player's integer brightness percentage to Android's
 * WindowManager screen-brightness level.
 */
internal fun playerWindowBrightness(
    brightnessPercent: Int,
): Float = brightnessPercent / 100f
