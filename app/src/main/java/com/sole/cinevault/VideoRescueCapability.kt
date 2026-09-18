package com.sole.cinevault

/** Pure capability/decision model for Playback Rescue Stage B1. */
enum class VideoRescueBackend {
    PLATFORM_SOFTWARE,
    FFMPEG,
}

data class VideoRescueCapabilities(
    val platformSoftwareAvailable: Boolean,
    val ffmpegVideoAvailable: Boolean,
)

enum class VideoRescueStage {
    HARDWARE,
    PLATFORM_SOFTWARE,
    FFMPEG,
}

enum class VideoRescueAction {
    SWITCH_TO_PLATFORM_SOFTWARE,
    SWITCH_TO_FFMPEG,
    FAIL,
}

/**
 * Conservative VIDEO rescue order:
 * hardware -> Android software MediaCodec -> FFmpeg video -> fail.
 *
 * FFmpeg is selected only when a real FFmpeg video backend is explicitly
 * reported available. This file does not activate or claim FFmpeg decoding.
 */
fun decideNextVideoRescue(
    currentStage: VideoRescueStage,
    capabilities: VideoRescueCapabilities,
): VideoRescueAction = when (currentStage) {
    VideoRescueStage.HARDWARE -> when {
        capabilities.platformSoftwareAvailable ->
            VideoRescueAction.SWITCH_TO_PLATFORM_SOFTWARE
        capabilities.ffmpegVideoAvailable ->
            VideoRescueAction.SWITCH_TO_FFMPEG
        else -> VideoRescueAction.FAIL
    }

    VideoRescueStage.PLATFORM_SOFTWARE ->
        if (capabilities.ffmpegVideoAvailable) {
            VideoRescueAction.SWITCH_TO_FFMPEG
        } else {
            VideoRescueAction.FAIL
        }

    VideoRescueStage.FFMPEG -> VideoRescueAction.FAIL
}
