package com.sole.cinevault

internal enum class AudioRuntimeRescueRestoreStep {
    MEDIA_ITEM,
    PLAYBACK_SPEED,
    VOLUME,
    AUDIO_TRACK,
    PREPARE,
    PLAY_WHEN_READY,
}

internal val AUDIO_RUNTIME_RESCUE_RESTORE_ORDER =
    listOf(
        AudioRuntimeRescueRestoreStep.MEDIA_ITEM,
        AudioRuntimeRescueRestoreStep.PLAYBACK_SPEED,
        AudioRuntimeRescueRestoreStep.VOLUME,
        AudioRuntimeRescueRestoreStep.AUDIO_TRACK,
        AudioRuntimeRescueRestoreStep.PREPARE,
        AudioRuntimeRescueRestoreStep.PLAY_WHEN_READY,
    )
