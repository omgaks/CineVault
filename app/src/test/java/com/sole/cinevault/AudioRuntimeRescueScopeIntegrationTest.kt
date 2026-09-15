package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRuntimeRescueScopeIntegrationTest {

    @Test
    fun sameVideoRescueStateIsKept() {
        assertEquals(
            AudioRuntimeRescueScopeDecision.KEEP,
            decideAudioRuntimeRescueScope(
                AudioRuntimeRescueScope(
                    videoPath = "/movie/a.mkv",
                    hasPendingPlan = false,
                    rendererPreference = CineAudioRendererPreference.FFMPEG_FIRST,
                ),
                "/movie/a.mkv",
            ),
        )
    }

    @Test
    fun previousVideoRescueStateIsReset() {
        assertEquals(
            AudioRuntimeRescueScopeDecision.RESET,
            decideAudioRuntimeRescueScope(
                AudioRuntimeRescueScope(
                    videoPath = "/movie/a.mkv",
                    hasPendingPlan = false,
                    rendererPreference = CineAudioRendererPreference.FFMPEG_FIRST,
                ),
                "/movie/b.mkv",
            ),
        )
    }

    @Test
    fun orphanFfmpegModeIsResetOnEntry() {
        assertEquals(
            AudioRuntimeRescueScopeDecision.RESET,
            decideAudioRuntimeRescueScope(
                AudioRuntimeRescueScope(
                    videoPath = null,
                    hasPendingPlan = false,
                    rendererPreference = CineAudioRendererPreference.FFMPEG_FIRST,
                ),
                "/movie/a.mkv",
            ),
        )
    }
}
