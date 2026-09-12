package com.sole.cinevault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Slice 70: stable state for the main player chrome and transient HUD/menu UI.
 *
 * This holder owns presentation state only; playback and menu behavior remain
 * in the existing coordinators/effects.
 */
internal class PlayerChromeUiState(
    initialVolumePercent: Int,
) {
    var showControls by mutableStateOf(true)
    var controlsLocked by mutableStateOf(false)

    // Kept separate because a locked player can hide the normal controls while
    // still briefly exposing the unlock button.
    var lockButtonVisibleWhileLocked by mutableStateOf(true)
    var showTopBar by mutableStateOf(true)
    var isDraggingSeekbar by mutableStateOf(false)

    var volumePercent by mutableIntStateOf(initialVolumePercent)
    var brightnessPercent by mutableIntStateOf(90)
    var showVolumeCircle by mutableStateOf(false)
    var showBrightnessCircle by mutableStateOf(false)
    var brightnessGestureKey by mutableIntStateOf(0)
    var volumeGestureKey by mutableIntStateOf(0)

    var showAudioSelector by mutableStateOf(false)
    var showSpeedMenu by mutableStateOf(false)
    var showSleepMenu by mutableStateOf(false)
    var showSrtBrowser by mutableStateOf(false)
}
