package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoDecodeGenerationGateTest {

    private fun frame(pts: Long) =
        FfmpegDecodedVideoFrame(pts, 1920, 1080)

    private data class Fixture(
        val clock: FfmpegVideoPlaybackClockState,
        val gate: FfmpegVideoDecodeGenerationGate,
        val pump: FfmpegVideoScheduledFramePump,
        val ingress: FfmpegGenerationAwareVideoFrameIngress,
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
        return Fixture(
            clock = clock,
            gate = gate,
            pump = pump,
            ingress = FfmpegGenerationAwareVideoFrameIngress(gate, pump),
        )
    }

    @Test
    fun initialGenerationIsAccepted() {
        val f = fixture()

        val result = f.ingress.onDecodedFrame(
            FfmpegGenerationTaggedVideoFrame(
                generation = FfmpegVideoDecodeGeneration.INITIAL,
                frame = frame(1_005L),
            ),
        )

        assertTrue(result.accepted)
        assertEquals(1, f.pump.queuedFrameCount)
    }

    @Test
    fun advancingGenerationRejectsOldDecoderFrame() {
        val f = fixture()
        val oldGeneration = f.gate.activeGeneration
        val newGeneration = f.gate.advance()

        val result = f.ingress.onDecodedFrame(
            FfmpegGenerationTaggedVideoFrame(
                generation = oldGeneration,
                frame = frame(1_005L),
            ),
        )

        assertFalse(result.accepted)
        assertEquals(newGeneration, result.activeGeneration)
        assertEquals(0, f.pump.queuedFrameCount)
    }

    @Test
    fun frameFromNewGenerationIsAccepted() {
        val f = fixture()
        val newGeneration = f.gate.advance()

        val result = f.ingress.onDecodedFrame(
            FfmpegGenerationTaggedVideoFrame(
                generation = newGeneration,
                frame = frame(1_005L),
            ),
        )

        assertTrue(result.accepted)
        assertEquals(1, f.pump.queuedFrameCount)
    }

    @Test
    fun staleFrameCannotReachPresentationAfterGenerationAdvance() {
        val f = fixture()
        val stale = f.gate.activeGeneration
        val active = f.gate.advance()

        f.ingress.onDecodedFrame(
            FfmpegGenerationTaggedVideoFrame(stale, frame(1_005L)),
        )
        f.ingress.onDecodedFrame(
            FfmpegGenerationTaggedVideoFrame(active, frame(1_010L)),
        )

        val result = f.pump.presentDueFrame()

        assertTrue(result is FfmpegVideoFramePresentationResult.PRESENTED)
        result as FfmpegVideoFramePresentationResult.PRESENTED
        assertEquals(1_010L, result.frame.presentationTimeMs)
        assertEquals(0, f.pump.queuedFrameCount)
    }

    @Test
    fun multipleAdvancesInvalidateEveryEarlierGeneration() {
        val gate = FfmpegVideoDecodeGenerationGate()
        val generation0 = gate.activeGeneration
        val generation1 = gate.advance()
        val generation2 = gate.advance()

        assertFalse(gate.accepts(generation0))
        assertFalse(gate.accepts(generation1))
        assertTrue(gate.accepts(generation2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeGenerationIsRejected() {
        FfmpegVideoDecodeGeneration(-1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generationOverflowIsRejected() {
        FfmpegVideoDecodeGeneration(Long.MAX_VALUE).next()
    }
}
