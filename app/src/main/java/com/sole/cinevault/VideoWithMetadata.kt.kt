package com.sole.cinevault

import androidx.compose.runtime.Immutable

@Immutable
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
    val type: String,
    // ── Media intelligence additions ───────────────────────────────────────
    // All default to empty/null so every existing VideoWithMetadata(...)
    // construction site (scanner, cache, etc.) keeps compiling unchanged.
    val genres: List<String> = emptyList(),
    val director: String? = null,
    val collectionId: Int? = null,
    val collectionName: String? = null,
    // Curated groupings (e.g. "Marvel Cinematic Universe") that aren't a
    // single native TMDB collection — matched by keyword, see MetadataCache.kt.
    // A movie could in principle match more than one, hence a list.
    val curatedCollections: List<String> = emptyList()
)
