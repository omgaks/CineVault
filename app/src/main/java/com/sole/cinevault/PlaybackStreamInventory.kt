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
    TEXT_MEDIA3_OR_CINEVAULT,
    UNRESOLVED,
}

data class PlaybackStreamRoutingPlan(
    val videoRoute: PlaybackStreamRoute,
    val audioRoute: PlaybackStreamRoute?,
    val textRoute: PlaybackStreamRoute?,
    val isMixedPipeline: Boolean,
)

/**
 * Builds the first stream-by-stream routing plan.
 *
 * This intentionally does not pretend CineVault already owns a generic FFmpeg
 * video renderer. VIDEO software rescue here means the platform software-only
 * MediaCodec path already implemented by Playback Resilience.
 *
 * AUDIO is marked platform-or-FFmpeg-extension because CineRenderersFactory
 * already registers the Jellyfin FFmpeg audio renderer after the platform
 * renderer. We do not claim which renderer actually won until later analytics
 * expose that information.
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

    val audioRoute = inventory.selectedAudio?.let {
        PlaybackStreamRoute.AUDIO_PLATFORM_OR_FFMPEG_EXTENSION
    }

    val textRoute = inventory.selectedText?.let {
        PlaybackStreamRoute.TEXT_MEDIA3_OR_CINEVAULT
    }

    val activeRoutes = listOfNotNull(
        videoRoute.takeUnless { it == PlaybackStreamRoute.UNRESOLVED },
        audioRoute,
        textRoute,
    )

    return PlaybackStreamRoutingPlan(
        videoRoute = videoRoute,
        audioRoute = audioRoute,
        textRoute = textRoute,
        isMixedPipeline = activeRoutes.distinct().size > 1,
    )
}
