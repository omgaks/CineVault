package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoFrameSchedulerTest {
    private fun frame(pts: Long) = FfmpegDecodedVideoFrame(pts, 1920, 1080)

    @Test fun nearClockPresents() {
        assertEquals(FfmpegVideoFrameScheduleDecision.PRESENT,
            FfmpegVideoFrameScheduler().decide(1010L, 1000L))
    }

    @Test fun earlyFrameWaits() {
        assertEquals(FfmpegVideoFrameScheduleDecision.WAIT,
            FfmpegVideoFrameScheduler().decide(1100L, 1000L))
    }

    @Test fun lateFrameDrops() {
        assertEquals(FfmpegVideoFrameScheduleDecision.DROP_LATE,
            FfmpegVideoFrameScheduler().decide(800L, 1000L))
    }

    @Test fun pumpKeepsEarlyFrameQueued() {
        val clock = FfmpegVideoPlaybackClockState().apply { start(1000L) }
        val received = mutableListOf<FfmpegDecodedVideoFrame>()
        val pump = FfmpegVideoScheduledFramePump(
            FfmpegVideoFrameQueue(),
            FfmpegVideoFrameCoordinator(
                FfmpegVideoOutputBinding(),
                FfmpegVideoTimelineCoordinator(clock),
                FfmpegDecodedVideoFrameSink { received += it }),
            clock)
        pump.enqueueDecodedFrame(frame(1100L))
        assertTrue(pump.presentDueFrame() is FfmpegVideoFramePresentationResult.WAITING)
        assertEquals(1, pump.queuedFrameCount)
        assertTrue(received.isEmpty())
    }

    @Test fun pumpDropsLateThenPresentsCurrent() {
        val clock = FfmpegVideoPlaybackClockState().apply { start(1000L) }
        val received = mutableListOf<FfmpegDecodedVideoFrame>()
        val pump = FfmpegVideoScheduledFramePump(
            FfmpegVideoFrameQueue(4),
            FfmpegVideoFrameCoordinator(
                FfmpegVideoOutputBinding(),
                FfmpegVideoTimelineCoordinator(clock),
                FfmpegDecodedVideoFrameSink { received += it }),
            clock)
        pump.enqueueDecodedFrame(frame(700L))
        pump.enqueueDecodedFrame(frame(800L))
        pump.enqueueDecodedFrame(frame(1005L))
        val result = pump.presentDueFrame()
        assertTrue(result is FfmpegVideoFramePresentationResult.PRESENTED)
        result as FfmpegVideoFramePresentationResult.PRESENTED
        assertEquals(2, result.droppedLateFrameCount)
        assertEquals(1005L, result.frame.presentationTimeMs)
        assertEquals(listOf(1005L), received.map { it.presentationTimeMs })
    }

    @Test fun onlyLateFramesReturnsDroppedOnly() {
        val clock = FfmpegVideoPlaybackClockState().apply { start(2000L) }
        val pump = FfmpegVideoScheduledFramePump(
            FfmpegVideoFrameQueue(),
            FfmpegVideoFrameCoordinator(
                FfmpegVideoOutputBinding(),
                FfmpegVideoTimelineCoordinator(clock),
                FfmpegDecodedVideoFrameSink {}),
            clock)
        pump.enqueueDecodedFrame(frame(1000L))
        pump.enqueueDecodedFrame(frame(1100L))
        assertEquals(FfmpegVideoFramePresentationResult.DROPPED_ONLY(2), pump.presentDueFrame())
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeToleranceRejected() { FfmpegVideoFrameScheduler(earlyToleranceMs = -1L) }

    @Test(expected = IllegalArgumentException::class)
    fun negativeLateThresholdRejected() { FfmpegVideoFrameScheduler(lateDropThresholdMs = -1L) }
}
