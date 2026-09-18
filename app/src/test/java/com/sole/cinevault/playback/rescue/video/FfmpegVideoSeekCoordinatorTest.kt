package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FfmpegVideoSeekCoordinatorTest {

    private fun frame(pts: Long) =
        FfmpegDecodedVideoFrame(pts, 1920, 1080)

    private data class Fixture(
        val clock: FfmpegVideoPlaybackClockState,
        val pump: FfmpegVideoScheduledFramePump,
        val timeline: FfmpegVideoTimelineCoordinator,
    )

    private fun fixture(startMs: Long = 1_000L): Fixture {
        val clock = FfmpegVideoPlaybackClockState().apply { start(startMs) }
        val timeline = FfmpegVideoTimelineCoordinator(clock)
        val pump = FfmpegVideoScheduledFramePump(
            queue = FfmpegVideoFrameQueue(),
            coordinator = FfmpegVideoFrameCoordinator(
                output = FfmpegVideoOutputBinding(),
                timeline = timeline,
                sink = FfmpegDecodedVideoFrameSink { },
            ),
            clock = clock,
        )
        return Fixture(clock, pump, timeline)
    }

    @Test
    fun seekFlushesQueuedFramesAndMovesClock() {
        val f = fixture()
        f.pump.enqueueDecodedFrame(frame(1_100L))
        f.pump.enqueueDecodedFrame(frame(1_200L))

        val target = FfmpegVideoSeekCoordinator(f.pump, f.timeline)
            .seekTo(5_000L)

        assertEquals(5_000L, target)
        assertEquals(5_000L, f.clock.positionMs)
        assertEquals(0, f.pump.queuedFrameCount)
        assertEquals(
            FfmpegVideoFramePresentationResult.EMPTY,
            f.pump.presentDueFrame(),
        )
    }

    @Test
    fun negativeSeekClampsToZero() {
        val f = fixture()

        val target = FfmpegVideoSeekCoordinator(f.pump, f.timeline)
            .seekTo(-500L)

        assertEquals(0L, target)
        assertEquals(0L, f.clock.positionMs)
    }

    @Test
    fun seekPathCanAcceptFreshPostSeekFrame() {
        val f = fixture()
        val seek = FfmpegVideoSeekCoordinator(f.pump, f.timeline)

        seek.seekTo(5_000L)
        f.pump.enqueueDecodedFrame(frame(5_005L))
        val result = f.pump.presentDueFrame()

        assertEquals(5_005L, f.clock.positionMs)
        assertEquals(0, f.pump.queuedFrameCount)
        assert(result is FfmpegVideoFramePresentationResult.PRESENTED)
    }

    @Test
    fun sourceChangeFlushesFramesButKeepsClock() {
        val f = fixture(startMs = 8_000L)
        f.pump.enqueueDecodedFrame(frame(8_100L))
        val discontinuity =
            FfmpegVideoDiscontinuityCoordinator(f.pump, f.timeline)

        discontinuity.onDiscontinuity(
            FfmpegVideoDiscontinuityReason.SOURCE_CHANGE,
        )

        assertEquals(0, f.pump.queuedFrameCount)
        assertEquals(8_000L, f.clock.positionMs)
    }

    @Test
    fun decoderResetFlushesFramesButKeepsClock() {
        val f = fixture(startMs = 9_000L)
        f.pump.enqueueDecodedFrame(frame(9_100L))
        val discontinuity =
            FfmpegVideoDiscontinuityCoordinator(f.pump, f.timeline)

        discontinuity.onDiscontinuity(
            FfmpegVideoDiscontinuityReason.DECODER_RESET,
        )

        assertEquals(0, f.pump.queuedFrameCount)
        assertEquals(9_000L, f.clock.positionMs)
    }

    @Test
    fun seekDiscontinuityFlushesAndUpdatesPosition() {
        val f = fixture()
        f.pump.enqueueDecodedFrame(frame(1_100L))
        val discontinuity =
            FfmpegVideoDiscontinuityCoordinator(f.pump, f.timeline)

        discontinuity.onDiscontinuity(
            reason = FfmpegVideoDiscontinuityReason.SEEK,
            positionMs = 12_000L,
        )

        assertEquals(0, f.pump.queuedFrameCount)
        assertEquals(12_000L, f.clock.positionMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun seekDiscontinuityRequiresPosition() {
        val f = fixture()
        FfmpegVideoDiscontinuityCoordinator(f.pump, f.timeline)
            .onDiscontinuity(FfmpegVideoDiscontinuityReason.SEEK)
    }
}
