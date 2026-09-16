package com.sole.cinevault

internal data class AudioRuntimeRescueRestoreContract(
    val order: List<AudioRuntimeRescueRestoreStep>,
) {
    fun isValid(): Boolean {
        if (order.size != AudioRuntimeRescueRestoreStep.entries.size) return false
        if (order.distinct().size != order.size) return false
        if (order.firstOrNull() != AudioRuntimeRescueRestoreStep.MEDIA_ITEM) return false
        if (order.lastOrNull() != AudioRuntimeRescueRestoreStep.PLAY_WHEN_READY) return false

        val prepareIndex = order.indexOf(AudioRuntimeRescueRestoreStep.PREPARE)
        if (prepareIndex < 0) return false

        return listOf(
            AudioRuntimeRescueRestoreStep.PLAYBACK_SPEED,
            AudioRuntimeRescueRestoreStep.VOLUME,
            AudioRuntimeRescueRestoreStep.AUDIO_TRACK,
        ).all { step ->
            val index = order.indexOf(step)
            index in 0 until prepareIndex
        }
    }
}

internal val AUDIO_RUNTIME_RESCUE_RESTORE_CONTRACT =
    AudioRuntimeRescueRestoreContract(
        order = AUDIO_RUNTIME_RESCUE_RESTORE_ORDER,
    )
