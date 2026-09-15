package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRuntimeRescueExitDecisionTest {

    @Test
    fun ownerVideoExitResetsRescue() {
        assertEquals(
            AudioRuntimeRescueExitDecision.RESET,
            decideAudioRuntimeRescueExit(
                rescueVideoPath = "/movies/a.mkv",
                exitingVideoPath = "/movies/a.mkv",
            ),
        )
    }

    @Test
    fun staleDisposeFromDifferentVideoCannotResetActiveRescue() {
        assertEquals(
            AudioRuntimeRescueExitDecision.KEEP,
            decideAudioRuntimeRescueExit(
                rescueVideoPath = "/movies/b.mkv",
                exitingVideoPath = "/movies/a.mkv",
            ),
        )
    }

    @Test
    fun cleanStateNeedsNoExitReset() {
        assertEquals(
            AudioRuntimeRescueExitDecision.KEEP,
            decideAudioRuntimeRescueExit(
                rescueVideoPath = null,
                exitingVideoPath = "/movies/a.mkv",
            ),
        )
    }
}
