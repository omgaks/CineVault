package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRuntimeRescueLifecycleDecisionTest {

    @Test
    fun sameVideoIsKeptOnEntryAndResetOnExit() {
        val path = "/movies/a.mkv"

        assertEquals(
            AudioRuntimeRescueScopeDecision.KEEP,
            decideAudioRuntimeRescueScope(
                AudioRuntimeRescueScope(
                    videoPath = path,
                    hasPendingPlan = false,
                    rendererPreference = CineAudioRendererPreference.FFMPEG_FIRST,
                ),
                path,
            ),
        )

        assertEquals(
            AudioRuntimeRescueExitDecision.RESET,
            decideAudioRuntimeRescueExit(
                rescueVideoPath = path,
                exitingVideoPath = path,
            ),
        )
    }

    @Test
    fun newVideoResetsOldScopeAndOldDisposeCannotResetNewOwner() {
        val oldPath = "/movies/a.mkv"
        val newPath = "/movies/b.mkv"

        assertEquals(
            AudioRuntimeRescueScopeDecision.RESET,
            decideAudioRuntimeRescueScope(
                AudioRuntimeRescueScope(
                    videoPath = oldPath,
                    hasPendingPlan = false,
                    rendererPreference = CineAudioRendererPreference.FFMPEG_FIRST,
                ),
                newPath,
            ),
        )

        assertEquals(
            AudioRuntimeRescueExitDecision.KEEP,
            decideAudioRuntimeRescueExit(
                rescueVideoPath = newPath,
                exitingVideoPath = oldPath,
            ),
        )
    }
}
