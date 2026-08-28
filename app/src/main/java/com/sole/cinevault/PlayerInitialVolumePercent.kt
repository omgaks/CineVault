package com.sole.cinevault

import android.media.AudioManager

internal fun playerInitialMusicVolumePercent(
    audioManager: AudioManager,
): Int {
    val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return ((audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f) / maximum).toInt()
}
