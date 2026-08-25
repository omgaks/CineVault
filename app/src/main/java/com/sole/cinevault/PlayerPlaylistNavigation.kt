package com.sole.cinevault

internal data class PlayerPlaylistNavigation(
    val currentMeta: VideoWithMetadata?,
    val currentIndex: Int,
    val showPrevNextButtons: Boolean,
    val hasNextVideo: Boolean,
)

internal fun derivePlayerPlaylistNavigation(
    currentVideo: VideoFile,
    episodeList: List<VideoWithMetadata>,
    isCurrentTvShow: Boolean,
    isRestrictedFolderMedia: Boolean,
): PlayerPlaylistNavigation {
    val currentMeta = episodeList.firstOrNull {
        it.video.path == currentVideo.path
    } ?: episodeList.firstOrNull {
        it.video.name == currentVideo.name
    }

    val currentIndex = episodeList.indexOfFirst {
        it.video.path == currentVideo.path
    }

    val showPrevNextButtons =
        (isCurrentTvShow || isRestrictedFolderMedia) && episodeList.size > 1

    val hasNextVideo =
        episodeList.size > 1 && currentIndex in 0 until episodeList.lastIndex

    return PlayerPlaylistNavigation(
        currentMeta = currentMeta,
        currentIndex = currentIndex,
        showPrevNextButtons = showPrevNextButtons,
        hasNextVideo = hasNextVideo
    )
}
