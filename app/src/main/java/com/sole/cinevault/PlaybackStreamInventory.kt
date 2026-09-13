package com.sole.cinevault

enum class PlaybackStreamKind {
    VIDEO,
    AUDIO,
    TEXT,
}

data class PlaybackStreamDescriptor(
    val kind: PlaybackStreamKind,
    val mimeType: String?,
    val codecString: String?,
    val language: String?,
    val selected: Boolean,
)

data class PlaybackStreamInventory(
    val videoStreams: List<PlaybackStreamDescriptor> = emptyList(),
    val audioStreams: List<PlaybackStreamDescriptor> = emptyList(),
    val textStreams: List<PlaybackStreamDescriptor> = emptyList(),
) {
    val selectedVideo: PlaybackStreamDescriptor?
        get() = videoStreams.firstOrNull { it.selected }

    val selectedAudio: PlaybackStreamDescriptor?
        get() = audioStreams.firstOrNull { it.selected }

    val selectedText: PlaybackStreamDescriptor?
        get() = textStreams.firstOrNull { it.selected }
}

enum class PlaybackStreamRoute {
    NATIVE_VIDEO,
    PLATFORM_SOFTWARE_VIDEO,
    AUDIO_PLATFORM_OR_FFMPEG_EXTENSION,
    AUDIO_FFMPEG_RESCUE_CANDIDATE,
    TEXT_MEDIA3_OR_CINEVAULT,
    UNRESOLVED,
}

data class PlaybackStreamRoutingPlan(
    val videoRoute: PlaybackStreamRoute,
    val audioRoute: PlaybackStreamRoute?,
    val textRoute: PlaybackStreamRoute?,
    val isMixedPipeline: Boolean,
    val audioAssessment: AudioStreamCapabilityAssessment? = null,
)

/**
 * Builds CineVault's stream-by-stream routing plan.
 *
 * VIDEO software rescue is still Android's software-only MediaCodec path.
 * CineVault does not yet claim a generic FFmpeg video renderer.
 *
 * AUDIO is evaluated independently. CineRenderersFactory already registers
 * MediaCodec first and FfmpegAudioRenderer second, so DTS/DTS-HD/TrueHD can be
 * identified as extension-rescue candidates without forcing the video stream
 * away from its current hardware/software route.
 */
fun buildPlaybackStreamRoutingPlan(
    inventory: PlaybackStreamInventory,
    engineMode: PlaybackEngineMode,
    videoCapabilityReport: VideoDecoderCapabilityReport?,
): PlaybackStreamRoutingPlan {
    val videoRoute = when {
        inventory.selectedVideo == null ->
            PlaybackStreamRoute.UNRESOLVED

        engineMode == PlaybackEngineMode.SOFTWARE ->
            PlaybackStreamRoute.PLATFORM_SOFTWARE_VIDEO

        videoCapabilityReport != null ->
            PlaybackStreamRoute.NATIVE_VIDEO

        else ->
            PlaybackStreamRoute.UNRESOLVED
    }

    val audioAssessment = assessAudioStreamCapability(
        inventory.selectedAudio
    )

    val audioRoute = audioAssessment?.let { assessment ->
        when (assessment.decoderPreference) {
            AudioDecoderPreference.FFMPEG_RESCUE_CANDIDATE ->
                PlaybackStreamRoute.AUDIO_FFMPEG_RESCUE_CANDIDATE

            AudioDecoderPreference.PLATFORM_PREFERRED,
            AudioDecoderPreference.UNKNOWN ->
                PlaybackStreamRoute.AUDIO_PLATFORM_OR_FFMPEG_EXTENSION
        }
    }

    val textRoute = inventory.selectedText?.let {
        PlaybackStreamRoute.TEXT_MEDIA3_OR_CINEVAULT
    }

    val activeRoutes = listOfNotNull(
        videoRoute.takeUnless {
            it == PlaybackStreamRoute.UNRESOLVED
        },
        audioRoute,
        textRoute,
    )

    return PlaybackStreamRoutingPlan(
        videoRoute = videoRoute,
        audioRoute = audioRoute,
        textRoute = textRoute,
        isMixedPipeline = activeRoutes.distinct().size > 1,
        audioAssessment = audioAssessment,
    )
}
