package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRescueCoordinatorTest {
    private fun snapshot(
        videoRequested: Boolean = false,
        videoAvailable: Boolean = true,
        engineMode: PlaybackEngineMode = PlaybackEngineMode.HARDWARE,
        audioRequested: Boolean = false,
        audioPreference: CineAudioRendererPreference =
            CineAudioRendererPreference.PLATFORM_FIRST,
    ) = PlaybackRescueRuntimeSnapshot(
        softwareVideoRequested = videoRequested,
        softwareVideoAvailable = videoAvailable,
        engineMode = engineMode,
        ffmpegAudioRequested = audioRequested,
        audioRendererPreference = audioPreference,
    )

    @Test fun videoOnlyRuntimeSelectsSoftwareVideo() {
        assertEquals(
            PlaybackRescueLane.SOFTWARE_VIDEO,
            PlaybackRescueCoordinator.decide(
                snapshot(videoRequested = true)
            )
        )
    }

    @Test fun audioOnlyRuntimeSelectsFfmpegAudio() {
        assertEquals(
            PlaybackRescueLane.FFMPEG_AUDIO,
            PlaybackRescueCoordinator.decide(
                snapshot(audioRequested = true)
            )
        )
    }

    @Test fun simultaneousRuntimeRequestsSelectMixedRescue() {
        assertEquals(
            PlaybackRescueLane.MIXED,
            PlaybackRescueCoordinator.decide(
                snapshot(videoRequested = true, audioRequested = true)
            )
        )
    }

    @Test fun activeSoftwareVideoCannotReenterVideoRescue() {
        assertEquals(
            PlaybackRescueLane.NONE,
            PlaybackRescueCoordinator.decide(
                snapshot(
                    videoRequested = true,
                    engineMode = PlaybackEngineMode.SOFTWARE,
                )
            )
        )
    }

    @Test fun activeFfmpegAudioCannotReenterAudioRescue() {
        assertEquals(
            PlaybackRescueLane.NONE,
            PlaybackRescueCoordinator.decide(
                snapshot(
                    audioRequested = true,
                    audioPreference = CineAudioRendererPreference.FFMPEG_FIRST,
                )
            )
        )
    }

    @Test fun activeVideoLaneCanStillAdmitIndependentAudioRescue() {
        assertEquals(
            PlaybackRescueLane.FFMPEG_AUDIO,
            PlaybackRescueCoordinator.decide(
                snapshot(
                    videoRequested = true,
                    engineMode = PlaybackEngineMode.SOFTWARE,
                    audioRequested = true,
                )
            )
        )
    }
}
