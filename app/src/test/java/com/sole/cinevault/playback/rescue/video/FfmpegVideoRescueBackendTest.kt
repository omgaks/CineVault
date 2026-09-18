package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoRescueBackendTest {

    private class RecordingBackend(
        backendPresent: Boolean,
        decoderReady: Boolean,
        private val result: FfmpegVideoRescueStartResult =
            FfmpegVideoRescueStartResult.Started,
    ) : FfmpegVideoRescueBackend {

        var startCalled = false
            private set

        var receivedRequest: FfmpegVideoRescueRequest? = null
            private set

        override val capability: FfmpegVideoBackendCapability =
            object : FfmpegVideoBackendCapability {
                override fun snapshot() =
                    FfmpegVideoBackendCapabilitySnapshot(
                        backendPresent = backendPresent,
                        decoderReady = decoderReady,
                        detail = if (backendPresent && decoderReady) {
                            "ready"
                        } else {
                            "not ready"
                        },
                    )
            }

        override fun start(
            request: FfmpegVideoRescueRequest,
        ): FfmpegVideoRescueStartResult {
            startCalled = true
            receivedRequest = request
            return result
        }
    }

    @Test
    fun unavailableDefaultBackendRejectsSafely() {
        val result = attemptFfmpegVideoRescue(
            backend = UnavailableFfmpegVideoRescueBackend,
            mediaUri = "file:///movie.mkv",
            resumePositionMs = 5_000L,
            playWhenReady = true,
        )

        assertTrue(result is FfmpegVideoRescueStartResult.Rejected)
    }

    @Test
    fun coordinatorDoesNotCallBackendWhenCapabilityIsUnavailable() {
        val backend = RecordingBackend(
            backendPresent = true,
            decoderReady = false,
        )

        val result = attemptFfmpegVideoRescue(
            backend = backend,
            mediaUri = "file:///movie.mkv",
            resumePositionMs = 5_000L,
            playWhenReady = true,
        )

        assertFalse(backend.startCalled)
        assertTrue(result is FfmpegVideoRescueStartResult.Rejected)
    }

    @Test
    fun coordinatorCallsBackendOnceCapabilityIsVerified() {
        val backend = RecordingBackend(
            backendPresent = true,
            decoderReady = true,
        )

        val result = attemptFfmpegVideoRescue(
            backend = backend,
            mediaUri = "file:///movie.mkv",
            resumePositionMs = 44_000L,
            playWhenReady = true,
        )

        assertTrue(backend.startCalled)
        assertEquals(FfmpegVideoRescueStartResult.Started, result)
    }

    @Test
    fun coordinatorPreservesRequestStateForBackend() {
        val backend = RecordingBackend(
            backendPresent = true,
            decoderReady = true,
        )

        attemptFfmpegVideoRescue(
            backend = backend,
            mediaUri = "content://media/movie",
            resumePositionMs = 77_777L,
            playWhenReady = false,
        )

        val request = backend.receivedRequest!!
        assertEquals("content://media/movie", request.mediaUri)
        assertEquals(77_777L, request.resumePositionMs)
        assertFalse(request.playWhenReady)
    }

    @Test
    fun backendRejectionIsReturnedWithoutBeingHidden() {
        val backend = RecordingBackend(
            backendPresent = true,
            decoderReady = true,
            result = FfmpegVideoRescueStartResult.Rejected(
                reason = "decoder init failed",
            ),
        )

        val result = attemptFfmpegVideoRescue(
            backend = backend,
            mediaUri = "file:///movie.mkv",
            resumePositionMs = 0L,
            playWhenReady = true,
        )

        assertEquals(
            FfmpegVideoRescueStartResult.Rejected("decoder init failed"),
            result,
        )
    }
}
