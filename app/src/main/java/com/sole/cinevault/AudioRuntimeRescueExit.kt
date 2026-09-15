package com.sole.cinevault

internal enum class AudioRuntimeRescueExitDecision {
    KEEP,
    RESET,
}

internal fun decideAudioRuntimeRescueExit(
    rescueVideoPath: String?,
    exitingVideoPath: String,
): AudioRuntimeRescueExitDecision =
    if (rescueVideoPath == exitingVideoPath) {
        AudioRuntimeRescueExitDecision.RESET
    } else {
        AudioRuntimeRescueExitDecision.KEEP
    }
