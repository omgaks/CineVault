package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoRescueSessionControllerTest {

    private class FakeSession : FfmpegVideoRescueSession {
        override var state = FfmpegVideoRescueSessionState.READY
            private set

        var releaseCount = 0
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
                lastSeekMs = positionMs
            }
        }

        override fun release() {
            releaseCount++
            state = FfmpegVideoRescueSessionState.RELEASED
        }
    }

    @Test
    fun attachingReplacementReleasesPreviousSession() {
        val controller = FfmpegVideoRescueSessionController()
        val first = FakeSession()
        val second = FakeSession()

        controller.attach(first)
        controller.attach(second)

        assertEquals(1, first.releaseCount)
        assertEquals(FfmpegVideoRescueSessionState.RELEASED, first.state)
        assertTrue(controller.hasActiveSession)
        assertEquals(FfmpegVideoRescueSessionState.READY, controller.state)
    }

    @Test
    fun attachingSameSessionDoesNotReleaseIt() {
        val controller = FfmpegVideoRescueSessionController()
        val session = FakeSession()

        controller.attach(session)
        controller.attach(session)

        assertEquals(0, session.releaseCount)
        assertTrue(controller.hasActiveSession)
    }

    @Test
    fun controllerForwardsPlaybackCommands() {
        val controller = FfmpegVideoRescueSessionController()
        val session = FakeSession()
        controller.attach(session)

        controller.resume()
        assertEquals(FfmpegVideoRescueSessionState.PLAYING, controller.state)

        controller.seekTo(12_345L)
        assertEquals(12_345L, session.lastSeekMs)

        controller.pause()
        assertEquals(FfmpegVideoRescueSessionState.PAUSED, controller.state)
    }

    @Test
    fun controllerClampsNegativeSeekBeforeForwarding() {
        val controller = FfmpegVideoRescueSessionController()
        val session = FakeSession()
        controller.attach(session)

        controller.seekTo(-99L)

        assertEquals(0L, session.lastSeekMs)
    }

    @Test
    fun releaseClearsOwnershipAndIsSafeWhenRepeated() {
        val controller = FfmpegVideoRescueSessionController()
        val session = FakeSession()
        controller.attach(session)

        controller.release()
        controller.release()

        assertFalse(controller.hasActiveSession)
        assertEquals(null, controller.state)
        assertEquals(1, session.releaseCount)
    }
}
