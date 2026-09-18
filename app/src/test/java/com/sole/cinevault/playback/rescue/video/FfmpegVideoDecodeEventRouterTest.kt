package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoDecodeEventRouterTest {

    private data class Fixture(
        val gate: FfmpegVideoDecodeGenerationGate,
        val pump: FfmpegVideoScheduledFramePump,
        val router: FfmpegVideoDecodeEventRouter,
    )

    private fun fixture(): Fixture {
        val clock = FfmpegVideoPlaybackClockState().apply { start(1_000L) }
        val gate = FfmpegVideoDecodeGenerationGate()
        val pump = FfmpegVideoScheduledFramePump(
            queue = FfmpegVideoFrameQueue(),
            coordinator = FfmpegVideoFrameCoordinator(
                output = FfmpegVideoOutputBinding(),
                timeline = FfmpegVideoTimelineCoordinator(clock),
                sink = FfmpegDecodedVideoFrameSink { },
            ),
            clock = clock,
        )
        val ingress = FfmpegGenerationAwareVideoFrameIngress(gate, pump)

        return Fixture(
            gate = gate,
            pump = pump,
            router = FfmpegVideoDecodeEventRouter(gate, ingress),
        )
    }

    @Test
    fun activeFrameEventIsAcceptedAndQueued() {
        val f = fixture()

        val result = f.router.route(
            FfmpegVideoDecodeEvent.Frame(
                generation = f.gate.activeGeneration,
                frame = FfmpegDecodedVideoFrame(1_005L, 1920, 1080),
            ),
        )

        assertEquals(FfmpegVideoDecodeEventResult.FRAME_ACCEPTED, result)
        assertEquals(1, f.pump.queuedFrameCount)
    }

    @Test
    fun activeEndOfStreamSetsTerminalState() {
        val f = fixture()

        val result = f.router.route(
            FfmpegVideoDecodeEvent.EndOfStream(
                f.gate.activeGeneration,
            ),
        )

        assertEquals(FfmpegVideoDecodeEventResult.END_OF_STREAM, result)
        assertEquals(
            FfmpegVideoDecodeTerminalState.END_OF_STREAM,
            f.router.terminalState,
        )
        assertNull(f.router.failureMessage)
    }

    @Test
    fun activeFailureSetsFailureStateAndMessage() {
        val f = fixture()

        val result = f.router.route(
            FfmpegVideoDecodeEvent.Failure(
                generation = f.gate.activeGeneration,
                message = "decoder failed",
            ),
        )

        assertEquals(
            FfmpegVideoDecodeEventResult.FAILED("decoder failed"),
            result,
        )
        assertEquals(
            FfmpegVideoDecodeTerminalState.FAILED,
            f.router.terminalState,
        )
        assertEquals("decoder failed", f.router.failureMessage)
    }

    @Test
    fun staleEndOfStreamCannotTerminateNewGeneration() {
        val f = fixture()
        val stale = f.gate.activeGeneration
        f.gate.advance()

        val result = f.router.route(
            FfmpegVideoDecodeEvent.EndOfStream(stale),
        )

        assertEquals(
            FfmpegVideoDecodeEventResult.REJECTED_STALE,
            result,
        )
        assertEquals(
            FfmpegVideoDecodeTerminalState.NONE,
            f.router.terminalState,
        )
    }

    @Test
    fun staleFailureCannotFailNewGeneration() {
        val f = fixture()
        val stale = f.gate.activeGeneration
        f.gate.advance()

        val result = f.router.route(
            FfmpegVideoDecodeEvent.Failure(
                stale,
                "old decoder failure",
            ),
        )

        assertEquals(
            FfmpegVideoDecodeEventResult.REJECTED_STALE,
            result,
        )
        assertEquals(
            FfmpegVideoDecodeTerminalState.NONE,
            f.router.terminalState,
        )
        assertNull(f.router.failureMessage)
    }

    @Test
    fun terminalStateCanBeResetForReplacementDecoder() {
        val f = fixture()
        f.router.route(
            FfmpegVideoDecodeEvent.Failure(
                f.gate.activeGeneration,
                "failed",
            ),
        )

        f.router.resetTerminalState()

        assertEquals(
            FfmpegVideoDecodeTerminalState.NONE,
            f.router.terminalState,
        )
        assertNull(f.router.failureMessage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankFailureMessageIsRejected() {
        FfmpegVideoDecodeEvent.Failure(
            generation = FfmpegVideoDecodeGeneration.INITIAL,
            message = " ",
        )
    }
}
