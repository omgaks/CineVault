package com.sole.cinevault.metadata.artworkstudio

import com.sole.cinevault.metadata.ArtworkOption

enum class ArtworkStudioTool { OVERVIEW, MATCH, ARTWORK, LOCAL }

enum class ArtworkStudioSource(val label: String) {
    ALL("All"), TMDB("TMDB"), FANART("Fanart.tv"), LOCAL("Local")
}

enum class ArtworkProviderStatus { LOADING, READY, EMPTY, ERROR, NOT_CONFIGURED }

data class ArtworkProviderReport(
    val provider: String,
    val status: ArtworkProviderStatus,
    val count: Int = 0,
    val message: String = ""
)

data class ArtworkStudioGallery(
    val options: List<ArtworkOption>,
    val reports: List<ArtworkProviderReport>
)

data class StudioMatchCandidate(
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val rating: Double?,
    val type: String
)

fun artworkSourceMatches(optionSource: String, filter: ArtworkStudioSource): Boolean =
    filter == ArtworkStudioSource.ALL || optionSource.equals(filter.label, ignoreCase = true)

fun artworkLanguageScore(language: String?, preferred: String): Double = when {
    language.equals(preferred, ignoreCase = true) -> 3_000.0
    language.equals("en", ignoreCase = true) -> 2_000.0
    language.isNullOrBlank() || language == "00" -> 1_000.0
    else -> 0.0
}

fun artworkSourceName(url: String?): String = when {
    url.isNullOrBlank() -> "Not set"
    url.startsWith("file:") || url.startsWith("content:") -> "Local"
    "image.tmdb.org" in url -> "TMDB"
    "fanart.tv" in url -> "Fanart.tv"
    else -> "Automatic / cached"
}

sealed interface ArtworkStudioResult<out T> {
    data class Success<T>(val value: T) : ArtworkStudioResult<T>
    data class Failure(val message: String) : ArtworkStudioResult<Nothing>
}
