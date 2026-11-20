package com.sole.cinevault.glasses

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/**
 * Settings buttons that bring the one-time glasses cards back: the intro tile on the tablet
 * and the calibration card on the glasses. Each press bumps a stamp the screens watch.
 */
internal object GlassesReplay {
    var introStamp by mutableLongStateOf(0L)
        private set
    var calibrationStamp by mutableLongStateOf(0L)
        private set

    fun showIntroAgain() { introStamp = System.nanoTime() }
    fun recalibrate() { calibrationStamp = System.nanoTime() }
}
