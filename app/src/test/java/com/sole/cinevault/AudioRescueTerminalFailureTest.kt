package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRescueTerminalFailureTest {

    @Test
    fun failedPostFfmpegRescueProducesTerminalFailure() {
        val failure =
            terminalAudioRescueFailureOrNull(
                PostFfmpegAudioFailureAction.FAIL_RESCUE
            )

        assertEquals(
            AudioRescueTerminalFailureReason
                .AUDIO_RENDERER_FAILED_AFTER_FFMPEG_RESCUE,
            failure?.reason,
        )
        assertTrue(failure?.rescueWasAttempted == true)
    }

    @Test
    fun normalRecoveryDoesNotProduceTerminalRescueFailure() {
        assertNull(
            terminalAudioRescueFailureOrNull(
                PostFfmpegAudioFailureAction.USE_NORMAL_RECOVERY
            )
        )
    }
}
