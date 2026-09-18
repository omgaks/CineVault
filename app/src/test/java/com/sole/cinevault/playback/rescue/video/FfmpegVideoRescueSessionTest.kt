package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoRescueSessionTest {

    private class FakeSession : FfmpegVideoRescueSession {
        override var state = FfmpegVideoRescueSessionState.READY
            private set

        var lastSeekMs: Long? = null
            private set

        override fun pause() {
            if (state != FfmpegVideoRescueSessionState.RELEASED) {
                state = FfmpegVideoRescueSessionState.PAUSED
            }
        }

        override fun resume() {
            if (state != FfmpegVideoRescueSessionState.RELEASED) {
                state = FfmpegVideoRescueSessionState.PLAYING
            }
        }

        override fun seekTo(positionMs: Long) {
            if (state != FfmpegVideoRescueSessionState.RELEASED) {
                lastSeekMs = positionMs.coerceAtLeast(0L)
            }
        }

        override fun release() {
            state = FfmpegVideoRescueSessionState.RELEASED
        }
    }

    private class FakeBackend(
        ready: Boolean,
    ) : FfmpegVideoSessionBackend {
        var openCalled = false
            private set
        var request: FfmpegVideoRescueRequest? = null
            private set

        private val session = FakeSession()

        override val capability: FfmpegVideoBackendCapability =
            object : FfmpegVideoBackendCapability {
                override fun snapshot() =
                    FfmpegVideoBackendCapabilitySnapshot(
                        backendPresent = ready,
                        decoderReady = ready,
                        detail = if (ready) "ready" else "not ready",
                    )
            }

        override fun start(
            request: FfmpegVideoRescueRequest,
        ): FfmpegVideoRescueStartResult =
            if (capability.snapshot().available) {
                FfmpegVideoRescueStartResult.Started
            } else {
                FfmpegVideoRescueStartResult.Rejected("not ready")
            }

        override fun openSession(
            request: FfmpegVideoRescueRequest,
        ): FfmpegVideoSessionOpenResult {
            openCalled = true
            this.request = request
            return FfmpegVideoSessionOpenResult.Opened(session)
        }
    }

    @Test
    fun unavailableBackendCannotOpenSession() {
        val backend = FakeBackend(ready = false)

        val result = openFfmpegVideoRescueSession(
            backend = backend,
            mediaUri = "file:///movie.mkv",
            resumePositionMs = 1_000L,
            playWhenReady = true,
        )

        assertFalse(backend.openCalled)
        assertTrue(result is FfmpegVideoSessionOpenResult.Rejected)
    }

    @Test
    fun verifiedBackendReceivesPreservedPlaybackState() {
        val backend = FakeBackend(ready = true)

        openFfmpegVideoRescueSession(
            backend = backend,
            mediaUri = "file:///movie.mkv",
            resumePositionMs = 65_432L,
            playWhenReady = false,
        )

        assertTrue(backend.openCalled)
        assertEquals(65_432L, backend.request!!.resumePositionMs)
        assertFalse(backend.request!!.playWhenReady)
    }

    @Test
    fun sessionSupportsPauseResumeSeekAndReleaseLifecycle() {
        val session = FakeSession()

        session.resume()
        assertEquals(FfmpegVideoRescueSessionState.PLAYING, session.state)

        session.pause()
        assertEquals(FfmpegVideoRescueSessionState.PAUSED, session.state)

        session.seekTo(9_999L)
        assertEquals(9_999L, session.lastSeekMs)

        session.release()
        assertEquals(FfmpegVideoRescueSessionState.RELEASED, session.state)
    }

    @Test
    fun releasedSessionCannotBeResumed() {
        val session = FakeSession()
        session.release()
        session.resume()

        assertEquals(FfmpegVideoRescueSessionState.RELEASED, session.state)
    }

    @Test
    fun negativeSeekIsSafelyClampedBySessionImplementation() {
        val session = FakeSession()
        session.seekTo(-500L)

        assertEquals(0L, session.lastSeekMs)
    }
}
