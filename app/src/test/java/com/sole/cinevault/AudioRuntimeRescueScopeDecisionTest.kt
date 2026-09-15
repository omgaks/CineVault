package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRuntimeRescueScopeDecisionTest {

    @Test
    fun cleanPlatformFirstStateNeedsNoReset() {
        assertEquals(
            AudioRuntimeRescueScopeDecision.KEEP,
            decideAudioRuntimeRescueScope(
                scope = AudioRuntimeRescueScope(
                    videoPath = null,
                    hasPendingPlan = false,
                    rendererPreference = CineAudioRendererPreference.PLATFORM_FIRST,
                ),
                activeVideoPath = "/movies/new.mkv",
            ),
        )
    }

    @Test
    fun activeRescueForSameVideoIsKept() {
        assertEquals(
            AudioRuntimeRescueScopeDecision.KEEP,
            decideAudioRuntimeRescueScope(
                scope = AudioRuntimeRescueScope(
                    videoPath = "/movies/current.mkv",
                    hasPendingPlan = false,
                    rendererPreference = CineAudioRendererPreference.FFMPEG_FIRST,
                ),
                activeVideoPath = "/movies/current.mkv",
            ),
        )
    }

    @Test
    fun rescueFromPreviousVideoMustReset() {
        assertEquals(
            AudioRuntimeRescueScopeDecision.RESET,
            decideAudioRuntimeRescueScope(
                scope = AudioRuntimeRescueScope(
                    videoPath = "/movies/old.mkv",
                    hasPendingPlan = false,
                    rendererPreference = CineAudioRendererPreference.FFMPEG_FIRST,
                ),
                activeVideoPath = "/movies/new.mkv",
            ),
        )
    }

    @Test
    fun orphanPendingStateMustReset() {
        assertEquals(
            AudioRuntimeRescueScopeDecision.RESET,
            decideAudioRuntimeRescueScope(
                scope = AudioRuntimeRescueScope(
                    videoPath = null,
                    hasPendingPlan = true,
                    rendererPreference = CineAudioRendererPreference.PLATFORM_FIRST,
                ),
                activeVideoPath = "/movies/current.mkv",
            ),
        )
    }

    @Test
    fun orphanRescueRendererModeMustReset() {
        assertEquals(
            AudioRuntimeRescueScopeDecision.RESET,
            decideAudioRuntimeRescueScope(
                scope = AudioRuntimeRescueScope(
                    videoPath = null,
                    hasPendingPlan = false,
                    rendererPreference = CineAudioRendererPreference.FFMPEG_FIRST,
                ),
                activeVideoPath = "/movies/current.mkv",
            ),
        )
    }
}
