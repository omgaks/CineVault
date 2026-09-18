package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoRescueCapabilityTest {

    @Test
    fun hardwarePrefersExistingPlatformSoftwareLane() {
        assertEquals(
            VideoRescueAction.SWITCH_TO_PLATFORM_SOFTWARE,
            decideNextVideoRescue(
                VideoRescueStage.HARDWARE,
                VideoRescueCapabilities(true, true),
            ),
        )
    }

    @Test
    fun hardwareCanGoDirectlyToFfmpegWhenPlatformSoftwareIsUnavailable() {
        assertEquals(
            VideoRescueAction.SWITCH_TO_FFMPEG,
            decideNextVideoRescue(
                VideoRescueStage.HARDWARE,
                VideoRescueCapabilities(false, true),
            ),
        )
    }

    @Test
    fun platformSoftwareFailureEscalatesToFfmpegWhenAvailable() {
        assertEquals(
            VideoRescueAction.SWITCH_TO_FFMPEG,
            decideNextVideoRescue(
                VideoRescueStage.PLATFORM_SOFTWARE,
                VideoRescueCapabilities(true, true),
            ),
        )
    }

    @Test
    fun platformSoftwareFailureStopsWhenFfmpegVideoIsUnavailable() {
        assertEquals(
            VideoRescueAction.FAIL,
            decideNextVideoRescue(
                VideoRescueStage.PLATFORM_SOFTWARE,
                VideoRescueCapabilities(true, false),
            ),
        )
    }

    @Test
    fun ffmpegFailureIsTerminalAndCannotLoop() {
        assertEquals(
            VideoRescueAction.FAIL,
            decideNextVideoRescue(
                VideoRescueStage.FFMPEG,
                VideoRescueCapabilities(true, true),
            ),
        )
    }

    @Test
    fun noAvailableBackendFailsWithoutInventingSupport() {
        assertEquals(
            VideoRescueAction.FAIL,
            decideNextVideoRescue(
                VideoRescueStage.HARDWARE,
                VideoRescueCapabilities(false, false),
            ),
        )
    }
}
