package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoRescueArchitectureRegressionTest {

    private class Bridge : FfmpegVideoNativeBridge {
        override var isCreated = false
            private set
        var releaseCount = 0
            private set
        private var listener: FfmpegVideoNativeEventListener? = null

        override fun create(eventListener: FfmpegVideoNativeEventListener) {
            listener = eventListener
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
        ) = Unit

        override fun stop() = Unit

        override fun release() {
            releaseCount++
            isCreated = false
        }

        fun emit(event: FfmpegVideoDecodeEvent) {
            requireNotNull(listener).onEvent(event)
        }
    }

    private data class Fixture(
        val bridge: Bridge,
        val runtime: FfmpegVideoRescueRuntime,
        val session: FfmpegVideoRescueSession,
    )

    private fun fixture(
        received: MutableList<FfmpegDecodedVideoFrame> =
            mutableListOf(),
    ): Fixture {
        val bridge = Bridge()
        val runtime = FfmpegVideoRescueRuntime.create(
            nativeBridge = bridge,
            frameSink = FfmpegDecodedVideoFrameSink {
                received += it
            },
        )
        return Fixture(
            bridge,
            runtime,
            FfmpegVideoRescueSession(runtime),
        )
    }

    @Test
    fun architectureContractIsFrozenAtVersionOne() {
        assertEquals(1, FfmpegVideoRescueArchitecture.CONTRACT_VERSION)
        assertTrue(
            FfmpegVideoRescueArchitecture.invariants.contains(
                "generation_guarded_callbacks",
            ),
        )
        assertTrue(
            FfmpegVideoRescueArchitecture.invariants.contains(
                "single_session_owner",
            ),
        )
    }

    @Test
    fun activeGenerationFrameTraversesCompleteKotlinPipeline() {
        val received = mutableListOf<FfmpegDecodedVideoFrame>()
        val f = fixture(received)
        val generation = f.session.prepare(1_000L)

        f.bridge.emit(
            FfmpegVideoDecodeEvent.Frame(
                generation,
                FfmpegDecodedVideoFrame(
                    presentationTimeMs = 1_005L,
                    widthPx = 1920,
                    heightPx = 1080,
                ),
            ),
        )

        assertEquals(1, f.runtime.framePump.queuedFrameCount)
        f.runtime.framePump.presentDueFrame()

        assertEquals(1, received.size)
        assertEquals(1_005L, f.runtime.clock.positionMs)
        assertEquals(
            FfmpegVideoOutputTarget(1920, 1080),
            f.runtime.output.target,
        )
    }

    @Test
    fun seekRejectsOldGenerationAndAcceptsNewGeneration() {
        val f = fixture()
        val old = f.session.prepare(1_000L)
        val active = f.session.seekTo(8_000L)

        f.bridge.emit(
            FfmpegVideoDecodeEvent.Frame(
                old,
                FfmpegDecodedVideoFrame(1_010L, 1920, 1080),
            ),
        )
        f.bridge.emit(
            FfmpegVideoDecodeEvent.Frame(
                active,
                FfmpegDecodedVideoFrame(8_005L, 1920, 1080),
            ),
        )

        assertEquals(1, f.runtime.framePump.queuedFrameCount)
    }

    @Test
    fun seekClearsTerminalFailureBeforeReplacementGeneration() {
        val f = fixture()
        val generation = f.session.prepare()

        f.bridge.emit(
            FfmpegVideoDecodeEvent.Failure(
                generation,
                "decoder failed",
            ),
        )
        assertEquals(
            FfmpegVideoDecodeTerminalState.FAILED,
            f.runtime.eventRouter.terminalState,
        )

        f.session.seekTo(5_000L)

        assertEquals(
            FfmpegVideoDecodeTerminalState.NONE,
            f.runtime.eventRouter.terminalState,
        )
        assertNull(f.runtime.eventRouter.failureMessage)
    }

    @Test
    fun sessionOwnerReplacementReleasesPreviousRuntime() {
        val first = fixture()
        val second = fixture()
        val owner = FfmpegVideoRescueSessionOwner()

        owner.install(first.runtime).prepare()
        owner.install(second.runtime)

        assertTrue(first.runtime.isReleased)
        assertFalse(second.runtime.isReleased)
        assertEquals(1, first.bridge.releaseCount)
    }

    @Test
    fun runtimeReleaseIsIdempotentAcrossOwnerCleanup() {
        val f = fixture()
        val owner = FfmpegVideoRescueSessionOwner()
        owner.install(f.runtime).prepare()

        owner.clear()
        owner.clear()
        f.runtime.release()

        assertTrue(f.runtime.isReleased)
        assertEquals(1, f.bridge.releaseCount)
    }

    @Test
    fun staleTerminalCallbackCannotPoisonActiveGeneration() {
        val f = fixture()
        val stale = f.session.prepare()
        f.session.seekTo(12_000L)

        f.bridge.emit(
            FfmpegVideoDecodeEvent.Failure(
                stale,
                "late old decoder failure",
            ),
        )

        assertEquals(
            FfmpegVideoDecodeTerminalState.NONE,
            f.runtime.eventRouter.terminalState,
        )
        assertNull(f.runtime.eventRouter.failureMessage)
    }
}
