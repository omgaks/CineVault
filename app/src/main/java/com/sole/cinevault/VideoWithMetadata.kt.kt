package com.sole.cinevault

data class VideoWithMetadata(
    val video: VideoFile,
    val title: String,
    val subtitle: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val episodeStill: String? = null,
    val overview: String?,
    val rating: Double?,
    val imdbRating: String? = null,
    val rottenTomatoesRating: String? = null,
    val tmdbId: Int? = null,
    val type: String
)