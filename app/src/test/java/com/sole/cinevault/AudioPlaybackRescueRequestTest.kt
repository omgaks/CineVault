package com.sole.cinevault

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlaybackRescueRequestTest {

    @Test
    fun eligibleDtsFailureCreatesFfmpegFirstRequestAndConsumesAttempt() {
        val state = rescueReadyState("audio/vnd.dts", "dts")

        val request = state.prepareAudioFfmpegRescue(
            attribution = audioFailure(),
            errorCode = 4003,
            resumePositionMs = 12_345L,
            subtitleUri = null,
        )

        requireNotNull(request)
        assertEquals(4003, request.errorCode)
        assertEquals(12_345L, request.resumePositionMs)
        assertEquals(
            CineAudioRendererPreference.FFMPEG_FIRST,
            request.rendererPreference,
        )
        assertTrue(state.audioFfmpegRescueAttempted)
    }

    @Test
    fun negativeResumePositionIsClampedToZero() {
        val state = rescueReadyState("audio/true-hd", "mlpa")

        val request = state.prepareAudioFfmpegRescue(
            attribution = audioFailure(),
            errorCode = 4003,
            resumePositionMs = -500L,
            subtitleUri = null,
        )

        requireNotNull(request)
        assertEquals(0L, request.resumePositionMs)
    }

    @Test
    fun secondFailureCannotCreateSecondRescueRequest() {
        val state = rescueReadyState("audio/vnd.dts.hd", "dtsh")

        val first = state.prepareAudioFfmpegRescue(
            attribution = audioFailure(),
            errorCode = 4003,
            resumePositionMs = 1_000L,
            subtitleUri = null,
        )
        val second = state.prepareAudioFfmpegRescue(
            attribution = audioFailure(),
            errorCode = 4003,
            resumePositionMs = 2_000L,
            subtitleUri = null,
        )

        requireNotNull(first)
        assertNull(second)
        assertTrue(state.audioFfmpegRescueAttempted)
    }

    @Test
    fun aacFailureDoesNotConsumeFfmpegAttempt() {
        val state = rescueReadyState("audio/mp4a-latm", "mp4a.40.2")

        val request = state.prepareAudioFfmpegRescue(
            attribution = audioFailure(),
            errorCode = 4003,
            resumePositionMs = 1_000L,
            subtitleUri = null,
        )

        assertNull(request)
        assertFalse(state.audioFfmpegRescueAttempted)
    }

    @Test
    fun videoFailureDoesNotConsumeFfmpegAttempt() {
        val state = rescueReadyState("audio/vnd.dts", "dts")

        val request = state.prepareAudioFfmpegRescue(
            attribution = PlaybackFailureAttribution(
                streamKind = PlaybackFailureStreamKind.VIDEO,
                rendererFailure = true,
                rendererTrackType = C.TRACK_TYPE_VIDEO,
                errorCode = 4003,
            ),
            errorCode = 4003,
            resumePositionMs = 1_000L,
            subtitleUri = null,
        )

        assertNull(request)
        assertFalse(state.audioFfmpegRescueAttempted)
    }

    private fun rescueReadyState(
        mimeType: String,
        codecString: String,
    ) = PlayerPlaybackRecoveryState().apply {
        updateStreamInventory(
            PlaybackStreamInventory(
                audioStreams = listOf(
                    PlaybackStreamDescriptor(
                        kind = PlaybackStreamKind.AUDIO,
                        mimeType = mimeType,
                        codecString = codecString,
                        language = "eng",
                        selected = true,
                    )
                )
            )
        )
    }

    private fun audioFailure() = PlaybackFailureAttribution(
        streamKind = PlaybackFailureStreamKind.AUDIO,
        rendererFailure = true,
        rendererTrackType = C.TRACK_TYPE_AUDIO,
        errorCode = 4003,
    )
}
