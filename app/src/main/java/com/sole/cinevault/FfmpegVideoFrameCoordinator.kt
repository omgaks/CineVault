package com.sole.cinevault.playback.rescue.video

/**
 * Coordinates decoded-frame metadata with output geometry and playback time.
 *
 * B10 deliberately keeps actual native pixel-buffer rendering out of this
 * class. Its responsibility is to make frame delivery deterministic:
 *  - update output geometry only when it changes;
 *  - publish frame PTS to the timeline;
 *  - forward the frame to the rendering/native sink.
 */
class FfmpegVideoFrameCoordinator(
    private val output: FfmpegVideoOutput,
    private val timeline: FfmpegVideoTimelineCoordinator,
    private val sink: FfmpegDecodedVideoFrameSink,
) {
    fun onDecodedFrame(frame: FfmpegDecodedVideoFrame) {
        val nextTarget = frame.asOutputTarget()

        if (output.target != nextTarget) {
            output.attach(nextTarget)
        }

        timeline.onPresentationTimestamp(
            FfmpegVideoPresentationTimestamp(frame.presentationTimeMs),
        )

        sink.onFrame(frame)
    }

    fun detachOutput() {
        output.detach()
    }
}
