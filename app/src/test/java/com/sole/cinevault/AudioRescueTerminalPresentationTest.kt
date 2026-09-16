package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRescueTerminalPresentationTest {

    @Test
    fun postFfmpegAudioFailureHasStablePresentation() {
        val presentation =
            AudioRescueTerminalFailure(
                reason =
                    AudioRescueTerminalFailureReason
                        .AUDIO_RENDERER_FAILED_AFTER_FFMPEG_RESCUE,
                rescueWasAttempted = true,
            ).toPresentation()

        assertEquals(
            "Audio playback failed after FFmpeg rescue.",
            presentation.userMessage,
        )
        assertEquals(
            "audio_renderer_failed_after_ffmpeg_rescue",
            presentation.diagnosticTag,
        )
    }
}
