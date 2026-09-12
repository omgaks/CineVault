package com.sole.cinevault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Slice 67: stable mutable state for playback health/recovery presentation.
 *
 * It groups buffering, retry/error state, and dropped-frame recovery counters.
 * Recovery behavior remains in the existing player runtime effects.
 */
internal class PlayerPlaybackHealthState {
    var isBuffering by mutableStateOf(false)
    var showBufferingSpinner by mutableStateOf(false)
    var stuckBufferingHint by mutableStateOf(false)
    var playerErrorMessage by mutableStateOf<String?>(null)
    var errorRetryCount by mutableIntStateOf(0)

    var droppedFrameNudgeCount by mutableIntStateOf(0)
    var lastNudgeAtMs by mutableLongStateOf(0L)
}
