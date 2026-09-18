package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FfmpegVideoFrameCoordinatorTest {

    @Test
    fun decodedFrameUpdatesOutputClockAndSink() {
        val output = FfmpegVideoOutputBinding()
        val clock = FfmpegVideoPlaybackClockState().apply { start(0L) }
        val timeline = FfmpegVideoTimelineCoordinator(clock)
        val received = mutableListOf<FfmpegDecodedVideoFrame>()
        val coordinator = FfmpegVideoFrameCoordinator(
            output = output,
            timeline = timeline,
            sink = FfmpegDecodedVideoFrameSink { received += it },
        )
        val frame = FfmpegDecodedVideoFrame(
            presentationTimeMs = 1_250L,
            widthPx = 1920,
            heightPx = 1080,
        )

        coordinator.onDecodedFrame(frame)

        assertEquals(frame.asOutputTarget(), output.target)
        assertEquals(1_250L, clock.positionMs)
        assertEquals(listOf(frame), received)
    }

    @Test
    fun laterFrameWithSameGeometryKeepsCorrectTargetAndAdvancesClock() {
        val output = FfmpegVideoOutputBinding()
        val clock = FfmpegVideoPlaybackClockState().apply { start(1_000L) }
        val timeline = FfmpegVideoTimelineCoordinator(clock)
        val received = mutableListOf<FfmpegDecodedVideoFrame>()
        val coordinator = FfmpegVideoFrameCoordinator(
            output,
            timeline,
            FfmpegDecodedVideoFrameSink { received += it },
        )

        coordinator.onDecodedFrame(
            FfmpegDecodedVideoFrame(1_100L, 1920, 1080),
        )
        coordinator.onDecodedFrame(
            FfmpegDecodedVideoFrame(1_200L, 1920, 1080),
        )

        assertEquals(FfmpegVideoOutputTarget(1920, 1080), output.target)
        assertEquals(1_200L, clock.positionMs)
        assertEquals(2, received.size)
    }

    @Test
    fun geometryChangeRebindsOutputTarget() {
        val output = FfmpegVideoOutputBinding()
        val clock = FfmpegVideoPlaybackClockState().apply { start(0L) }
        val coordinator = FfmpegVideoFrameCoordinator(
            output,
            FfmpegVideoTimelineCoordinator(clock),
            FfmpegDecodedVideoFrameSink { },
        )

        coordinator.onDecodedFrame(
            FfmpegDecodedVideoFrame(100L, 1920, 1080),
        )
        coordinator.onDecodedFrame(
            FfmpegDecodedVideoFrame(
                presentationTimeMs = 200L,
                widthPx = 1080,
                heightPx = 1920,
                rotationDegrees = 90,
            ),
        )

        assertEquals(
            FfmpegVideoOutputTarget(1080, 1920, 90),
            output.target,
        )
    }

    @Test
    fun smallBackwardPtsJitterStillForwardsFrameButDoesNotMoveClockBackward() {
        val output = FfmpegVideoOutputBinding()
        val clock = FfmpegVideoPlaybackClockState().apply { start(10_000L) }
        val received = mutableListOf<FfmpegDecodedVideoFrame>()
        val coordinator = FfmpegVideoFrameCoordinator(
            output,
            FfmpegVideoTimelineCoordinator(
                clock = clock,
                backwardJitterToleranceMs = 250L,
            ),
            FfmpegDecodedVideoFrameSink { received += it },
        )
        val frame = FfmpegDecodedVideoFrame(9_900L, 1920, 1080)

        coordinator.onDecodedFrame(frame)

        assertEquals(10_000L, clock.positionMs)
        assertEquals(listOf(frame), received)
    }

    @Test
    fun detachOutputClearsBinding() {
        val output = FfmpegVideoOutputBinding()
        val clock = FfmpegVideoPlaybackClockState()
        val coordinator = FfmpegVideoFrameCoordinator(
            output,
            FfmpegVideoTimelineCoordinator(clock),
            FfmpegDecodedVideoFrameSink { },
        )
        coordinator.onDecodedFrame(
            FfmpegDecodedVideoFrame(0L, 1280, 720),
        )

        coordinator.detachOutput()

        assertNull(output.target)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeFrameTimestampIsRejected() {
        FfmpegDecodedVideoFrame(-1L, 1920, 1080)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidFrameWidthIsRejected() {
        FfmpegDecodedVideoFrame(0L, 0, 1080)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidFrameRotationIsRejected() {
        FfmpegDecodedVideoFrame(0L, 1920, 1080, 45)
    }
}
