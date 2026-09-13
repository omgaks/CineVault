package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStreamInventoryTest {

    @Test
    fun hardwareVideoWithAudioAndTextBuildsMixedNativePlan() {
        val inventory = inventory(
            video = true,
            audio = true,
            text = true,
        )

        val plan = buildPlaybackStreamRoutingPlan(
            inventory = inventory,
            engineMode = PlaybackEngineMode.HARDWARE,
            videoCapabilityReport = capabilityReport(),
        )

        assertEquals(
            PlaybackStreamRoute.NATIVE_VIDEO,
            plan.videoRoute,
        )
        assertEquals(
            PlaybackStreamRoute.AUDIO_PLATFORM_OR_FFMPEG_EXTENSION,
            plan.audioRoute,
        )
        assertEquals(
            PlaybackStreamRoute.TEXT_MEDIA3_OR_CINEVAULT,
            plan.textRoute,
        )
        assertTrue(plan.isMixedPipeline)
    }

    @Test
    fun softwareVideoKeepsAudioOnIndependentFallbackLane() {
        val plan = buildPlaybackStreamRoutingPlan(
            inventory = inventory(video = true, audio = true),
            engineMode = PlaybackEngineMode.SOFTWARE,
            videoCapabilityReport = capabilityReport(),
        )

        assertEquals(
            PlaybackStreamRoute.PLATFORM_SOFTWARE_VIDEO,
            plan.videoRoute,
        )
        assertEquals(
            PlaybackStreamRoute.AUDIO_PLATFORM_OR_FFMPEG_EXTENSION,
            plan.audioRoute,
        )
        assertTrue(plan.isMixedPipeline)
    }

    @Test
    fun missingVideoCapabilityDoesNotInventNativeSupport() {
        val plan = buildPlaybackStreamRoutingPlan(
            inventory = inventory(video = true, audio = true),
            engineMode = PlaybackEngineMode.HARDWARE,
            videoCapabilityReport = null,
        )

        assertEquals(
            PlaybackStreamRoute.UNRESOLVED,
            plan.videoRoute,
        )
        assertEquals(
            PlaybackStreamRoute.AUDIO_PLATFORM_OR_FFMPEG_EXTENSION,
            plan.audioRoute,
        )
    }

    @Test
    fun audioOnlyInventoryDoesNotPretendVideoExists() {
        val plan = buildPlaybackStreamRoutingPlan(
            inventory = inventory(video = false, audio = true),
            engineMode = PlaybackEngineMode.HARDWARE,
            videoCapabilityReport = null,
        )

        assertEquals(
            PlaybackStreamRoute.UNRESOLVED,
            plan.videoRoute,
        )
        assertEquals(
            PlaybackStreamRoute.AUDIO_PLATFORM_OR_FFMPEG_EXTENSION,
            plan.audioRoute,
        )
        assertFalse(plan.isMixedPipeline)
    }

    private fun inventory(
        video: Boolean = false,
        audio: Boolean = false,
        text: Boolean = false,
    ): PlaybackStreamInventory = PlaybackStreamInventory(
        videoStreams = if (video) {
            listOf(
                PlaybackStreamDescriptor(
                    kind = PlaybackStreamKind.VIDEO,
                    mimeType = "video/hevc",
                    codecString = "hvc1.2.4.L153.B0",
                    language = null,
                    selected = true,
                )
            )
        } else {
            emptyList()
        },
        audioStreams = if (audio) {
            listOf(
                PlaybackStreamDescriptor(
                    kind = PlaybackStreamKind.AUDIO,
                    mimeType = "audio/eac3",
                    codecString = "ec-3",
                    language = "eng",
                    selected = true,
                )
            )
        } else {
            emptyList()
        },
        textStreams = if (text) {
            listOf(
                PlaybackStreamDescriptor(
                    kind = PlaybackStreamKind.TEXT,
                    mimeType = "application/x-subrip",
                    codecString = null,
                    language = "eng",
                    selected = true,
                )
            )
        } else {
            emptyList()
        },
    )

    private fun capabilityReport(): VideoDecoderCapabilityReport =
        VideoDecoderCapabilityReport(
            mimeType = "video/hevc",
            status = VideoDecoderCapabilityStatus.SUPPORTED,
            decoders = emptyList(),
            queryError = null,
            streamProfile = null,
        )
}
