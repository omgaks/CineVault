package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FfmpegVideoFramePumpTest {

    private fun frame(pts: Long) =
        FfmpegDecodedVideoFrame(
            presentationTimeMs = pts,
            widthPx = 1920,
            heightPx = 1080,
        )

    private data class Fixture(
        val clock: FfmpegVideoPlaybackClockState,
        val received: MutableList<FfmpegDecodedVideoFrame>,
        val pump: FfmpegVideoFramePump,
    )

    private fun fixture(capacity: Int = 3): Fixture {
        val output = FfmpegVideoOutputBinding()
        val clock = FfmpegVideoPlaybackClockState().apply { start(0L) }
        val received = mutableListOf<FfmpegDecodedVideoFrame>()
        val coordinator = FfmpegVideoFrameCoordinator(
            output = output,
            timeline = FfmpegVideoTimelineCoordinator(clock),
            sink = FfmpegDecodedVideoFrameSink { received += it },
        )

        return Fixture(
            clock = clock,
            received = received,
            pump = FfmpegVideoFramePump(
                queue = FfmpegVideoFrameQueue(capacity),
                coordinator = coordinator,
            ),
        )
    }

    @Test
    fun framesArePresentedInQueueOrder() {
        val fixture = fixture()

        fixture.pump.enqueueDecodedFrame(frame(100L))
        fixture.pump.enqueueDecodedFrame(frame(200L))
        fixture.pump.enqueueDecodedFrame(frame(300L))

        fixture.pump.presentNextFrame()
        fixture.pump.presentNextFrame()
        fixture.pump.presentNextFrame()

        assertEquals(
            listOf(100L, 200L, 300L),
            fixture.received.map { it.presentationTimeMs },
        )
        assertEquals(300L, fixture.clock.positionMs)
        assertEquals(0, fixture.pump.queuedFrameCount)
    }

    @Test
    fun fullQueueDropsOldestFrame() {
        val fixture = fixture(capacity = 2)

        fixture.pump.enqueueDecodedFrame(frame(100L))
        fixture.pump.enqueueDecodedFrame(frame(200L))
        val result = fixture.pump.enqueueDecodedFrame(frame(300L))

        assertEquals(100L, result.droppedFrame?.presentationTimeMs)
        assertEquals(2, result.queuedFrameCount)

        fixture.pump.presentNextFrame()
        fixture.pump.presentNextFrame()

        assertEquals(
            listOf(200L, 300L),
            fixture.received.map { it.presentationTimeMs },
        )
    }

    @Test
    fun enqueueWithoutOverflowReportsNoDroppedFrame() {
        val fixture = fixture(capacity = 3)

        val result = fixture.pump.enqueueDecodedFrame(frame(100L))

        assertNull(result.droppedFrame)
        assertEquals(1, result.queuedFrameCount)
    }

    @Test
    fun presentingEmptyQueueDoesNothing() {
        val fixture = fixture()

        val presented = fixture.pump.presentNextFrame()

        assertNull(presented)
        assertEquals(emptyList<FfmpegDecodedVideoFrame>(), fixture.received)
        assertEquals(0L, fixture.clock.positionMs)
    }

    @Test
    fun flushRemovesStaleFramesBeforeSeekResume() {
        val fixture = fixture()

        fixture.pump.enqueueDecodedFrame(frame(1_000L))
        fixture.pump.enqueueDecodedFrame(frame(1_100L))

        fixture.pump.flush()

        assertEquals(0, fixture.pump.queuedFrameCount)
        assertNull(fixture.pump.presentNextFrame())
        assertEquals(emptyList<FfmpegDecodedVideoFrame>(), fixture.received)
    }

    @Test
    fun queueCanBeReusedAfterFlush() {
        val fixture = fixture()

        fixture.pump.enqueueDecodedFrame(frame(1_000L))
        fixture.pump.flush()
        fixture.pump.enqueueDecodedFrame(frame(5_000L))

        fixture.pump.presentNextFrame()

        assertEquals(
            listOf(5_000L),
            fixture.received.map { it.presentationTimeMs },
        )
        assertEquals(5_000L, fixture.clock.positionMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroCapacityIsRejected() {
        FfmpegVideoFrameQueue(capacity = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeCapacityIsRejected() {
        FfmpegVideoFrameQueue(capacity = -1)
    }
}
