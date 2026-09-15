package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRuntimeRescueVideoScopeTest {

    @Test
    fun noActiveRescueIsNoOp() {
        AudioRuntimeRescueController.reset()

        assertFalse(
            AudioRuntimeRescueController.resetForVideo("/movies/next.mkv")
        )
        assertFalse(
            AudioRuntimeRescueController.endVideoScope("/movies/next.mkv")
        )
    }

    @Test
    fun resetRestoresPlatformFirst() {
        AudioRuntimeRescueController.reset()

        assertTrue(
            AudioRuntimeRescueController.rendererPreference ==
                CineAudioRendererPreference.PLATFORM_FIRST
        )
    }
}
