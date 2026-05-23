package com.sole.cinevault

data class TvGroup(
    val showName: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val episodes: List<VideoWithMetadata>
)
data class ContinueWatchingItem(
    val video: VideoWithMetadata,
    val progress: Float
)