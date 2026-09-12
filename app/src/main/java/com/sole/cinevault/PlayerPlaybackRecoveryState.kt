package com.sole.cinevault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Per-video runtime state for Playback Resilience.
 *
 * The state is intentionally remembered with currentVideo.path by the screen,
 * so a fallback decision made for one title cannot leak into the next title.
 */
internal class PlayerPlaybackRecoveryState {
    var engineMode by mutableStateOf(PlaybackEngineMode.HARDWARE)

    /**
     * False until CineVault has a real software VIDEO engine that can accept
     * the current item. Slice 73 wires the state; a later slice turns this on
     * only when the fallback engine is actually available.
     */
    var softwareFallbackAvailable by mutableStateOf(false)

    var softwareFallbackRequested by mutableStateOf(false)
    var fallbackResumePositionMs by mutableStateOf(0L)
    var fallbackErrorCode by mutableIntStateOf(0)

    // Slice 76: capability report for the currently selected VIDEO track.
    // Because this entire holder is remembered per currentVideo.path, the
    // report cannot leak from one title/episode into another.
    var videoDecoderCapabilityReport by mutableStateOf<VideoDecoderCapabilityReport?>(null)

    fun requestSoftwareFallback(
        errorCode: Int,
        resumePositionMs: Long,
    ) {
        softwareFallbackRequested = true
        fallbackErrorCode = errorCode
        fallbackResumePositionMs = resumePositionMs.coerceAtLeast(0L)
    }
}
