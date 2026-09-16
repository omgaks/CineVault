package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRuntimeRescueRestoreContractTest {

    @Test
    fun productionRestoreContractIsValid() {
        assertTrue(AUDIO_RUNTIME_RESCUE_RESTORE_CONTRACT.isValid())
    }

    @Test
    fun duplicateStepIsRejected() {
        val broken = AUDIO_RUNTIME_RESCUE_RESTORE_ORDER.toMutableList().apply {
            this[1] = AudioRuntimeRescueRestoreStep.VOLUME
        }

        assertFalse(
            AudioRuntimeRescueRestoreContract(broken).isValid()
        )
    }

    @Test
    fun prepareBeforeContinuityStateIsRejected() {
        val broken = listOf(
            AudioRuntimeRescueRestoreStep.MEDIA_ITEM,
            AudioRuntimeRescueRestoreStep.PREPARE,
            AudioRuntimeRescueRestoreStep.PLAYBACK_SPEED,
            AudioRuntimeRescueRestoreStep.VOLUME,
            AudioRuntimeRescueRestoreStep.AUDIO_TRACK,
            AudioRuntimeRescueRestoreStep.PLAY_WHEN_READY,
        )

        assertFalse(
            AudioRuntimeRescueRestoreContract(broken).isValid()
        )
    }

    @Test
    fun playStateBeforePrepareIsRejected() {
        val broken = listOf(
            AudioRuntimeRescueRestoreStep.MEDIA_ITEM,
            AudioRuntimeRescueRestoreStep.PLAYBACK_SPEED,
            AudioRuntimeRescueRestoreStep.VOLUME,
            AudioRuntimeRescueRestoreStep.AUDIO_TRACK,
            AudioRuntimeRescueRestoreStep.PLAY_WHEN_READY,
            AudioRuntimeRescueRestoreStep.PREPARE,
        )

        assertFalse(
            AudioRuntimeRescueRestoreContract(broken).isValid()
        )
    }

    @Test
    fun missingStepIsRejected() {
        val broken = AUDIO_RUNTIME_RESCUE_RESTORE_ORDER
            .filterNot { it == AudioRuntimeRescueRestoreStep.VOLUME }

        assertFalse(
            AudioRuntimeRescueRestoreContract(broken).isValid()
        )
    }
}
