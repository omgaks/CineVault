package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoRescueRequestTest {

    private fun capability(
        backendPresent: Boolean,
        decoderReady: Boolean,
    ): FfmpegVideoBackendCapability =
        object : FfmpegVideoBackendCapability {
            override fun snapshot() = FfmpegVideoBackendCapabilitySnapshot(
                backendPresent = backendPresent,
                decoderReady = decoderReady,
            )
        }

    @Test
    fun unavailableBackendCannotCreateRescueRequest() {
        assertNull(
            createFfmpegVideoRescueRequest(
                capability = UnavailableFfmpegVideoBackendCapability,
                mediaUri = "file:///movie.mkv",
                resumePositionMs = 12_345L,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun backendPresentWithoutReadyDecoderCannotCreateRequest() {
        assertNull(
            createFfmpegVideoRescueRequest(
                capability = capability(true, false),
                mediaUri = "file:///movie.mkv",
                resumePositionMs = 12_345L,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun readyDecoderWithoutBackendCannotCreateRequest() {
        assertNull(
            createFfmpegVideoRescueRequest(
                capability = capability(false, true),
                mediaUri = "file:///movie.mkv",
                resumePositionMs = 12_345L,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun verifiedBackendCreatesRequestAndPreservesPlaybackState() {
        val request = createFfmpegVideoRescueRequest(
            capability = capability(true, true),
            mediaUri = "file:///movie.mkv",
            resumePositionMs = 98_765L,
            playWhenReady = true,
        )!!

        assertEquals("file:///movie.mkv", request.mediaUri)
        assertEquals(98_765L, request.resumePositionMs)
        assertTrue(request.playWhenReady)
    }

    @Test
    fun pausedStateIsPreservedAcrossHandoff() {
        val request = createFfmpegVideoRescueRequest(
            capability = capability(true, true),
            mediaUri = "content://media/movie",
            resumePositionMs = 1_000L,
            playWhenReady = false,
        )!!

        assertFalse(request.playWhenReady)
    }

    @Test
    fun negativeRuntimePositionIsClampedBeforeRequestCreation() {
        val request = createFfmpegVideoRescueRequest(
            capability = capability(true, true),
            mediaUri = "file:///movie.mkv",
            resumePositionMs = -50L,
            playWhenReady = true,
        )!!

        assertEquals(0L, request.resumePositionMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankMediaUriIsRejected() {
        createFfmpegVideoRescueRequest(
            capability = capability(true, true),
            mediaUri = "   ",
            resumePositionMs = 0L,
            playWhenReady = true,
        )
    }
}
