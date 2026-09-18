package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoRescueSessionControllerTest {

    private class FakeBridge : FfmpegVideoNativeBridge {
        override var isCreated: Boolean = false
            private set

        var releaseCount = 0
            private set
        var lastSeekMs: Long? = null
            private set

        override fun create(eventListener: FfmpegVideoNativeEventListener) {
            isCreated = true
        }

        override fun prepare(
            generation: FfmpegVideoDecodeGeneration,
            startPositionMs: Long,
        ) = Unit

        override fun play() = Unit
        override fun pause() = Unit

        override fun seek(
            generation: FfmpegVideoDecodeGeneration,
            positionMs: Long,
        ) {
            lastSeekMs = positionMs
        }

        override fun stop() = Unit

        override fun release() {
            releaseCount++
            isCreated = false
        }
    }

    private data class SessionFixture(
        val bridge: FakeBridge,
        val runtime: FfmpegVideoRescueRuntime,
        val session: FfmpegVideoRescueSession,
    )

    private fun sessionFixture(): SessionFixture {
        val bridge = FakeBridge()
        val runtime = FfmpegVideoRescueRuntime.create(
            nativeBridge = bridge,
            frameSink = FfmpegDecodedVideoFrameSink { },
        )
        val session = FfmpegVideoRescueSession(runtime)
        session.prepare()
        return SessionFixture(bridge, runtime, session)
    }

    @Test
    fun attachingReplacementReleasesPreviousSession() {
        val controller = FfmpegVideoRescueSessionController()
        val first = sessionFixture()
        val second = sessionFixture()

        controller.attach(first.session)
        controller.attach(second.session)

        assertTrue(first.runtime.isReleased)
        assertEquals(1, first.bridge.releaseCount)
        assertTrue(controller.hasActiveSession)
        assertEquals(
            FfmpegVideoRescueSessionState.READY,
            controller.state,
        )
    }

    @Test
    fun attachingSameSessionDoesNotReleaseIt() {
        val controller = FfmpegVideoRescueSessionController()
        val fixture = sessionFixture()

        controller.attach(fixture.session)
        controller.attach(fixture.session)

        assertEquals(0, fixture.bridge.releaseCount)
        assertTrue(controller.hasActiveSession)
    }

    @Test
    fun controllerForwardsPlaybackCommands() {
        val controller = FfmpegVideoRescueSessionController()
        val fixture = sessionFixture()
        controller.attach(fixture.session)

        controller.resume()
        assertEquals(
            FfmpegVideoRescueSessionState.PLAYING,
            controller.state,
        )

        controller.seekTo(12_345L)
        assertEquals(12_345L, fixture.bridge.lastSeekMs)

        controller.pause()
        assertEquals(
            FfmpegVideoRescueSessionState.PAUSED,
            controller.state,
        )
    }

    @Test
    fun controllerClampsNegativeSeekBeforeForwarding() {
        val controller = FfmpegVideoRescueSessionController()
        val fixture = sessionFixture()
        controller.attach(fixture.session)

        controller.seekTo(-99L)

        assertEquals(0L, fixture.bridge.lastSeekMs)
    }

    @Test
    fun releaseClearsOwnershipAndIsSafeWhenRepeated() {
        val controller = FfmpegVideoRescueSessionController()
        val fixture = sessionFixture()
        controller.attach(fixture.session)

        controller.release()
        controller.release()

        assertFalse(controller.hasActiveSession)
        assertEquals(null, controller.state)
        assertEquals(1, fixture.bridge.releaseCount)
    }
}
