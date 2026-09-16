package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRuntimeRescueRestorePlanTest {

    @Test
    fun mediaItemIsAlwaysFirst() {
        assertEquals(
            AudioRuntimeRescueRestoreStep.MEDIA_ITEM,
            AUDIO_RUNTIME_RESCUE_RESTORE_ORDER.first(),
        )
    }

    @Test
    fun preparationHappensAfterContinuityState() {
        val prepareIndex =
            AUDIO_RUNTIME_RESCUE_RESTORE_ORDER.indexOf(
                AudioRuntimeRescueRestoreStep.PREPARE
            )

        assertTrue(
            AUDIO_RUNTIME_RESCUE_RESTORE_ORDER.indexOf(
                AudioRuntimeRescueRestoreStep.PLAYBACK_SPEED
            ) < prepareIndex
        )
        assertTrue(
            AUDIO_RUNTIME_RESCUE_RESTORE_ORDER.indexOf(
                AudioRuntimeRescueRestoreStep.VOLUME
            ) < prepareIndex
        )
        assertTrue(
            AUDIO_RUNTIME_RESCUE_RESTORE_ORDER.indexOf(
                AudioRuntimeRescueRestoreStep.AUDIO_TRACK
            ) < prepareIndex
        )
    }

    @Test
    fun playStateIsRestoredLast() {
        assertEquals(
            AudioRuntimeRescueRestoreStep.PLAY_WHEN_READY,
            AUDIO_RUNTIME_RESCUE_RESTORE_ORDER.last(),
        )
    }

    @Test
    fun restorePlanContainsEveryExpectedStepExactlyOnce() {
        assertEquals(6, AUDIO_RUNTIME_RESCUE_RESTORE_ORDER.size)
        assertEquals(
            AUDIO_RUNTIME_RESCUE_RESTORE_ORDER.size,
            AUDIO_RUNTIME_RESCUE_RESTORE_ORDER.distinct().size,
        )
    }
}
