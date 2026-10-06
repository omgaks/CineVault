package com.sole.cinevault

import com.sole.cinevault.library.VideoFile
import androidx.compose.runtime.Immutable

@Immutable
data class CastEntry(
    val id: Int,
    val name: String,
    val profilePath: String?
)

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
    val genres: List<String> = emptyList(),
    val director: String? = null,
    val collectionId: Int? = null,
    val collectionName: String? = null,
    val curatedCollections: List<String> = emptyList(),
    val cast: List<CastEntry> = emptyList(),
    // P5 Animation Intelligence evidence. F1 establishes the contract;
    // F2 classifies and fills the classification fields.
    val originalLanguage: String? = null,
    val metadataKeywords: List<String> = emptyList(),
    val animationSubtype: String? = null,
    val animationConfidence: Float? = null,
    val animationClassificationSource: String? = null,
    val animationEvidence: List<String> = emptyList(),
    val animationClassifierVersion: Int = 0,
)
