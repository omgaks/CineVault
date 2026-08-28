package com.sole.cinevault

internal fun adjustPlayerBrightnessPercent(
    currentPercent: Int,
    deltaY: Float,
): Int {
    return (currentPercent - deltaY.toInt() / 8).coerceIn(5, 100)
}

internal fun adjustPlayerVolumePercent(
    currentPercent: Int,
    deltaY: Float,
    maxPercent: Int,
): Int {
    return (currentPercent - deltaY.toInt() / 8).coerceIn(0, maxPercent)
}

internal fun playerSystemVolumeIndex(
    volumePercent: Int,
    maxSystemVolume: Int,
): Int {
    val systemPercent = volumePercent.coerceIn(0, 100)
    return ((systemPercent / 100f) * maxSystemVolume).toInt()
}
